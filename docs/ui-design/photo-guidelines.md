# Photo and logo guidelines

How merchant photos and logos are shown, what a photo has to look like so nothing important is cropped, and what was done to the demo photos of the debug build. Read it before preparing real photos or implementing an image slot. The design files keep their placeholders (`Photo.dc.html`); this file records the rules the built app needs (NFR-013, D-30).

## 1. How the app draws a photo

Every photo goes through `ui/MenuPhoto` (a tone placeholder, then the image from Coil) and is drawn with **`ContentScale.Crop`**, the design's "cover". The slot decides the shape, so one photo is cut to many shapes. A photo that is fine in a square can lose its top and bottom in a wide slot, and a landscape photo loses its sides in a square one. A missing or failed image leaves the plain tone.

## 2. The slots

Sizes are design units (`dpx`, 1080 wide).

| Slot | Size (aspect) | Notes |
|---|---|---|
| Idle slide (SCR-04) | 1080 × 1920 (9:16), full screen | The white sheet covers the bottom 620 px, so the photo is seen in the top 1300 px. Ken Burns zoom 1.06 over 8 s, crossfade between slides |
| Item detail hero (SCR-08) | 1080 × 560 (1.93:1) | Starts at 1.08 zoom. The rounded sheet covers the bottom ~60 px. The widest slot |
| Menu card (SCR-06) | about 420 × 300 (1.4:1) | |
| Upsell card (SCR-09) | about 400 × 220 (1.8:1) | |
| Combo row (SCR-07) | 176 × 128 (1.375:1) | Side and drink options are circles of 96 |
| Merchant card (SCR-03) | 272 × 272 | The merchant logo fills it. Without a logo: the cover photo with a 72 px initial chip on top |
| Thumbnails | 160 (Your order), 128 (meal option), 88 (extras, dock), 80 (Budget Plan), 72 (Upsell strip) | Square or round |

## 3. What a photo needs

- **One 2:1 photo works for every slot except the idle slide.** Shape it as a 2:1 canvas (the demo images are 1600 × 800) with the subject centred, upright, and at most **82 % of the canvas height** (the demo originals have their own margin, so the subject itself ends up at 60 to 75 %; a tall bottle at 75 % clears the item hero's sheet by about 10 px at rest). Then a slot from square to 2:1 shows the whole subject and cuts only margin; the item hero at 1.08 zoom and under its sheet still fits.
- **Margins are continuous.** Fill them with the photo's own background (a flat studio colour or a blurred continuation), never a hard edge, a border or a different colour, so a narrow slot does not show a seam.
- **Idle slides are 9:16** (1080 × 1920), with the subject in the upper two thirds, because of the sheet, and at most **90 % of the width**, so the 1.06 Ken Burns zoom never pushes it off the edges.
- **Do not bake in text or a logo near the edges** (the app crops there). Packaging with a printed logo is fine in the middle.
- **Size and format:** 1600 px on the long side is enough (nothing is shown larger than 1080 px wide); WebP or JPEG at about 80 % quality, under 200 KB. The disk cache is 200 MB (A-37). Coil decodes at the slot size, so a larger file only costs download time.
- **Logo:** square, opaque (no transparency, the tile behind it is dark), at least 512 × 512, with the mark centred and a margin of about 10 %. It is shown as small as 72 px, so the shape has to read at that size.

## 4. What was done to the demo photos

The 29 images in `app/src/debug/assets/demo/` come from 24 square 1254 × 1254 originals (about 50 MB). Shown as they were, they were cropped hard: tall drinks and desserts lost their top and bottom in the 1.4:1 and 1.93:1 slots, and a square cropped to the 1080 × 1920 idle slot showed only its middle 56 %.

`scripts/demo-images/make_demo_images.py` rebuilds them (usage is in the file):

1. **Products (23 photos).** The original is shrunk to 82 % of a 1600 × 800 canvas. Above and below, the margin is the flat colour of the photo's own top and bottom edge. Left and right, the edge columns are stretched out, the result is blurred, and the sharp photo fades into it over 70 px (120 px top and bottom). The originals have a dark studio background, so the fill is invisible. Saved as WebP, quality 82, 20 to 120 KB each.
2. **Idle slides (3).** The original is scaled to 90 % of 1080 and placed 150 px from the top of a 1080 × 1920 canvas. The margin above and below is its flat edge colour, and left and right its stretched edge columns, blurred and faded in. The slides are made from the double burger, the six wings and the caramel sundae.
3. **"Make it a meal" photo (1).** The option adds fries and a drink, so its photo is the fries and the cola side by side: each original is shrunk to 70 % of a square, faded to black at its edges, and the two are merged by keeping the lighter pixel (the studio backgrounds are the same dark, so no rectangle shows). Then it is padded like a product photo. A product photo of the burger would be wrong here: the tile shows what the option adds, not the item.
3a. **"Feast for Two" photo (1).** The combo is 2 burgers, 2 sides and 2 drinks, so its photo is a tray seen straight on: the cheeseburger and the bacon BBQ burger on the top row, the fries and the cola under them. Each part is cut to its own box and faded into the flat studio colour of a 1600 × 800 canvas (the parts are not overlapped, so nothing ghosts), with the whole group at about 80 % of the height and 830 px wide, so even the 1.4:1 slots show all four.
4. **Logo (1).** Only resized to 512 × 512.
5. **Mapping.** `DemoImages` (`src/debug`) maps each product, combo, combo option and the "Make it a meal" option by id to one of these files, and the merchant (E2) and config (E5) to the logo and the hero. It replaces only a missing URL or the `cdn.example` placeholder, so a real backend URL is never replaced. Every sample product has a photo (r3 Ayam Geprek Bowl, r4 Black Pepper Beef Bowl and c4 Family Bucket were added on 2026-10-25); a failed image still shows the placeholder.
6. **Result.** Checked on the API 37 emulator at 1080 × 1920, against the fakes: Choose a restaurant, Idle, Order type, Menu (Popular, Drinks, Desserts), Item detail, Combo, Your order and the Checkout QR. No subject is cut in any of them. The steps are repeatable; run the script again after changing a source image or the canvas.

## 5. Where the merchant logo is used

The logo is the config `logoUrl` (E5) for everything after a restaurant is chosen, and the merchant's own `logoUrl` (E2) on its card, where it fills the whole picture box (PO, 2026-10-25): no cover photo and no small chip on top. A merchant without a logo keeps the design's cover photo with the initial chip. When there is none, or while it loads or if it fails, the design's ink tile with the accent dot stays (NFR-013).

| Place | Size | Built with |
|---|---|---|
| Choose a restaurant, merchant card | the whole 272 box | `MerchantCards` (E2 `logoUrl`) |
| Header of Order type, Menu and Checkout | 80 | `BrandLockup(logo = …)` |
| Idle, the merchant chip | 80 | `BrandMarkTile` |
| Order type, the large mark | 168 | `BrandMarkTile` |
| Checkout, in the middle of the QR | 96, in a white chip | `QrCenterMark(logo = …)` |

The Splash, Sign in and Choose a restaurant headers show the Hitachi Kiosk brand, not a merchant: no merchant is bound yet. The receipt logo ("Logo on receipt", FR-021) comes with printing in 0.6.0. The logo reaches the screens through `LocalMerchantLogoUrl`, set at the theme root from `BrandingViewModel` like the accent (A-38).
