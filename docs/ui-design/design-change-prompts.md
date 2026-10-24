# Design change prompts for the 0.6.0 rework

Paste these into the design chat, in order. Review each result before the next one. Prompt 1 matters most. Items marked "build last" are lower priority (P2) and can be skipped if the design chat runs short.

Source of the changes: PRD 1.9 (D-33, D-34, D-35), `docs/dev-plan.md` §0.6.0 (DEV-104) and `docs/testing-plan.md` §5.4c.

## Decisions taken (so the prompts and the PRD agree)

| Item | Decision | Why |
|---|---|---|
| OQ-15: extras cap and combo rule | Both apply. A diner whose meal is a combo already has a side, so the side allowance is people minus combo diners. Total extras (sides plus desserts) are also capped at min(people, 4), as in the design prototype. | 10 sides for 10 people looks wrong, and a combo's fries plus an extra side reads as a bug. |
| OQ-16: preselected budget chip | No chip is preselected. The budget starts empty. The "About Rp X per person" caption helps people choose. | A preselected chip is a hidden price anchor. |
| OQ-17: "Make it a meal" flag | Add an optional `purpose: "MEAL_UPGRADE"` on the modifier group. The strip card for it is a P2 state. | Guessing from the group's shape also matches groups like "Make it spicy?". |
| A-47: recovered paid order | Accepted: the receipt prints order number, totals and payment details only, and the kitchen ticket is skipped. | The 0.5.0 recovery does not store order lines, and the kitchen already has the backend order (D-10). Complete needs no design change. |

Two points the prompts add that the PRD must still reflect:
- An added strip card stays in place as a stepper until the screen is reopened (so the undo stays visible).
- The strip is hidden on the blocking Your order boards (offline, no methods, price changed, item unavailable, empty tray).

## Shared context (paste this first)

```text
You are updating the "Hitachi Kiosk" design canvas (Linen & Paprika). It is an Android self-order kiosk, portrait 1080 x 1920. Several screens change. I will send four prompts after this one. In this message, do not change anything yet. Confirm you have understood the rules below, then wait.

KEEP EVERYTHING EXISTING UNLESS A PROMPT SAYS OTHERWISE
- Tokens: bg #FAF8F5, surface #FFFFFF, soft #F1EDE7, line #E9E4DC, ink #171513, mute #6F6961, accent #C8552B (a prop), success #2F7A50.
- Font: Schibsted Grotesk 400-800. Scale: display 112/800, title 80/700, section 44/700, item 36/600, body 30/400, button 34/700, meta 26/500. Nothing smaller than 24 px.
- Layout: 72 px side margins, 8-pt spacing, radii 40 / 32 / pill, touch targets at least 96 px, one accent action in the bottom dock per screen. English only.
- Every state board is named S_<Screen>_<scenario> and imports the real screen with a `scenario` prop. EXTEND the scenario props of the existing screens. Do not duplicate screens.
- Photos: slots use cover crop through the existing Photo component. A 2:1 photo (subject centred, at most 82% of the height) must work in every slot.
- Do NOT change: Checkout, Complete, StillThere (the inactivity overlay and its timing), Idle, Order type, the Login/Merchants screens, Settings screens other than printers, and the tokens.

HOW TO DELIVER
- Update the existing files, add new scenario boards, and keep naming consistent.
- Finish with: (1) the list of every file changed, (2) every new `scenario` value per screen with what it shows, (3) anything you could not fit, and why.
- If a requirement below collides with a size limit, tell me and propose a fix. Do not shrink touch targets below 96 px or text below 24 px.
```

## Prompt 1: Upsell becomes a strip on Your order (Payment.dc.html, Upsell retired)

