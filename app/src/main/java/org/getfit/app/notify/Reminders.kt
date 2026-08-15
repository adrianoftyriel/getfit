package org.getfit.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.getfit.app.MainActivity
import org.getfit.app.R
import org.getfit.app.settings.SettingsRepository
import org.getfit.app.workout.ReminderSchedule
import org.getfit.app.workout.nextOccurrence

/**
 * Reminders to train.
 *
 * One alarm is ever outstanding, and firing it schedules the next one. A
 * repeating alarm would have been less code and would drift: the schedule is
 * "18:00 on Wednesday", and the gap between two Wednesdays is not a constant
 * number of hours when the clocks change. [nextOccurrence] works the next one
 * out from a calendar, and this re-arms from it every time.
 *
 * The alarm is inexact — [AlarmManager.setAndAllowWhileIdle] — on purpose. An
 * exact alarm needs `SCHEDULE_EXACT_ALARM`, which Android treats as a
 * high-privilege permission and may refuse, and being reminded to go to the gym
 * at 18:04 instead of 18:00 is not worth asking somebody for that.
 */
object Reminders {

    const val CHANNEL_ID = "workout-reminders"
    private const val REQUEST_CODE = 4001
    private const val NOTIFICATION_ID = 4002

    /** Must exist before the first notification is posted; safe to call repeatedly. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminders to train on the days you have chosen."
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Arms the next reminder, or cancels any outstanding one when the schedule
     * is off or has no days.
     *
     * Idempotent, because it is called from everywhere the answer could have
     * changed: the settings screen, the receiver that has just fired, and boot.
     */
    fun sync(context: Context, schedule: ReminderSchedule) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)

        val next = nextOccurrence(schedule, System.currentTimeMillis())
        if (next == null) {
            alarms.cancel(pending)
            return
        }

        runCatching {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
        // Immutable is required from API 31 and harmless before it.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /**
     * Posts the reminder.
     *
     * Silently does nothing when notifications are not permitted. From API 33
     * the user may simply have said no, and that is an answer to respect rather
     * than an error to report — there is nowhere to report it to from a
     * broadcast receiver anyway.
     */
    fun notifyNow(context: Context) {
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to train")
            .setContentText("Open GetFit and start today's session.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}

/**
 * Fires the reminder and arms the next one.
 *
 * Re-arming here rather than only from the settings screen is what keeps the
 * schedule alive: nothing else runs on a phone whose owner has not opened the
 * app for a fortnight, which is exactly when a reminder is most wanted.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return

        Reminders.notifyNow(context)

        // Reading the schedule is a suspending disk read, so the broadcast has
        // to be held open across it. Without goAsync the process can be killed
        // the moment onReceive returns and the next alarm is never set.
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = SettingsRepository(app).settings.first()
                Reminders.sync(app, settings.reminder)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "org.getfit.app.REMIND"
    }
}

/**
 * Restores the alarm after a reboot.
 *
 * Android drops every alarm when the device restarts, so without this a
 * reminder set once would work until the first reboot and then quietly stop —
 * the worst kind of failure, because nothing appears to be wrong.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = SettingsRepository(app).settings.first()
                Reminders.sync(app, settings.reminder)
            } finally {
                pending.finish()
            }
        }
    }
}
