# Budget Plan cases (FR-032)

The golden table of the `BudgetPlanner` (DEV-042, technical-design §8.3). `BudgetPlanCasesTest` in `:core:domain` reads this file and checks every row, so a row that disagrees with the planner fails `./gradlew verify`. The expected values were computed by an independent exhaustive search (set convolution over every person's choices, without the planner's dynamic program), the same method testing-plan §5.4b used for TC-161–TC-165.

**Data used for every row.** The tagged sample menu of `api-spec.md` §4 (kid-friendly mains b1, c3, r2; kid-friendly drinks d2, d4; c4 Family Bucket left out because `servesAdults` is 4) and the E5 sample config: promo Rp 10.000 off at Rp 100.000 or more, PB1 10 %, rounding None, `overCapPercent` 10, `maxGroupSize` 10. Each item keeps its default options or the first option of a group without one (A-42); combos keep their first side and drink at Regular size.

**Plan rules.** Each adult has a main and a drink, or a one-person combo; two adults may share a two-person combo; each child has a kid-friendly main and a kid-friendly drink (BR-13, BR-14). The table may add one side or dessert per person. A person whose meal is a combo already has a side, so combo diners add no extra side and desserts are unaffected (OQ-15, applied in 0.6.0); the design prototype's cap at min(people, 4) extras is not applied, because these cases need one extra per person. Plans compare on the final total (BR-15).

**Columns.** *Options* are the Step 2 choices that differ from the default: the chips "Add sides" and "Add desserts" and the switch "Allow a little over budget" (off is `strict`, on is Up to +10 %). *Hero* is the plan kind shown first (`EXACT`, `UNDER`, `TOO_LOW`, `MORE_THAN_ENOUGH`). *Just over* is the over plan's total, or `—` when there is none.

| Case | Budget | Adults | Children | Options | Hero | Hero total | Just over |
|---|---|---|---|---|---|---|---|
| TC-161 | 150.000 | 2 | 1 | — | UNDER | 149.600 | 150.700 |
| TC-162 | 99.000 | 1 | 1 | — | EXACT | 99.000 | — |
| TC-163 | 100.000 | 2 | 1 | — | TOO_LOW | 116.600 | — |
| TC-164 a | 1.000.000 | 1 | 0 | — | MORE_THAN_ENOUGH | 105.600 | — |
| TC-165 a | 120.000 | 1 | 2 | — | UNDER | 118.800 | 121.000 |
| Strict | 150.000 | 2 | 1 | strict | UNDER | 149.600 | — |
| No sides or desserts | 150.000 | 2 | 1 | no sides, no desserts | UNDER | 149.600 | 150.700 |
| No desserts | 150.000 | 2 | 1 | no desserts | UNDER | 149.600 | 150.700 |
| One adult | 50.000 | 1 | 0 | — | UNDER | 48.400 | 50.600 |
| One adult, round budget | 60.000 | 1 | 0 | — | UNDER | 59.400 | 60.500 |
| Couple | 100.000 | 2 | 0 | — | UNDER | 99.000 | 100.100 |
| Family of four | 200.000 | 2 | 2 | — | UNDER | 199.100 | 200.200 |
| Six adults | 350.000 | 6 | 0 | — | UNDER | 349.800 | 350.900 |
| Ten adults (TC-168 load) | 600.000 | 10 | 0 | — | UNDER | 599.500 | 600.600 |
| Five plus five | 500.000 | 5 | 5 | — | UNDER | 499.400 | 500.500 |
| Odd budget | 123.400 | 2 | 1 | — | UNDER | 123.200 | 125.400 |
