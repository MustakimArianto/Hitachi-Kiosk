# Hitachi Kiosk — UI design source ("Linen & Paprika")

Published design: https://claude.ai/artifact/Lv6W9bttmG8fH4ho4CkWMg (Claude Design canvas). These files mirror it.

- `canvas.json` — artboard index and layout. One `*.dc.html` per artboard, 1080 × 1920 portrait.
- Main flow: `Main` (splash) → `Login` → `Merchants` → `OrderType` → `Menu` (→ `Combo`, `ItemDetail`, `BudgetPas`) → `Upsell` → `Payment` → `Checkout` → `Complete` → `Idle`; `StillThere` is the inactivity overlay.
- Staff area: `Settings`, `SettingsPrinter`, `SettingsPayment`, `SettingsKiosk`, `SettingsDevice`, `ChangePin`.
- `S_<Screen>_<scenario>.dc.html` — state boards. Each imports the real screen with a `scenario` prop (for example `S_Checkout_expired` = `Checkout` with `scenario="expired"`). Every screen with states exposes the same `scenario` prop, so the list of valid values is in that screen's `data-props`.
- `Photo.dc.html` — image slot component used by other artboards (not a screen).

## Tokens
bg `#FAF8F5` · surface `#FFFFFF` · soft `#F1EDE7` · line `#E9E4DC` · ink `#171513` · mute `#6F6961` · accent `#C8552B` (prop) · success `#2F7A50`.
Font: Schibsted Grotesk 400–800. Scale: display 112/800, title 80/700, section 44/700, item 36/600, body 30/400, button 34/700, meta 26/500; nothing below 24 px.
Layout: 72 px side margins, 8-pt spacing, radii 40/32/pill, touch targets ≥ 96 px, one accent action per screen in the bottom dock.
Language: English only (the language switch is deferred).

## Photos and logos
How the app crops photos, what a photo needs and where the merchant logo is used: [`photo-guidelines.md`](photo-guidelines.md). The design shows the Order type mark, the headers and the QR centre mark as the ink tile with the accent dot; the built app draws the merchant logo over that tile when the config has one. On the Choose a restaurant card the logo fills the whole picture box instead of the cover photo and the small chip (PO, 2026-10-25).

## Where the built app differs
The design files stay as they are. Each milestone's deviations are in `docs/test-reports/<version>.md`. For Checkout (0.5.0):
- The QR centre mark is 96 dpx, which is 20 % of the 480 dpx code. The design draws 104 dpx (21.7 %). The code is level H, so it decodes with the mark on (technical-design §8.5, TD-07).
- The real level-H QR is denser than the design's pseudo QR. The card shadow is `ink` at 0.06 alpha through the floating elevation.
- The "Prototype only · simulate the result" bar is debug-only. The QR and card goldens leave it out, because the goldens follow the release build.
- A card payment cancelled on the terminal shows "Payment cancelled" / "The payment was cancelled on the terminal. Try again or choose another method." The design text for `S_Checkout_cancelled` is QR-only (A-45).
- The "Your order is saved" note (without "You have not been charged") shows only on the "Ordering is paused" failure (`S_Checkout_offline`). The other failures show the green "You have not been charged · your order is saved" note only when the backend reported a terminal non-paid status (A-45).
- The method switcher's thumb shadow bleeds slightly, and the entrance and shake animations are approximated.
