package org.getfit.app.progress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The progress photographs themselves, on disk.
 *
 * They live in the app's own storage, which means they are private to this
 * install, go nowhere, and are removed with the app. There is deliberately no
 * upload and no share: the app has no server, and these are the photographs
 * somebody takes in their bathroom.
 *
 * Every photograph is re-encoded on the way in rather than copied. Two reasons,
 * both of which show up later rather than now:
 *
 * - A modern phone camera writes several megabytes a frame. Five of those a
 *   week is a gigabyte a year of somebody's storage, spent on detail nobody can
 *   see in a comparison two inches wide.
 * - The orientation a camera records in EXIF is not applied by the decoder, so
 *   a photograph taken in portrait comes back on its side. Rotating once here
 *   means every read afterwards is already the right way up, rather than every
 *   screen having to remember.
 */
class PhotoStore(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private val captureDirectory: File
        get() = File(context.filesDir, CAPTURE_DIRECTORY).apply { mkdirs() }

    fun file(name: String): File = File(directory, name)

    /**
     * Somewhere for the camera to put a frame, as a URI it is allowed to write.
     *
     * Its own directory rather than the one finished photographs live in: that
     * one is swept at launch for files no entry refers to, and a photograph
     * still being taken refers to nothing yet.
     *
     * The authority carries the applicationId rather than a literal, because a
     * dev build and a production build sit on the same phone on purpose — two
     * providers claiming one authority is an install that fails outright.
     */
    fun newCaptureUri(): Uri? = runCatching {
        val target = File(captureDirectory, "capture-${UUID.randomUUID()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.getOrNull()

    /**
     * Takes a just-captured frame into the collection proper.
     *
     * The camera's file is read through the same path a picked one is — shrunk,
     * turned upright, re-encoded — and then thrown away, so nothing downstream
     * has to care which of the two routes a photograph arrived by.
     */
    suspend fun keepCapture(uri: Uri): String? {
        val stored = save(uri)
        discardCaptures()
        return stored
    }

    /** Clears the camera's staging directory, whether or not a frame arrived. */
    suspend fun discardCaptures() = withContext(Dispatchers.IO) {
        runCatching { captureDirectory.listFiles()?.forEach { it.delete() } }
        Unit
    }

    /**
     * Copies a picked image in, returning the file name it was stored under, or
     * null if it could not be read.
     *
     * Null rather than an exception because the caller is a screen: an image
     * that will not decode is something to tell somebody about, not something
     * to bring the app down over.
     */
    suspend fun save(source: Uri): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        }
        val decoded = runCatching {
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull() ?: return@withContext null

        val rotation = runCatching {
            context.contentResolver.openInputStream(source)?.use { stream ->
                rotationFor(
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                )
            }
        }.getOrNull() ?: 0f

        val upright = if (rotation == 0f) {
            decoded
        } else {
            runCatching {
                Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation) }, true,
                )
            }.getOrDefault(decoded)
        }

        val name = "${UUID.randomUUID()}.jpg"
        val written = runCatching {
            File(directory, name).outputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
        }.getOrDefault(false)

        if (upright !== decoded) decoded.recycle()
        upright.recycle()

        if (written) name else null
    }

    /** Reads one back, shrunk to roughly [maxEdge] pixels on its longer side. */
    suspend fun decode(name: String, maxEdge: Int): Bitmap? = withContext(Dispatchers.IO) {
        val target = File(directory, name)
        if (!target.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(target.path, bounds) }
        if (bounds.outWidth <= 0) return@withContext null
        runCatching {
            BitmapFactory.decodeFile(
                target.path,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
                },
            )
        }.getOrNull()
    }

    suspend fun delete(names: List<String>) = withContext(Dispatchers.IO) {
        names.forEach { runCatching { File(directory, it).delete() } }
        Unit
    }

    /**
     * Removes any file no entry refers to.
     *
     * Photographs are written when they are picked rather than when the entry
     * is saved, so that what is on screen is the file that will be kept.
     * Abandoning a half-written entry therefore leaves files behind, and this is
     * what collects them — at launch, before any draft exists to be caught by
     * it.
     */
    suspend fun pruneOrphans(referenced: Set<String>) = withContext(Dispatchers.IO) {
        runCatching {
            directory.listFiles()?.forEach { file ->
                if (file.name !in referenced) file.delete()
            }
        }
        Unit
    }

    private fun rotationFor(orientation: Int): Float = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    private companion object {
        const val DIRECTORY = "progress-photos"

        /** Staging for the camera. Declared in res/xml/file_paths.xml. */
        const val CAPTURE_DIRECTORY = "progress-capture"

        /**
         * The longer edge a stored photograph is shrunk towards. Comfortably
         * more than any phone screen shows it at, and a fraction of what the
         * camera wrote.
         */
        const val MAX_EDGE = 1440
        const val QUALITY = 85
    }
}
