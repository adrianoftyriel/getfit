package org.getfit.app.ui

import androidx.activity.compose.BackHandler
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
import org.getfit.app.demo.DemoRepository
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
    val demos: DemoRepository,
    val inbox: MealInbox,
    val updater: Updater,
)

/**
 * The four places the app has.
 *
 * A bottom bar rather than a navigation graph: these are peers, all reachable
 * at all times, and none of them takes an argument.
 */
enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Home),
    PLANS("Plans", Icons.Filled.List),
    FOOD("Food", Icons.Filled.DateRange),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/**
 * A screen that covers the tabs rather than sitting beside them.
 *
 * These take arguments and are entered from a tab, so they are a stack over the
 * top rather than a fifth destination. A hand-rolled list of three cases is
 * both smaller and harder to get wrong than argument encoding through a
 * navigation library.
 */
sealed interface Route {
    /** Null id means a new plan. */
    data class EditPlan(val planId: String?) : Route
    data class Demo(val exerciseId: String) : Route
    data object Session : Route
}

@Composable
fun AppRoot(env: AppEnv, pendingMeal: MealEntry? = null, onMealHandled: () -> Unit = {}) {
    val settings by env.settingsRepository.settings.collectAsState(initial = Settings())
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var stack by remember { mutableStateOf(listOf<Route>()) }

    val push: (Route) -> Unit = { route -> stack = stack + route }
    val pop: () -> Unit = { if (stack.isNotEmpty()) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    // A meal arriving by link is the one thing allowed to move the app for the
    // user: they tapped a link to record a meal, and landing anywhere other
    // than the meal they just recorded would be answering a different question.
    //
    // Keyed on the id rather than on the meal, so this fires once per meal that
    // arrives and not again on every recomposition in between.
    LaunchedEffect(pendingMeal?.id) {
        if (pendingMeal != null) {
            stack = emptyList()
            tab = Tab.FOOD
        }
    }

    when (val route = stack.lastOrNull()) {
        is Route.EditPlan -> PlanEditorScreen(env = env, planId = route.planId, onDone = pop)

        is Route.Demo -> ExerciseDemoScreen(env = env, exerciseId = route.exerciseId, onBack = pop)

        Route.Session -> SessionScreen(
            env = env,
            onBack = pop,
            onShowDemo = { exerciseId -> push(Route.Demo(exerciseId)) },
        )

        null -> Tabs(
            env = env,
            settings = settings,
            tab = tab,
            onTab = { tab = it },
            pendingMeal = pendingMeal,
            onMealHandled = onMealHandled,
            push = push,
        )
    }
}

@Composable
private fun Tabs(
    env: AppEnv,
    settings: Settings,
    tab: Tab,
    onTab: (Tab) -> Unit,
    pendingMeal: MealEntry?,
    onMealHandled: () -> Unit,
    push: (Route) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { onTab(entry) },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    env = env,
                    settings = settings,
                    onOpenSession = { push(Route.Session) },
                    onBrowsePlans = { onTab(Tab.PLANS) },
                )

                Tab.PLANS -> PlansScreen(
                    env = env,
                    onEditPlan = { planId -> push(Route.EditPlan(planId)) },
                    onOpenSession = { push(Route.Session) },
                    onShowDemo = { exerciseId -> push(Route.Demo(exerciseId)) },
                )

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
