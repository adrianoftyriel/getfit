package org.getfit.app.workout

/**
 * The movements the app ships knowing about.
 *
 * A seed rather than a library: enough to build a real plan on day one without
 * typing every lift in by hand, and small enough to read. User-defined
 * exercises will live alongside these rather than replacing them, which is why
 * lookup goes through [byId] instead of anything indexing this list directly.
 */
object ExerciseCatalog {

    val all: List<Exercise> = listOf(
        // Chest
        Exercise("bench-press", "Barbell bench press", MuscleGroup.CHEST, Equipment.BARBELL),
        Exercise("incline-db-press", "Incline dumbbell press", MuscleGroup.CHEST, Equipment.DUMBBELL),
        Exercise("push-up", "Push-up", MuscleGroup.CHEST, Equipment.BODYWEIGHT, bodyweight = true),
        Exercise("cable-fly", "Cable fly", MuscleGroup.CHEST, Equipment.CABLE),

        // Back
        Exercise("deadlift", "Deadlift", MuscleGroup.BACK, Equipment.BARBELL),
        Exercise("barbell-row", "Barbell row", MuscleGroup.BACK, Equipment.BARBELL),
        Exercise("pull-up", "Pull-up", MuscleGroup.BACK, Equipment.BODYWEIGHT, bodyweight = true),
        Exercise("lat-pulldown", "Lat pulldown", MuscleGroup.BACK, Equipment.CABLE),
        Exercise("seated-row", "Seated cable row", MuscleGroup.BACK, Equipment.CABLE),

        // Legs
        Exercise("back-squat", "Back squat", MuscleGroup.LEGS, Equipment.BARBELL),
        Exercise("front-squat", "Front squat", MuscleGroup.LEGS, Equipment.BARBELL),
        Exercise("romanian-deadlift", "Romanian deadlift", MuscleGroup.LEGS, Equipment.BARBELL),
        Exercise("leg-press", "Leg press", MuscleGroup.LEGS, Equipment.MACHINE),
        Exercise("walking-lunge", "Walking lunge", MuscleGroup.LEGS, Equipment.DUMBBELL),
        Exercise("calf-raise", "Standing calf raise", MuscleGroup.LEGS, Equipment.MACHINE),

        // Shoulders
        Exercise("overhead-press", "Overhead press", MuscleGroup.SHOULDERS, Equipment.BARBELL),
        Exercise("lateral-raise", "Lateral raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL),
        Exercise("face-pull", "Face pull", MuscleGroup.SHOULDERS, Equipment.CABLE),

        // Arms
        Exercise("barbell-curl", "Barbell curl", MuscleGroup.ARMS, Equipment.BARBELL),
        Exercise("hammer-curl", "Hammer curl", MuscleGroup.ARMS, Equipment.DUMBBELL),
        Exercise("triceps-pushdown", "Triceps pushdown", MuscleGroup.ARMS, Equipment.CABLE),
        Exercise("dip", "Dip", MuscleGroup.ARMS, Equipment.BODYWEIGHT, bodyweight = true),

        // Core
        Exercise("plank", "Plank", MuscleGroup.CORE, Equipment.BODYWEIGHT, bodyweight = true),
        Exercise("hanging-leg-raise", "Hanging leg raise", MuscleGroup.CORE, Equipment.BODYWEIGHT, bodyweight = true),
        Exercise("cable-crunch", "Cable crunch", MuscleGroup.CORE, Equipment.CABLE),

        // Cardio
        Exercise("row-erg", "Rowing machine", MuscleGroup.CARDIO, Equipment.MACHINE, bodyweight = true),
        Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, Equipment.MACHINE, bodyweight = true),
        Exercise("cycling", "Cycling", MuscleGroup.CARDIO, Equipment.MACHINE, bodyweight = true),
    )

    // Named apart from the function below rather than shadowing it. Kotlin would
    // resolve `byId[id]` to the property either way, but a reader should not
    // have to know that to be sure this is not infinite recursion.
    private val index: Map<String, Exercise> = all.associateBy { it.id }

    fun byId(id: String): Exercise? = index[id]

    fun inGroup(group: MuscleGroup): List<Exercise> = all.filter { it.group == group }

    /** The groups that actually have something in them, in declaration order. */
    val populatedGroups: List<MuscleGroup>
        get() = MuscleGroup.entries.filter { group -> all.any { it.group == group } }
}

/**
 * Two plans to start from, so a fresh install is not an empty screen.
 *
 * Ordinary full-body and upper/lower work — not a recommendation, just
 * something to edit into whatever is actually being run.
 */
object StarterPlans {

    val fullBody = WorkoutPlan(
        id = "starter-full-body",
        name = "Full body A",
        exercises = listOf(
            PlanExercise("back-squat", List(3) { PlannedSet(reps = 5, weightKg = 60.0) }),
            PlanExercise("bench-press", List(3) { PlannedSet(reps = 5, weightKg = 50.0) }),
            PlanExercise("barbell-row", List(3) { PlannedSet(reps = 8, weightKg = 40.0) }),
            PlanExercise("plank", List(3) { PlannedSet(reps = 1) }, notes = "Hold 45s"),
        ),
        notes = "Starter template — edit the loads to yours.",
    )

    val upper = WorkoutPlan(
        id = "starter-upper",
        name = "Upper",
        exercises = listOf(
            PlanExercise("overhead-press", List(4) { PlannedSet(reps = 6, weightKg = 30.0) }),
            PlanExercise("pull-up", List(3) { PlannedSet(reps = 6) }),
            PlanExercise("incline-db-press", List(3) { PlannedSet(reps = 10, weightKg = 20.0) }),
            PlanExercise("lateral-raise", List(3) { PlannedSet(reps = 15, weightKg = 8.0) }),
            PlanExercise("triceps-pushdown", List(3) { PlannedSet(reps = 12, weightKg = 25.0) }),
        ),
        notes = "Starter template — edit the loads to yours.",
    )

    val all: List<WorkoutPlan> = listOf(fullBody, upper)
}