```text
CHANGE 1 of 4: Upsell is no longer a screen. It becomes a suggestion strip inside "Your order" (Payment.dc.html).

A. RETIRE THE OLD SCREEN
- Upsell.dc.html and S_Upsell_noSuggestions.dc.html are retired. Remove them from the main flow and from canvas.json's flow, and move them to a clearly labelled "Retired" group. Do not delete them.
- New flow: Menu -> (View order) -> Your order. There is no screen in between. Update the flow notes accordingly.

B. THE STRIP ON YOUR ORDER
- Place: the LAST element of the scrolling order list, below the last line and below any banners (such as the promo nudge). It scrolls with the list. It is not pinned. The dark summary panel and the Pay dock do not change.
- Header: "Goes well with your order" in the item style (36/600), 40 px above it, 24 px below it. No subtitle.
- Cards: exactly 3 cards in one row. Width 296, gap 24 (936 total inside the 72 margins). Card: white surface, 1 px line border #E9E4DC, radius 32, padding 24.
  - Top: photo 296 x 168 (cover, rounded top corners), with a small kind chip over the photo's top-left corner: "Drink", "Side" or "Dessert" (meta 26/500, soft pill).
  - Name: 30/600, up to 2 lines.
  - Price: 30/600 under the name, e.g. "Rp 8.000".
  - Action row, 96 px high, full inner width (248):
    * DEFAULT: an ink pill "+ Add" (white label, button 34/700).
    * ADDED: the pill becomes a stepper "-  1  +" (96 / 56 / 96 wide), with an accent border and the quantity in accent. The minus returns to the default pill when the quantity reaches 0. An added card stays in place while the screen is open; it only re-ranks the next time the screen opens.
  - The card height will be about 450. Tune spacing if you must, but keep touch targets at 96 and text at 24 or more.
- Order of cards: one card per missing "gap", in this priority: Drink, Side, Dessert. Show the first three that apply. Use items that already exist in the design's sample menu (a drink, a side such as the cheese fries, a dessert such as the Caramel Sundae).
- When the strip would be empty, nothing is drawn (no heading, no empty state).
- The strip is hidden in the blocking boards: S_Payment_offline, S_Payment_priceChanged, S_Payment_itemUnavailable, S_Payment_noMethods. S_Payment_emptyTray has no strip either.

C. NEW SCENARIOS on Payment.dc.html (and a state board for each)
1. default: the design sample cart with the strip showing Drink, Side, Dessert.
2. upsellAdded: same, with the Drink card in the ADDED state (quantity 1) and the new drink line visible in the list above with its normal added look; totals in the summary panel updated.
3. upsellBudget: the order came from a Budget Plan with the plan tag visible ("Budget Plan - 2 adults - 1 child - Rp 150.000") and only Rp 10.000 left of the budget. The strip shows just ONE card (a Drink at Rp 8.000), left-aligned at card width. Add a small mute caption under the heading: "Fits your budget". (This caption is a proposal; keep it if it reads well.)
4. upsellMeal (lower priority, build it last): a main-dish line with the meal option off. Its card reads "Make it a meal", note "Adds fries & a drink", price "+Rp 18.000" (the option's price change), with a "+ Add" pill. It replaces the Drink card as the first card. After the tap, the meal line changes in the list: its description gains "Meal" and the line price rises by Rp 18.000.
5. planAdded: arriving from Budget Plan. A toast at the top, "Plan added", visible for 2 s (success colour), over the screen. The plan tag row is visible.
6. planTag: the plan tag row with a trailing text button "Edit plan" (accent, 96 px touch target) at the right edge of the tag row. Today the tag row is "Budget Plan - 2 adults - 1 child - Rp 150.000".

D. EMPTY ORDER (S_Payment_emptyTray)
- Keep "Your tray is empty" and the "Back to menu" button. Add a secondary text button under it: "Plan by budget" with the people icon from the Menu row. It opens the Budget Plan screen. Add a scenario value to show it, plus one without it (when the Budget Plan feature is off).

E. PHOTO GUIDELINES
- Add one row to docs/ui-design/photo-guidelines.md's slot table (or tell me the text to add): "Your order suggestion card | 296 x 168 (1.76:1)".
```

## Prompt 2: Budget Plan Step 1, the keypad and the Menu entry (BudgetPas.dc.html, Menu.dc.html)

