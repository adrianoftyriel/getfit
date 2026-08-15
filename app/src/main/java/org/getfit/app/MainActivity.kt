package org.getfit.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.getfit.app.demo.DemoRepository
import org.getfit.app.notify.Reminders
import org.getfit.app.nutrition.MealEntry
import org.getfit.app.nutrition.MealInbox
import org.getfit.app.nutrition.MealLink
import org.getfit.app.nutrition.MealLinkResult
import org.getfit.app.nutrition.NutritionRepository
import org.getfit.app.progress.ProgressRepository
import org.getfit.app.settings.SettingsRepository
import org.getfit.app.ui.AppEnv
import org.getfit.app.ui.AppRoot
import org.getfit.app.ui.theme.GetFitTheme
import org.getfit.app.update.Updater
import org.getfit.app.workout.WorkoutRepository

class MainActivity : ComponentActivity() {

    private lateinit var env: AppEnv

    /**
     * A meal handed over by a `getfit://` link, waiting to be confirmed.
     *
     * Held on the activity rather than inside a screen because the link is
     * delivered to the activity, and may arrive either at start or while the
     * app is already open.
     */
    private var pendingMeal by mutableStateOf<MealEntry?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        env = AppEnv(
            settingsRepository = SettingsRepository(applicationContext),
            workouts = WorkoutRepository(applicationContext),
            nutrition = NutritionRepository(applicationContext),
            progress = ProgressRepository(applicationContext),
            demos = DemoRepository(applicationContext),
            inbox = MealInbox(),
            updater = Updater(this),
        )

        // Cheap, and required before any reminder can be posted.
        Reminders.ensureChannel(applicationContext)

        // Both documents are read off disk once, here, rather than by whichever
        // screen happens to be drawn first. Until this finishes the repositories
        // report empty, which is why every screen's flow has a default rather
        // than blocking on a load.
        lifecycleScope.launch {
            env.workouts.load()
            env.nutrition.load()
            env.progress.load()

            // Progress photographs are written when they are picked rather than
            // when the entry is saved, so an abandoned entry leaves files
            // behind. Collected here, at launch, which is the one moment no
            // half-written entry can exist to be caught by it.
            env.progress.photos.pruneOrphans(env.progress.referencedPhotos())

            // Re-arm on every launch as well as on boot. An alarm can be lost
            // to a force-stop or to the battery optimiser, neither of which
            // sends a broadcast, and the failure is invisible: reminders simply
            // stop. Opening the app is the one moment we can put that right.
            Reminders.sync(applicationContext, env.settingsRepository.settings.first().reminder)
        }

        handleIntent(intent)

        setContent {
            GetFitTheme {
                AppRoot(
                    env = env,
                    pendingMeal = pendingMeal,
                    onMealHandled = { pendingMeal = null },
                )
            }
        }
    }

    /** A link that arrives while the app is already running comes through here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Pulls a meal out of a `getfit://meal?v=1&d=…` link.
     *
     * The URI is taken apart with `android.net.Uri`, which is better at that
     * than anything hand-written would be; everything after the `d` parameter —
     * decoding it, and deciding whether what came out is a meal at all — is
     * [MealLink]'s, where it can be tested. A link that does not decode says so
     * rather than being dropped: somebody tapped it on purpose, and silence
     * would leave them waiting for a meal that is never going to appear.
     */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!uri.scheme.equals(MealLink.SCHEME, ignoreCase = true)) return

        val data = runCatching { uri.getQueryParameter(MealLink.PARAM_DATA) }.getOrNull()
        when (val result = MealLink.decodePayload(data)) {
            is MealLinkResult.Ok -> pendingMeal = result.entry
            is MealLinkResult.Malformed -> toast(result.reason)
            is MealLinkResult.TooNew -> toast(result.reason)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
