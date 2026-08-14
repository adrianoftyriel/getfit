# Estimating portions and nutrition from a photograph

Reference values for the `meal-photo` skill. Everything here is per 100 g of the
food **as eaten** unless it says otherwise — raw and cooked weights differ
enough to matter, and rice is the usual place that goes wrong.

These are typical values, good to roughly ±15%. That is the honest precision of
this exercise; quoting more is false confidence.

## Scale anchors

Portion size is where nearly all the error lives. Find something in the frame
whose size is known:

| Object | Size |
|---|---|
| Dinner plate | 26–28 cm across |
| Side plate | 19–21 cm |
| Fork, table | 19 cm |
| Teaspoon bowl | 3 cm |
| Standard mug | 350 ml, 9 cm tall |
| Drinks can | 330 ml, 12 cm tall |
| Chicken breast | Palm-sized, 150–200 g |
| Slice of bread | 10 × 10 cm, 35–40 g |
| Egg, large | 55–60 g |

Hand comparisons, when a hand is in shot: a cupped palm holds about 150 ml, a
closed fist is about 250 ml, a thumb tip is about 1 tablespoon.

## Common foods, per 100 g as eaten

### Starches
| Food | kcal | P | C | F |
|---|---|---|---|---|
| Rice, white, boiled | 130 | 2.4 | 28 | 0.3 |
| Rice, brown, boiled | 123 | 2.7 | 26 | 1.0 |
| Pasta, boiled | 158 | 5.8 | 31 | 0.9 |
| Potato, boiled | 87 | 1.9 | 20 | 0.1 |
| Potato, roast | 149 | 2.9 | 26 | 4.5 |
| Chips / fries | 312 | 3.4 | 41 | 15 |
| Bread, white | 265 | 9.0 | 49 | 3.2 |
| Bread, wholemeal | 247 | 13 | 41 | 3.4 |
| Couscous, cooked | 112 | 3.8 | 23 | 0.2 |
| Oats, dry | 379 | 13 | 68 | 6.5 |

### Proteins
| Food | kcal | P | C | F |
|---|---|---|---|---|
| Chicken breast, grilled | 165 | 31 | 0 | 3.6 |
| Chicken thigh, skinless | 179 | 24 | 0 | 8.2 |
| Beef mince, 5% fat | 137 | 21 | 0 | 5.0 |
| Beef mince, 20% fat | 254 | 17 | 0 | 20 |
| Steak, sirloin, lean | 206 | 30 | 0 | 9.0 |
| Pork loin | 242 | 27 | 0 | 14 |
| Salmon, baked | 208 | 20 | 0 | 13 |
| White fish, baked | 105 | 23 | 0 | 1.2 |
| Prawns | 99 | 24 | 0.2 | 0.3 |
| Egg, whole | 143 | 13 | 0.7 | 9.5 |
| Tofu, firm | 144 | 17 | 3.0 | 8.7 |
| Lentils, boiled | 116 | 9.0 | 20 | 0.4 |
| Black beans, boiled | 132 | 8.9 | 24 | 0.5 |
| Chickpeas, boiled | 164 | 8.9 | 27 | 2.6 |

### Dairy
| Food | kcal | P | C | F |
|---|---|---|---|---|
| Milk, whole | 61 | 3.2 | 4.8 | 3.3 |
| Milk, skimmed | 34 | 3.4 | 5.0 | 0.1 |
| Greek yoghurt, 0% | 59 | 10 | 3.6 | 0.4 |
| Greek yoghurt, full | 97 | 9.0 | 3.6 | 5.0 |
| Cheddar | 402 | 25 | 1.3 | 33 |
| Mozzarella | 280 | 28 | 3.1 | 17 |
| Butter | 717 | 0.9 | 0.1 | 81 |

### Vegetables and fruit
Most non-starchy vegetables land at 20–40 kcal per 100 g; unless a large volume
is present, precision here is not what decides the total.

| Food | kcal | P | C | F |
|---|---|---|---|---|
| Mixed salad leaves | 17 | 1.4 | 2.9 | 0.2 |
| Broccoli, steamed | 35 | 2.4 | 7.2 | 0.4 |
| Sweetcorn | 96 | 3.4 | 21 | 1.5 |
| Peas | 81 | 5.4 | 14 | 0.4 |
| Avocado | 160 | 2.0 | 8.5 | 15 |
| Banana | 89 | 1.1 | 23 | 0.3 |
| Apple | 52 | 0.3 | 14 | 0.2 |
| Berries | 45 | 1.0 | 10 | 0.4 |

### Fats, sauces and the things a photo hides
This section is the one that matters. These are what turn a 500 kcal plate into
an 800 kcal plate without changing how it looks.

| Item | Typical serving | kcal |
|---|---|---|
| Oil, cooking, per tablespoon | 15 ml | 120 |
| Oil absorbed, pan-fried item | — | 50–150 |
| Oil absorbed, deep-fried item | — | 150–300 |
| Butter on bread | 10 g | 72 |
| Mayonnaise | 1 tbsp | 94 |
| Salad dressing, vinaigrette | 2 tbsp | 140 |
| Ketchup | 1 tbsp | 19 |
| Soy sauce | 1 tbsp | 8 |
| Curry sauce, creamy | 100 g | 150–250 |
| Tomato pasta sauce | 100 g | 60 |
| Cheese, grated on top | 30 g | 120 |
| Sour cream / crème fraîche | 2 tbsp | 60 |
| Peanut butter | 1 tbsp | 96 |

**Assume some cooking fat unless the dish is visibly steamed, boiled or
grilled.** A restaurant plate almost always carries more than a home one. Say
what you assumed in `notes` — it is the single most useful thing you can write
there.

## Cooked and raw

Weights change on cooking, and a photograph shows the cooked weight:

- Rice and pasta take up roughly **2.5×** their dry weight in water.
- Meat loses roughly **25%** of its weight.
- Leafy greens collapse to about **⅓**.

So 75 g of dry rice is about 190 g on the plate; a 200 g raw chicken breast
serves at about 150 g.

## Composed dishes

For a mixed dish where components cannot be separated, estimate the dish as a
whole against a known comparator rather than pretending to itemise it. Typical
restaurant servings:

| Dish | kcal |
|---|---|
| Burrito bowl | 600–900 |
| Margherita pizza, 12" | 900–1100 |
| Beef burger and fries | 900–1300 |
| Chicken curry with rice | 700–1000 |
| Pad thai | 700–950 |
| Caesar salad with chicken | 450–700 |
| Full English breakfast | 800–1200 |
| Sushi, 8 pieces | 350–500 |
| Croissant | 230–280 |
| Blueberry muffin | 380–450 |

Where a range is this wide, use the middle of it, set `confidence` to `LOW`, and
say in `notes` which end you think it sits at and why.