```text
CHANGE 2 of 4: Budget Plan Step 1 "Your table" and the number pad. Make it easier for a family at a kiosk. The rule is fewer decisions, plain words.

STEP 1 CHANGES
1. DEFAULTS: Adults start at 2, Children at 0. The budget starts EMPTY. No budget chip is preselected (do not suggest a price).
2. PER-PERSON CAPTION: under the large budget amount and its existing caption ("Final total, tax included - exact to the nearest Rp 100"), add a second line in mute meta style: "About Rp 50.000 per person". It shows only when a budget is set. It is the budget divided by the number of people (adults + children), rounded down to the nearest Rp 100. Sample: Rp 150.000 for 3 people = "About Rp 50.000 per person".
3. DISABLED DOCK: while the budget is empty, "See plans" is disabled and the dock text (where it normally shows "{n} people - Rp X") reads "Choose a budget to continue" (not a silent disabled button).
4. THE GROUP-TOO-LARGE state stays as designed.

NUMBER PAD ("Enter your budget")
- When reopened, it shows the current amount, not an empty field.
- Thousand separators while typing (Indonesian style: Rp 150.000).
- Below the amount, a helper line: "Exact to the nearest Rp 100. Plans match your final total, tax included." (existing copy).
- ERROR state: if the amount is below Rp 1.000, show "Minimum Rp 1.000" in the warning style under the amount, and the "Use this budget" button is disabled and shows the same reason as its label ("Minimum Rp 1.000"). Add a scenario for this (keypad open, amount 500).

MENU ENTRY FOR BUDGET PLAN (Menu.dc.html)
1. The slim dark row at the top of Popular changes copy: title "Eating with a group?", subtitle "Tell us your budget and we pick the meal". Keep the arrow button and the 128 px height.
2. NEW: a compact "Plan by budget" pill in the Menu HEADER so the feature is reachable from EVERY category tab (today it exists only on Popular). The pill: people icon + "Plan by budget" (30/600), 96 px high, dark ink fill with white text, placed in the header next to the existing "Dine in - Change" and "My order" pills. If the header is too crowded at 1080 px, put it instead as the first FIXED (non-scrolling) pill in the category pill row, visually distinct (dark, with the people icon). Tell me which you chose.
3. Add a Menu scenario that shows a non-Popular tab (for example `drinks`) where the pill is visible and the slim row is NOT shown. Hide both when the Budget Plan feature is off (add that scenario too).
4. Use the same people icon in the empty "Your order" button from change 1.

SCENARIOS TO ADD OR UPDATE (BudgetPas.dc.html and S_BudgetPas_*)
- step1 (default): 2 adults, 0 children, empty budget, disabled dock "Choose a budget to continue".
- step1Budget: budget Rp 150.000 selected on the chip, 2 adults + 1 child, caption "About Rp 50.000 per person", dock enabled.
- keypadMin: keypad open, amount 500, "Minimum Rp 1.000".
- Update S_BudgetPas_group so it still works with the new defaults.
```

## Prompt 3: Budget Plan Step 2, swap picker and states (BudgetPas.dc.html)

```text
CHANGE 3 of 4: Budget Plan Step 2 "Your plan". Goal: the customer sees both plans, sees the food first, and does not need to understand jargon.

A. TWO PLANS ARE BOTH VISIBLE
- Remove the "Just under | Just over" segmented control and the single hero card behaviour. When both an under and an over plan exist, show two stacked selectable plan cards (each full width, radius 40):
  1. "Within your budget" - total Rp 149.600 - badge "Rp 400 left" - SELECTED by default (accent ring, filled radio at the left).
  2. "A little extra" - total Rp 150.700 - badge "+Rp 700 over" - unselected (line border, empty radio).
  Each card also shows "{n} people - {k} items - tax included". Tapping a card selects it; the dock total and the content below follow the selected plan.
- The title stays "Two plans for you". The exact-match board ("A perfect fit") keeps ONE card with the "Exact match" badge and no radio, as designed. Keep the title rules: "A perfect fit" for exact, "Your plan" otherwise.
- Sample numbers: 2 adults + 1 child, Rp 150.000: Rp 149.600 and Rp 150.700.

B. "WHAT'S INCLUDED" IS OPEN BY DEFAULT
- Show one MEAL per person with a 80 px square thumbnail, name and what it contains, grouped by person: "Adult 1", "Adult 2", "Child 1 - kid-friendly". A meal that is a combo reads "Cheeseburger Combo" with the contents under it "fries, cola". A meal that is a main plus a drink reads "Cheeseburger + Cola". The customer must not need to know which is which. Shared items (sides and desserts for the table) are in a "For the table" group.
- "Price details" stays collapsed, with "Rp X" at the right. When expanded it lists Subtotal, Promo, PB1, Rounding and Total, and, only when combos are used, a green line "Combo savings Rp 4.000". Add an expanded scenario.

C. OPTIONS ROW (replaces the Customize sheet's toggles and the "Strict | Up to +10 %" control)
- Directly under the plan cards, a single row:
  * Two chips with a check when on: "Add sides" and "Add desserts" (both on by default).
  * One switch row: "Allow a little over budget", helper text "Shows a second plan up to 10% over". Off = only the within-budget plan is shown and the two cards collapse to one card (no radio).
- "Reset to suggested": a text button that appears only after the customer changed something.
- The old "Customize" row and the bottom sheet as a place for toggles go away. A sheet may remain only if you need one for Reset; if not needed, remove it and tell me.

D. SWAP PICKER (lower priority, build it last)
- In "What's included", every item row is tappable (chevron or a "Swap" text button at the right, 96 px target).
- Tapping opens a bottom sheet titled "Swap Adult 1's drink" (or "Swap Adult 1's meal", or "Swap shared meal for 2"). It lists alternatives in the SAME role in ONE list, sorted by price. A row: 88 px thumbnail, name (and "fries, cola" for a combo), and on the right the price change in meta style ("+Rp 2.000", "-Rp 1.000" or "Same price") above the NEW TOTAL ("Total Rp 151.600"). The current item is first with a "Current" tag and a check.
- For a meal swap the list mixes "Cheeseburger + Cola" style pairs with combos. For a shared 2-person meal it lists other 2-person combos and a "Two separate meals" option.
- Alternatives that would break the budget (or the over cap) are NOT listed.
- Footer: "Done". Sample total after a swap is only an illustration.

E. HONEST EDGE COPY (update the existing state boards)
- S_BudgetPas_moreThanEnough: title stays "More than enough". Replace the hero text with: "Our menu tops out at Rp X for your group, which is under your budget." The badge reads "Rp X under your budget" (X = budget minus plan total). Do not imply the budget was matched.
- S_BudgetPas_tooLow: unchanged ("Need Rp 16.600 more", "Closest complete plan").
- S_BudgetPas_exact: unchanged.

F. NEW STATE BOARD: PLAN DOES NOT FIT THE CART
- scenario `cartFull` (S_BudgetPas_cartFull): the customer tapped "Add plan to order" but the cart cannot take it. Show a message card above the dock: "Your order has too many items to add this plan". The dock button is "Review order" (accent, opens Your order). The plan stays visible behind.

G. AFTER ADDING
- The dock button turns into "Added to order" (check) for about 0.6 s, then the app opens Your order, which shows the "Plan added" toast (see the Your order prompt). Show the "Added to order" dock as a scenario `added`.

H. DO NOT CHANGE the planner rules or the numbers; they are final. The inactivity overlay and timing are unchanged.
```

