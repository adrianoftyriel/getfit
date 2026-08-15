package org.getfit.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.getfit.app.workout.ReminderSchedule
import java.util.Calendar

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// [UnitSystem] and the conversions live in Units.kt, which has no Android in it
// so the unit tests can reach them.

data class Settings(
    val checkForUpdatesOnLaunch: Boolean = true,
    /**
     * Whether to look for meals the skill has published when the app opens.
     *
     * On by default, because a meal analysed at a desk turning up on the phone
     * without being asked for is the whole point of the inbox. One request when
     * there is nothing new — see [org.getfit.app.nutrition.MealInbox].
     */
    val syncMealInbox: Boolean = true,
    val units: UnitSystem = UnitSystem.METRIC,
    /** Daily calorie target, or null for no target rather than a target of zero. */
    val calorieTarget: Int? = null,
    /** When to be reminded to train. Off until asked for. */
    val reminder: ReminderSchedule = ReminderSchedule(),
    /**
     * Exactly what the user has typed, which may be blank while they are
     * mid-edit. Nothing substitutes a default into this value: doing so would
     * mean clearing the field wrote the default straight back and the text
     * snapped back before a real name could be typed.
     */
    val displayNameRaw: String = "",
) {
    val displayName: String get() = displayNameRaw.trim().ifBlank { DEFAULT_NAME }

    companion object {
        const val DEFAULT_NAME = "Athlete"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val UPDATE_ON_LAUNCH = booleanPreferencesKey("check_updates_on_launch")
        val SYNC_INBOX = booleanPreferencesKey("sync_meal_inbox")
        val UNITS = stringPreferencesKey("units")
        val CALORIE_TARGET = intPreferencesKey("calorie_target")
        val NAME = stringPreferencesKey("display_name")
        val REMINDER_ON = booleanPreferencesKey("reminder_enabled")
        // Days are Calendar constants held as strings, because DataStore has a
        // string set and no int set. Anything unparseable is dropped rather
        // than defaulted to a day nobody asked to be woken on.
        val REMINDER_DAYS = stringSetPreferencesKey("reminder_days")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        // There is deliberately no update-channel key. The channel is a
        // property of the installed APK, not a preference — an install that
        // could be pointed at the other channel's builds is exactly the mix-up
        // that separate applicationIds exist to prevent.
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            checkForUpdatesOnLaunch = prefs[Keys.UPDATE_ON_LAUNCH] ?: true,
            syncMealInbox = prefs[Keys.SYNC_INBOX] ?: true,
            units = prefs[Keys.UNITS]
                ?.let { stored -> UnitSystem.entries.firstOrNull { it.name == stored } }
                ?: UnitSystem.METRIC,
            // Absent and zero are different answers: zero would be a target
            // nobody set and nobody can meet.
            calorieTarget = prefs[Keys.CALORIE_TARGET]?.takeIf { it > 0 },
            displayNameRaw = prefs[Keys.NAME] ?: "",
            reminder = ReminderSchedule(
                enabled = prefs[Keys.REMINDER_ON] ?: false,
                days = prefs[Keys.REMINDER_DAYS].orEmpty()
                    .mapNotNull { it.toIntOrNull() }
                    .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
                    .toSet(),
                // Coerced rather than trusted: an hour of 25 would make
                // nextOccurrence refuse the schedule outright, and a reminder
                // that silently never fires is worse than one at the wrong time.
                hour = (prefs[Keys.REMINDER_HOUR] ?: 18).coerceIn(0, 23),
                minute = (prefs[Keys.REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
            ),
        )
    }

    suspend fun setReminder(schedule: ReminderSchedule) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDER_ON] = schedule.enabled
            prefs[Keys.REMINDER_DAYS] = schedule.days.map { it.toString() }.toSet()
            prefs[Keys.REMINDER_HOUR] = schedule.hour.coerceIn(0, 23)
            prefs[Keys.REMINDER_MINUTE] = schedule.minute.coerceIn(0, 59)
        }
    }

    suspend fun setCheckForUpdatesOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UPDATE_ON_LAUNCH] = enabled }
    }

    suspend fun setSyncMealInbox(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SYNC_INBOX] = enabled }
    }

    suspend fun setUnits(units: UnitSystem) {
        context.dataStore.edit { it[Keys.UNITS] = units.name }
    }

    suspend fun setCalorieTarget(target: Int?) {
        context.dataStore.edit { prefs ->
            if (target == null || target <= 0) prefs.remove(Keys.CALORIE_TARGET)
            else prefs[Keys.CALORIE_TARGET] = target
        }
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { it[Keys.NAME] = name }
    }
}
