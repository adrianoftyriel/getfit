# Working on this repo with Claude

GetFit is an Android app for planning and tracking workouts, and for tracking
what gets eaten. It is built and shipped the same way as
[ProLibertateGames](https://github.com/adrianoftyriel/ProLibertateGames), and
this file is the part of that arrangement you have to know before changing
anything.

## The two branches both ship

Neither long-lived branch is an integration branch. A merge into either is a
release:

- **`dev`** → `ci.yml` builds, tests, lints, and publishes a
  `v<series>.N-dev` **prerelease**. The in-app updater offers it to anyone
  running a dev install.
- **`main`** → `release.yml` builds and publishes a **production** release,
  which the updater installs for everyone on a production build.

Confirm before shipping to `main`. Note that `release.yml` runs `assembleDebug`
only — no tests, no lint — so a `main` release is only as verified as the `dev`
build it replays.

A dev APK installs as `org.getfit.app.dev`, is called **GetFit DEV**, and is
teal rather than orange, so it sits beside a production copy on one phone rather
than replacing it. Those four things — applicationId, label, accent colour, and
the `getfit://meal-dev` deep-link host — all hang off `isDevBuild` in
`app/build.gradle.kts` and have to move together.

## Nothing a session does triggers CI. Dispatch it explicitly.

GitHub creates **no workflow run** for anything this sandbox does — pushing a
branch, opening a pull request, or merging one. It all goes through the same
integration, and GitHub suppresses event-triggered runs from it. A pull request
will say it was opened by the repo owner; that is only how the UI attributes it,
and it does not change the suppression.

This was measured on ProLibertateGames, not on this repo, and the sandbox
credential is the same one — so expect the same behaviour here and check the
first time it matters. `workflow_dispatch` is the way round it, because it
creates a run directly instead of relying on an event.

## The working loop

1. Commit and push the feature branch.
2. Dispatch `ci.yml` **on that branch**. Wait for green.
3. Open the PR through the API and merge it there, so the change is reviewable
   and the merge commit is honest. Neither step builds anything.
4. Dispatch the target branch's workflow to actually build and publish:
   `ci.yml` on `dev`, `release.yml` on `main`.

Step 2 works because `ci.yml` builds, tests and lints on any ref, while its
staging and publishing steps are gated on `github.ref == 'refs/heads/dev'`.
Dispatching it on a feature branch is therefore the full check and publishes
nothing.

Skipping step 4 leaves a branch whose head was never built, and no release.

## Gradle does not run in the sandbox

`./gradlew` cannot resolve the Android plugin here: the proxy answers
`dl.google.com` with a 403. Verified in this container — `repo1.maven.org` is
reachable and `dl.google.com` is not, which is enough to stop the build at the
plugin. No local build, no local test run, no local lint. **A dispatched CI run
is the only way to find out whether a change compiles.**

Write accordingly: prefer code whose correctness can be argued from reading it,
keep pure logic out of the Android classes so the unit tests can reach it, and
expect the first CI run after a large change to find something.

## Where the logic lives, and why

The parts with rules in them are deliberately separable from Android, because
the unit tests are the only check that runs before CI:

- `update/UpdatePolicy.kt` — the wall between the two update channels. Pure
  functions, and the most important file in the repo to not break: it is what
  stops a production phone being handed a dev build.
- `nutrition/MealLink.kt` — decoding a meal out of a `getfit://` link, plus a
  base64url codec written out by hand. `android.util.Base64` is stubbed to
  return null under unit tests, which would make the tests pass without
  encoding anything; `java.util.Base64` needs API 26 and minSdk is 24.
- `nutrition/MealInbox.kt` — `selectNew` is pure and tested; the fetch around
  it is not.
- `workout/Workout.kt` — volume, completed sets, estimated maxes.

`store/JsonStore.kt` holds each document in one JSON file and writes it back
through a temp file and a rename. It is not a database because there is nothing
here worth querying, and it is behind flows and suspend functions so it can
become one later without a screen noticing.

## Meals reach the phone by two routes

The `meal-photo` skill in `.claude/skills/` estimates a meal from a photograph
and publishes it. Both routes are read-only from the app's side — there is no
token in the APK, and the same public path the updater uses is the only one it
has:

- **The inbox.** The skill commits `nutrition/inbox/<id>.json` to the
  **`nutrition-inbox`** branch. The app lists that directory on launch and
  collects what it has not already imported. A branch of its own, because
  publishing lunch is not a commit to the code and must never appear in a diff
  or trigger a build. `nutrition/inbox/` is gitignored everywhere else so it
  cannot be committed to `dev` by accident.
- **The link.** `getfit://meal?v=1&d=<base64url>` carries the whole meal, for
  when there is no network or the phone is already in hand.

Because the app cannot write to the repository, it can never tidy the inbox.
Files accumulate, every poll sees all of them, and deduplication is entirely
local and permanent — by `MealEntry.id`, in `MealLog.importedIds`. That is why
deleting a meal keeps its id: without that, the next poll would bring it back.

## Bash output is filtered before it reaches the model

`.claude/settings.json` registers a `PreToolUse` hook on `Bash` that rewrites
each command through [rtk](https://github.com/rtk-ai/rtk): `git status` runs as
`rtk git status`, and rtk compresses the output. Write commands normally — the
rewrite is transparent.

The `Read`, `Grep` and `Glob` tools are not `Bash`, so they never reach the hook
and are unaffected; a deliberate `rg` or `find` in a shell *is* filtered. To
bypass filtering for one command, run `rtk proxy <cmd>`.

`.claude/hooks/session-start.sh` installs rtk at the start of every session,
because the container starts empty. It pins `RTK_VERSION`, because the installer
otherwise asks `api.github.com` for the latest tag and the proxy answers 403 for
any repository outside this session's scope — the lookup fails and takes the
install with it. Bump that variable by hand. The hook is written not to matter
when it fails: with rtk absent the rewrite no-ops and the only cost is verbose
output.

## A permanent fix for the CI suppression, if you want one

It is a property of the sandbox's credential, so it cannot be fixed from inside
a session. Giving the environment a personal access token to push with would
make pushes and PRs behave normally. Until then, dispatching is the workaround.