## Prompt 4: Printer settings states (SettingsPrinter.dc.html)

```text
CHANGE 4 of 4: Printer settings. The real printer drivers are built later, so the screen must be fully designed with every state now. Today SettingsPrinter.dc.html shows ONE connected printer ("TP-80 - connected by USB - 80 mm") and has no state boards. Extend it to a printer LIST with roles and add state boards. Keep the existing sections as they are: status meter (paper roll, red below 15%), "Last printed", "Test page" (Printing... -> Printed), "Feed paper" (Feeding... -> Done), the toggles (Customer receipt, Kitchen ticket, Logo on receipt), the footer text input, paper width (58 / 80 mm) and density (Light / Normal / Dark).

NEW STRUCTURE
1. A "Printers" section at the top: one card per configured printer. Each card: an icon by connection type (Built-in, USB, Bluetooth), the name, the connection and paper width ("USB - 80 mm"), a status pill (Online green, Offline red, Out of paper red), and two role switches "Receipt" and "Kitchen". A role belongs to ONE printer only: turning a role on for a second printer turns it off on the first (show that in the `roles` scenario).
2. An "Add printer" button below the list (secondary style, 96 px).
3. The status meter, Test page, Feed and the receipt settings apply to the printer that is selected in the list (selected card has the accent ring).

SCENARIOS (add a `scenario` prop to SettingsPrinter.dc.html and one state board each, named S_SettingsPrinter_<scenario>)
- default: one connected USB printer with the Receipt role, meter at 72%.
- list: three printers (Built-in with Kitchen, USB 80 mm with Receipt, Bluetooth 58 mm without roles).
- roles: after moving the Receipt role from one printer to another (the previous card shows the role off).
- empty: no printers yet. Title "No printers yet", text "Add a printer to print receipts and kitchen tickets", an "Add a printer" button.
- add: a bottom sheet "Add a printer" with three groups: "Built-in" (found automatically), "USB" (lists attached devices; note "You will be asked to allow USB access"), "Bluetooth" (lists PAIRED printers only; note "Pair the printer in Android Bluetooth settings first"). A last row state "No printers found" (what a release build shows until the printer drivers arrive).
- offline: the selected printer is Offline (red pill), the meter is greyed with "Unknown", "Last error: Can't reach the printer", and a "Test page" button that is disabled with the reason.
- outOfPaper: red pill "Out of paper", meter at 0% in red, the text "Replace the paper roll, then print a test page".
- btPermission: the Bluetooth add sheet with a rationale card "Bluetooth access is needed to find your printers" and a "Grant" button (accent).

Keep this a staff screen: it uses the same visual language as the other Settings screens (PIN-gated later, not part of this change).
```

## After the design chat finishes

Sync the updated files into `docs/ui-design/`, then tell Claude what changed. Differences go back into the PRD, `docs/dev-plan.md` and `docs/testing-plan.md`, and DEV-104 is ticked after the PO signs the boards off.
