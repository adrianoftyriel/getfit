---
name: meal-photo
description: Estimate the calories and macronutrients in a photographed meal and publish it to the GetFit app. Use when the user shares a picture of food, asks what is in a meal, asks to log or track a meal, or asks how many calories something is. Also use when they ask to send a meal to GetFit or to their phone.
---

# Analysing a meal photograph for GetFit

Turn a picture of food into a nutrition estimate the GetFit app can hold, and
get it onto the phone.

The estimate is yours; the arithmetic, the encoding and the checking belong to
`scripts/publish_meal.py`. Do not hand-write the JSON file or the base64 link —
the script exists because a link encoded by eye is wrong nearly every time, and
a meal whose numbers do not add up is worse than no meal at all, being
indistinguishable from one that does.

## 1. Read the photograph

Name the components before costing any of them. For each, decide *what* it is
and *how much* is there — the second is where nearly all the error lives, so
say what you took the portion to be rather than only what you concluded.

Anchor portions against something visible: a dinner plate is about 27 cm, a
standard fork about 19 cm, a chicken breast about the size of a palm. When a
scale reference is genuinely absent, say so in `notes` and drop `confidence`.

`references/estimation.md` has portion anchors, per-100 g values for common
foods, and the traps — cooking oil, dressings, sauces — that a photograph hides.

## 2. Cost it

Estimate each item, then the meal as a whole. The script checks two things you
should get right first:

- **Items against the total.** They should agree within about 10%.
- **Macros against calories.** Protein and carbohydrate are 4 kcal/g, fat 9.
  If `4P + 4C + 9F` is more than 20% away from the calorie figure, the two were
  not estimated from the same portions.

Set `confidence` honestly. It is shown to the reader, and it is the difference
between a number that is useful and one that is merely present:

| | when |
|---|---|
| `HIGH` | Clear photo, packaged food, or the user stated the portions |
| `MEDIUM` | Ordinary case — the food is identifiable, the portions inferred |
| `LOW` | Obscured, mixed or sauced dish; a guess worth marking as one |

Put whatever the estimate depended on assuming into `notes` — "assumed no added
oil", "dressing not visible, allowed 100 kcal". This is the field that lets
somebody correct you later.

## 3. Publish it

Pipe the meal to the script from the repository root:

```bash
echo '{
  "label": "Chicken burrito bowl",
  "capturedAt": 1723600000,
  "confidence": "MEDIUM",
  "notes": "Assumed no added oil in the rice.",
  "items": [
    {"name": "Chicken thigh", "quantity": "150 g", "grams": 150,
     "nutrition": {"calories": 295, "proteinG": 25, "carbsG": 0, "fatG": 21}},
    {"name": "Rice", "quantity": "1 cup", "grams": 190,
     "nutrition": {"calories": 240, "proteinG": 4.4, "carbsG": 53, "fatG": 0.4}}
  ],
  "totals": {"calories": 640, "proteinG": 36.4, "carbsG": 72, "fatG": 21.8, "fibreG": 9}
}' | python3 .claude/skills/meal-photo/scripts/publish_meal.py
```

It writes `nutrition/inbox/<id>.json` and prints a `getfit://` link. Anything on
stderr beginning `note:` is worth reading back to the user; a non-zero exit
means the meal was refused and needs fixing, not retrying.

Fields: `label` and `totals.calories` are required, everything else is optional.
`capturedAt` is epoch seconds and defaults to now — set it when logging a meal
eaten earlier. `id` defaults to `<capturedAt>-<slug>`, which is deliberate:
publishing the same meal twice overwrites one file instead of creating two, and
the app dedupes on this id permanently. Nutrition keys are exactly `calories`,
`proteinG`, `carbsG`, `fatG`, `fibreG`, `sugarG`, `sodiumMg` — a misspelled key
is refused rather than silently counted as zero. Pass `--dev` when the user runs
the GetFit DEV build.

### Getting it to the phone

**The inbox** is the normal route. Commit the file the script wrote to the
`nutrition-inbox` branch — the app polls that directory on launch and collects
whatever it has not already imported:

- `mcp__github__create_or_update_file` with `branch: "nutrition-inbox"`,
  `path: "nutrition/inbox/<id>.json"`.
- If the branch does not exist yet, `mcp__github__create_branch` first.

It is a branch of its own on purpose: publishing lunch is not a commit to the
code, and it must never appear in a diff or trigger a build. Never commit a meal
to `dev` or `main`.

The app cannot write to the repository — it holds no token — so it can never
tidy up after itself. Inbox files accumulate, and that is expected: dedupe is
the app's job and it does it by `id`.

**The link** is the fallback: no network, nothing to poll, the whole meal
travels inside it. Give the user the `getfit://` line when they are holding the
phone, when the inbox is not reachable, or when they ask for it. Tapping it
opens GetFit and asks them to confirm the meal before it is logged.

## 4. Tell the user what you decided

Report the total, the confidence, and the assumptions that moved the number
most. If a component was genuinely unreadable, say which one rather than
averaging the difficulty away into a total that looks as solid as any other.

A photograph cannot show oil absorbed in cooking, sugar in a sauce, or what is
under the top layer. An estimate that admits its limits is more useful than one
that does not, and the user is the only one who can supply what the picture
left out.
