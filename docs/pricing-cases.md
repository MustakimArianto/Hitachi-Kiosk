# Pricing cases (FR-013, TC-060)

The golden table of the `PricingEngine` (DEV-035, technical-design §8.1). `PricingCasesTest` in `:core:domain` reads this file and checks every row, so a row that disagrees with the engine fails `./gradlew verify`. Edit a row only when FR-013 changes.

**Rules used for every row** (the api-spec E5 sample config): promo `promo_10k_over_100k` (Rp 10.000 off when the subtotal is at least Rp 100.000, one promo per order, BR-03); PB1 10 % (`rateBasisPoints` 1000), exclusive, `tax = (base × 1000 + 5.000) / 10.000` in integers on `base = subtotal − discount` (half-up, BR-02); the total rounded **up** to the step, with the difference as the Rounding line (BR-04). Amounts are whole Rupiah written with `.` thousands separators (BR-01).

**Columns.** *Lines* are the line totals that make the subtotal (`2×16.000` is a line of quantity 2 at Rp 16.000). *Step* is the rounding step (0 = None). The other columns are the expected `Totals`.

| # | Case | Lines | Step | Subtotal | Discount | Tax | Rounding | Total |
|---|---|---|---|---|---|---|---|---|
| P01 | Design sample (FR-013 AC1) | 75.000 + 38.000 + 22.000 + 2×16.000 | 0 | 167.000 | 10.000 | 15.700 | 0 | 172.700 |
| P02 | Design sample, step Rp 100 | 75.000 + 38.000 + 22.000 + 2×16.000 | 100 | 167.000 | 10.000 | 15.700 | 0 | 172.700 |
| P03 | Design sample, step Rp 500 (FR-013 AC3) | 75.000 + 38.000 + 22.000 + 2×16.000 | 500 | 167.000 | 10.000 | 15.700 | 300 | 173.000 |
| P04 | Just under the threshold (FR-013 AC2) | 99.999 | 0 | 99.999 | 0 | 10.000 | 0 | 109.999 |
| P05 | On the threshold | 100.000 | 0 | 100.000 | 10.000 | 9.000 | 0 | 99.000 |
| P06 | Empty cart | — | 0 | 0 | 0 | 0 | 0 | 0 |
| P07 | Empty cart, step Rp 500 | — | 500 | 0 | 0 | 0 | 0 | 0 |
| P08 | Half-up tax (TC-057) | 12.345 | 0 | 12.345 | 0 | 1.235 | 0 | 13.580 |
| P09 | Half-down tax | 12.344 | 0 | 12.344 | 0 | 1.234 | 0 | 13.578 |
| P10 | One drink | 8.000 | 0 | 8.000 | 0 | 800 | 0 | 8.800 |
| P11 | One drink, step Rp 500 | 8.000 | 500 | 8.000 | 0 | 800 | 200 | 9.000 |
| P12 | Two mains | 38.000 + 52.000 | 0 | 90.000 | 0 | 9.000 | 0 | 99.000 |
| P13 | Two mains, step Rp 500 | 38.000 + 52.000 | 500 | 90.000 | 0 | 9.000 | 0 | 99.000 |
| P14 | Promo makes it cheaper than less food | 90.000 | 0 | 90.000 | 0 | 9.000 | 0 | 99.000 |
| P15 | Promo exactly at 100.000 via quantity | 4×25.000 | 0 | 100.000 | 10.000 | 9.000 | 0 | 99.000 |
| P16 | Combo and fries | 56.000 + 18.000 | 100 | 74.000 | 0 | 7.400 | 0 | 81.400 |
| P17 | Feast for Two, large | 121.000 | 500 | 121.000 | 10.000 | 11.100 | 400 | 122.500 |
| P18 | Family Bucket and drinks | 129.000 + 4×14.000 | 0 | 185.000 | 10.000 | 17.500 | 0 | 192.500 |
| P19 | Twenty sundaes | 20×15.000 | 500 | 300.000 | 10.000 | 29.000 | 0 | 319.000 |
| P20 | Ten water | 10×8.000 | 100 | 80.000 | 0 | 8.000 | 0 | 88.000 |
| P21 | Odd amount, step Rp 100 | 10.001 | 100 | 10.001 | 0 | 1.000 | 99 | 11.100 |
| P22 | Exactly on a step | 50.000 | 500 | 50.000 | 0 | 5.000 | 0 | 55.000 |
| P23 | Large order | 20×52.000 + 20×59.000 + 20×18.000 | 500 | 2.580.000 | 10.000 | 257.000 | 0 | 2.827.000 |
| P24 | Budget Plan under (TC-161) | 146.000 | 0 | 146.000 | 10.000 | 13.600 | 0 | 149.600 |
| P25 | Budget Plan exact (TC-162) | 100.000 | 0 | 100.000 | 10.000 | 9.000 | 0 | 99.000 |
