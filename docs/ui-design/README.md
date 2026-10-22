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
