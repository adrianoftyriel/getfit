# GetFit

An Android app for planning and tracking workouts, and for tracking what gets
eaten — including meals estimated from a photograph by Claude.

Kotlin and Jetpack Compose, minSdk 24, no backend. Everything it stores is on
the phone; the only network calls it makes are two read-only requests to this
repository, for updates and for meals.

## Install

Download the newest production APK:

**https://github.com/adrianoftyriel/getfit/releases/latest/download/GetFit.apk**

You will need to allow *Install unknown apps* for whatever opens it. Builds are
debug-signed, which is why the signing key is committed — every build is signed
with the same one, so updates install over each other instead of being refused.

Once installed, the app updates itself: **Settings → Check for updates**, or on
launch if that is left on.

## Two channels

| | Production | Dev |
|---|---|---|
| Built from | `main` | `dev` |
| By | `release.yml` | `ci.yml` |
| Published as | a release | a prerelease |
| Package | `org.getfit.app` | `org.getfit.app.dev` |
| Called | GetFit | GetFit DEV |
| Colour | orange | teal |

They install side by side and update independently. **A build only ever updates
within its own channel** — the version sequences are unrelated, so a dev build
numbered higher than a production one is not newer than it, and offering it
across would hand a phone something nobody meant it to have. There is no setting
for this; the channel is read from the running APK.

To follow the dev channel, install a dev APK from any prerelease on the
[releases page](https://github.com/adrianoftyriel/getfit/releases). It will keep
itself up to date from there.

## Meals from a photograph

The `meal-photo` skill in `.claude/skills/` estimates the calories and
macronutrients in a picture of food and gets the result into the app. Show
Claude a photo of a meal and ask it to log it.

It reaches the phone two ways:

- **The inbox** — the estimate is committed to the `nutrition-inbox` branch, and
  the app collects it on launch. Analyse a meal at a desk, and it is on the
  phone by the time you look.
- **A link** — `getfit://meal?…` carries the whole meal inside it, for when
  there is no network. Tapping it opens the app and asks you to confirm.

Nothing is logged without confirmation on the link route, and estimates carry a
confidence and the assumptions they rested on. A photograph cannot show the oil
in the pan.

## Version numbers

A version is `<series>.<run number>`, and a dev build adds `-dev`. The series is
set by hand in `gradle.properties`; the number after it is the CI run number, so
it climbs on its own and never goes backwards when the series is bumped. Both
workflows read the series out of that same file when they name a tag, so a
release tag and the APK inside it cannot disagree.

## Building it yourself

```bash
./gradlew assembleDebug
```

An unstamped local build is a dev build: `org.getfit.app.dev`, versioned
`<series>.0-dev`, versionCode 1. It will not be offered as an update to
anything, which is the point.

## Contributing

Both branches publish, so a merge is a release rather than an integration.
[`CLAUDE.md`](CLAUDE.md) has the working loop, and the reasons behind the parts
of this that are not obvious.
