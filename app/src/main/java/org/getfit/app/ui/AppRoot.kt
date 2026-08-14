package org.getfit.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.getfit.app.nutrition.MealEntry
import org.getfit.app.nutrition.MealInbox
import org.getfit.app.nutrition.NutritionRepository
import org.getfit.app.settings.Settings
import org.getfit.app.settings.SettingsRepository
import org.getfit.app.update.Updater
import org.getfit.app.workout.WorkoutRepository

/** Everything the screens need, assembled once by the activity. */
class AppEnv(
    val settingsRepository: SettingsRepository,
    val workouts: WorkoutRepository,
    val nutrition: NutritionRepository,
    val inbox: MealInbox,
    val updater: Updater,
)

/**
 * The four places the app has.
 *
 * A bottom bar rather than a navigation graph: these are peers, all reachable
 * at all times, and none of them takes an argument. There is nothing here for a
 * navigation library to do that an enum does not already do.
 */
enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Home),
    PLANS("Plans", Icons.Filled.List),
    FOOD("Food", Icons.Filled.DateRange),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun AppRoot(env: AppEnv, pendingMeal: MealEntry? = null, onMealHandled: () -> Unit = {}) {
    val settings by env.settingsRepository.settings.collectAsState(initial = Settings())
    var tab by remember { mutableStateOf(Tab.TODAY) }

    // A meal arriving by link is the one thing allowed to move the app for the
    // user: they tapped a link to record a meal, and landing anywhere other
    // than the meal they just recorded would be answering a different question.
    //
    // Keyed on the id rather than on the meal, so this fires once per meal that
    // arrives and not again on every recomposition in between.
    LaunchedEffect(pendingMeal?.id) {
        if (pendingMeal != null) tab = Tab.FOOD
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(env, settings)
                Tab.PLANS -> PlansScreen(env)
                Tab.FOOD -> NutritionScreen(
                    env = env,
                    settings = settings,
                    pendingMeal = pendingMeal,
                    onMealHandled = onMealHandled,
                )

                Tab.SETTINGS -> SettingsScreen(env, settings)
            }
        }

        // Looks on launch and speaks only if there is something to install.
        LaunchUpdateCheck(env)
    }
}
