# Hitachi Kiosk — Backend API Specification (v1, for mock server)

| | |
|---|---|
| Version | v1 draft (2026-10-23) — combo slot options carry an optional `available` for sold-out sides and drinks (E6, FR-011); merchants carry an optional `opensAt` for the closed badge (E1/E2, FR-004); state conflicts on E3, E8 and E9 have their own 409 codes; E6 is the full design menu, with required choices on b2 (size, FR-010), c4, r1 and d2; E8 cancel is idempotent: cancelling a `CANCELLED` order answers 200 (0.5.0, DEV-053) |
| Consumer | Hitachi Kiosk Android app |
| Requirements | [`../product-requirement.md`](../product-requirement.md) §10 (endpoints E1–E14) |
| Status | **Proposed.** Build the mock server from this document. The real backend implements the same contract (PRD D-06). |

The sample data is taken from the approved design (`../ui-design/`): merchant "Urban Grill", its menu, prices, combos and promo. A mock returning exactly these examples makes the app look like the design.

**Machine-readable version:** [`openapi.yaml`](openapi.yaml) (OpenAPI 3.0.3). It is generated from this file by [`tools/gen_openapi.py`](tools/gen_openapi.py), using every JSON example below. Edit this file first, then regenerate:
```bash
python3 docs/api/tools/gen_openapi.py docs/api/api-spec.md docs/api/openapi.yaml
```
Run it as a mock server with Prism:
```bash
npx @stoplight/prism-cli mock docs/api/openapi.yaml --port 4010
```
To choose an outcome, send a `Prefer` header, e.g. `Prefer: code=409, example=priceChanged` or `Prefer: example=paid` (mapping in §4).

---

## 1. Endpoint list

| # | Method | Path | Purpose | Auth | Used on screen |
|---|---|---|---|---|---|
| E1 | POST | `/v1/devices/activate` | Activate kiosk by serial number (SN) | none | Sign in |
| E2 | GET | `/v1/devices/{deviceId}/merchants` | Merchants this kiosk may serve | device | Choose a restaurant |
| E3 | PUT | `/v1/devices/{deviceId}/binding` | Bind kiosk to a merchant | device | Choose a restaurant |
| E4 | DELETE | `/v1/devices/{deviceId}/binding` | Unbind / log out | device | Choose a restaurant (Log out) |
| E5 | GET | `/v1/merchants/{merchantId}/config` | Merchant config: tax, promos, payment, PIN, branding | device | Splash, Idle, Your order, Settings |
| E6 | GET | `/v1/merchants/{merchantId}/menu` | Full menu: categories, products, modifiers, combos | device | Menu, Item, Combo |
| E7 | POST | `/v1/orders` | Create order (backend re-prices, assigns order number) | device | Your order → Pay |
| E8 | POST | `/v1/orders/{orderId}/cancel` | Abandon an unpaid order | device | Checkout (Back to order), timeout |
| E9 | POST | `/v1/orders/{orderId}/payments` | Start a QRIS or card payment | device | Checkout |
| E10 | GET | `/v1/orders/{orderId}/payments/{paymentId}` | Payment status (polled every 2 s) | device | Checkout |
| E11 | POST | `/v1/orders/{orderId}/payments/{paymentId}/cancel` | Cancel a pending payment | device | Checkout |
| E12 | POST | `/v1/orders/{orderId}/payments/{paymentId}/terminal-result` | Relay EDC terminal result (only if the terminal reports to the kiosk) | device | Checkout (card) |
| E13 | PUT | `/v1/devices/{deviceId}/staff-pin` | Change staff PIN | device | Settings › Device & network |
| E14 | GET | `/v1/health` | Backend + gateway reachability | none | Settings › Connection test, offline detection |

### 1.1 Call sequence (happy path)
```
First run:      E1 activate → E2 merchants → E3 bind → E5 config → E6 menu → Idle
Every start:    E5 config (If-None-Match) → E6 menu (If-None-Match)
Every 15 min:   E5 + E6 with ETag (304 when unchanged)
Order:          E7 create order → E9 start payment → E10 poll … → PAID → Complete
Cancel/back:    E11 cancel payment → E8 cancel order
Offline check:  E14 health (on network change / every 30 s while offline)
```

---

## 2. Conventions

### 2.1 Base URL
| Env | Base URL (example) |
|---|---|
| mock | `http://10.0.2.2:4010` (emulator → host machine) |
| dev | `https://dev-api.hitachikiosk.example/` |
| staging | `https://staging-api.hitachikiosk.example/` |
| prod | `https://api.hitachikiosk.example/` |

> The mock may use plain HTTP. The app allows cleartext **only** in the `debug` build for the `mock` host.

### 2.2 Headers
| Header | When | Example |
|---|---|---|
| `Content-Type` | Requests with a body | `application/json; charset=utf-8` |
| `Authorization` | All calls except E1 and E14 | `Bearer dvt_9f2c…` |
| `Idempotency-Key` | E7, E9 (required); E3, E8, E11, E13 (optional) | `5b1c0c7e-2f1a-4c55-9d0e-1c2a7f0e9b11` |
| `If-None-Match` | E5, E6 | `"cfg-42"` |
| `X-App-Version` | All | `0.3.0` |
| `X-Device-Id` | All after activation | `dev_0003` |
| `Accept-Language` | All | `en` or `id`. The 1.0 app always sends `en` (English-only UI, D-29). `id` stays valid for later. |

Response headers the app reads: `ETag` (E5/E6) and `Date`, which the app uses to compute the server clock offset for QR expiry.

### 2.3 Data rules
- **Money:** integer Rupiah (IDR). No decimals. `52000` means Rp 52.000.
- **Time:** ISO-8601 UTC, e.g. `2026-10-22T07:15:30Z`.
- **IDs:** opaque strings. The app never parses them.
- **Localized text:** an object `{ "en": "…", "id": "…" }`. The contract can carry both languages. The 1.0 app is English only (D-29): it shows `en` and falls back to `id` only when `en` is missing. `id` values are ignored otherwise, and the backend may omit them.
- **Colors:** `#RRGGBB`.
- **Image URLs:** absolute HTTPS URLs, or `null` (the app shows a tone placeholder).

### 2.4 Error format
Every non-2xx response has this body:
```json
{
  "code": "PRICE_CHANGED",
  "message": "Prices have changed since the menu was loaded.",
  "details": { }
}
```

| HTTP | `code` | Meaning | App behaviour |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Bad request body | Log and show a generic error |
| 401 | `TOKEN_INVALID` | Device token revoked or expired | Clear activation and go to Sign in |
| 403 | `DEVICE_DISABLED` | Kiosk disabled in dashboard | "This kiosk has been disabled…" |
| 404 | `DEVICE_NOT_REGISTERED` | SN not in dashboard (E1 only) | Show the SN and "Try again" |
| 404 | `NOT_FOUND` | Unknown merchant/order/payment | Generic error |
| 409 | `PRICE_CHANGED` | Kiosk total ≠ backend total (E7) | Re-sync and re-price, back to Your order |
| 409 | `ITEM_UNAVAILABLE` | One or more items sold out (E7) | Mark the lines and block Pay |
| 409 | `MERCHANT_NOT_LIVE` | Merchant is `coming_soon` and cannot be bound (E3) | Stay on Choose a restaurant with Retry |
| 409 | `MERCHANT_CLOSED` | Merchant not accepting orders (E7) | "The restaurant isn't taking orders right now." |
| 409 | `ORDER_ALREADY_PAID` | Order is already `PAID`, so it cannot be cancelled (E8) | Go to Complete |
| 409 | `ORDER_NOT_AWAITING_PAYMENT` | Order is not `AWAITING_PAYMENT`, so no payment can start (E9) | Re-read the payment with E10 if one exists, otherwise back to Your order |
| 409 | `PAYMENT_NOT_CANCELLABLE` | Payment already paid/final (E11) | Re-read the status with E10 |
| 409 | `PIN_INCORRECT` | Wrong current PIN (E13) | Inline error |
| 422 | `PAYMENT_METHOD_UNAVAILABLE` | Method disabled for merchant (E9) | Hide the method and show the failure |
| 429 | `RATE_LIMITED` | Too many requests | Back off |
| 500/502/503 | `SERVER_ERROR` / `GATEWAY_ERROR` | Server or gateway problem | Retry idempotent calls (1 s/2 s/4 s), then show an error |

---

## 3. Endpoints

### E1 — Activate device
`POST /v1/devices/activate` · no auth

**Request**
```json
{
  "serialNumber": "IMN24K0003118",
  "model": "iMin Swan 2",
  "osVersion": "14",
  "appVersion": "0.2.0"
}
```

**Response 200**
```json
{
  "deviceId": "dev_0003",
  "deviceToken": "dvt_9f2c4a7e1b8d4f0aa3c6e2b17d5f9e01",
  "tokenExpiresAt": null,
  "kioskCode": "KSK-0003",
  "kioskName": "Kiosk 3",
  "boundMerchantId": null,
  "merchants": [
    {
      "id": "mer_urbangrill",
      "name": "Urban Grill",
      "cuisine": { "en": "Burgers & grill", "id": "Burger & panggangan" },
      "logoUrl": "https://cdn.example/mer_urbangrill/logo.png",
      "photoUrl": "https://cdn.example/mer_urbangrill/cover.jpg",
      "status": "live",
      "isOpen": true,
      "opensAt": null
    }
  ]
}
```
- `tokenExpiresAt: null` means the token does not expire. It is revoked from the dashboard instead, which makes calls return 401.
- `boundMerchantId` is non-null if the dashboard already pre-assigns a merchant. The app then skips Choose a restaurant.

**Errors:** `404 DEVICE_NOT_REGISTERED`, `403 DEVICE_DISABLED`, `400 VALIDATION_ERROR`.
```json
{ "code": "DEVICE_NOT_REGISTERED", "message": "Serial number is not registered.", "details": { "serialNumber": "IMN24K0003118" } }
```

---

### E2 — List merchants
`GET /v1/devices/{deviceId}/merchants`

**Response 200**
```json
{
  "merchants": [
    {
      "id": "mer_urbangrill",
      "name": "Urban Grill",
      "cuisine": { "en": "Burgers & grill", "id": "Burger & panggangan" },
      "logoUrl": "https://cdn.example/mer_urbangrill/logo.png",
      "photoUrl": "https://cdn.example/mer_urbangrill/cover.jpg",
      "status": "live",
      "isOpen": true,
      "opensAt": null
    },
    { "id": "mer_bellapasta", "name": "Bella Pasta",  "cuisine": { "en": "Italian kitchen",        "id": "Dapur Italia" },        "logoUrl": null, "photoUrl": null, "status": "coming_soon", "isOpen": false },
    { "id": "mer_sakura",     "name": "Sakura Ramen", "cuisine": { "en": "Japanese noodles",       "id": "Mi Jepang" },           "logoUrl": null, "photoUrl": null, "status": "coming_soon", "isOpen": false },
    { "id": "mer_tacoloco",   "name": "Taco Loco",    "cuisine": { "en": "Mexican street food",    "id": "Jajanan Meksiko" },     "logoUrl": null, "photoUrl": null, "status": "coming_soon", "isOpen": false },
    { "id": "mer_freshbowl",  "name": "Fresh Bowl",   "cuisine": { "en": "Salads & healthy bowls", "id": "Salad & mangkuk sehat" }, "logoUrl": null, "photoUrl": null, "status": "coming_soon", "isOpen": false }
  ]
}
```
`status`: `live` | `coming_soon`. Only `live` merchants can be bound.

`isOpen` is whether the merchant takes orders right now. `opensAt` (optional, ISO-8601 UTC) is when a closed `live` merchant opens next; it is `null` or absent while the merchant is open, for `coming_soon` merchants, and when the backend doesn't know. The kiosk shows a closed merchant as "Closed · opens {time}" in device-local time, or "Closed" when `opensAt` is missing (FR-004). E1 carries the same merchant object.

---

### E3 — Bind kiosk to merchant
`PUT /v1/devices/{deviceId}/binding`

**Request**
```json
{ "merchantId": "mer_urbangrill" }
```

**Response 200**
```json
{
  "deviceId": "dev_0003",
  "merchantId": "mer_urbangrill",
  "kioskName": "Kiosk 3",
  "boundAt": "2026-10-22T07:00:00Z"
}
```
**Errors:** `404 NOT_FOUND`, `409 MERCHANT_NOT_LIVE` (merchant is `coming_soon`).

---

### E4 — Unbind / log out
`DELETE /v1/devices/{deviceId}/binding`

**Response 204** (no body). The device token stays valid, so the app goes back to Choose a restaurant. To fully sign out, the app also deletes its local token.

---

### E5 — Merchant config
`GET /v1/merchants/{merchantId}/config` · supports `If-None-Match` → `304 Not Modified`

**Response 200** (header `ETag: "cfg-42"`)
```json
{
  "configVersion": "cfg-42",
  "merchantId": "mer_urbangrill",
  "mode": "SINGLE",
  "brandName": "Urban Grill",
  "accentColor": "#C8552B",
  "logoUrl": "https://cdn.example/mer_urbangrill/logo.png",
  "heroPhotoUrl": "https://cdn.example/mer_urbangrill/hero.jpg",
  "isOpen": true,
  "currency": "IDR",
  "tax": {
    "label": { "en": "Restaurant tax (PB1 10%)", "id": "Pajak restoran (PB1 10%)" },
    "rateBasisPoints": 1000,
    "inclusive": false
  },
  "rounding": { "allowedSteps": [0, 100, 500], "default": 0, "mode": "UP" },
  "promos": [
    {
      "id": "promo_10k_over_100k",
      "type": "THRESHOLD_FIXED",
      "threshold": 100000,
      "amount": 10000,
      "label": { "en": "Rp 10.000 off over Rp 100.000", "id": "Potongan Rp 10.000 untuk belanja di atas Rp 100.000" }
    }
  ],
  "orderTypes": ["DINE_IN", "TAKEAWAY"],
  "paymentMethods": [
    { "method": "QRIS", "enabled": true, "qrisMerchantIdMasked": "ID10 •••• 4721", "qrExpirySecondsAllowed": [180, 300, 600], "qrExpirySecondsDefault": 300 },
    { "method": "CARD", "enabled": true, "terminal": { "tid": "TID 0003 1182", "connection": "USB" } }
  ],
  "staffPin": {
    "algorithm": "PBKDF2WithHmacSHA256",
    "iterations": 120000,
    "salt": "b64:3q2+7w==",
    "hash": "b64:8Jw3k1m0…",
    "version": 3
  },
  "receipt": {
    "address": "Jl. Sudirman No. 1, Jakarta",
    "npwp": null,
    "defaultFooter": { "en": "Thank you for dining with us!", "id": "Terima kasih telah makan bersama kami!" }
  },
  "upsell": { "categoryIds": ["cat_sides", "cat_drinks", "cat_desserts"], "maxSuggestions": 4 },
  "budgetPlanner": { "enabled": true, "overCapPercent": 10, "maxGroupSize": 10, "quickBudgets": [50000, 100000, 150000, 200000] },
  "idlePromos": [
    { "imageUrl": "https://cdn.example/mer_urbangrill/idle1.jpg" },
    { "imageUrl": "https://cdn.example/mer_urbangrill/idle2.jpg" },
    { "imageUrl": "https://cdn.example/mer_urbangrill/idle3.jpg" }
  ]
}
```
Notes:
- `rateBasisPoints`: 1000 = 10.00 %. The app computes tax = round_half_up((subtotal − discount) × rate / 10000).
- `staffPin` must never contain the plain PIN. For the mock, use PIN `1234`. Generate the hash with the given algorithm and parameters, or use `"algorithm": "PLAIN_MOCK_ONLY", "hash": "1234"`, which the app accepts **only in debug builds**.

---

### E6 — Menu
`GET /v1/merchants/{merchantId}/menu` · supports `If-None-Match` → `304`

**Response 200** (header `ETag: "menu-118"`). This is the full design menu: 23 products in 6 categories, 4 combos and their slot options (see §4).
```json
{
  "menuVersion": "menu-118",
  "categories": [
    { "id": "cat_burgers",  "name": { "en": "Burgers",    "id": "Burger" },      "sort": 1, "tone": "#E9DED0" },
    { "id": "cat_chicken",  "name": { "en": "Chicken",    "id": "Ayam" },        "sort": 2, "tone": "#E3D4C2" },
    { "id": "cat_bowls",    "name": { "en": "Rice bowls", "id": "Nasi mangkuk" }, "sort": 3, "tone": "#EADCCB" },
    { "id": "cat_sides",    "name": { "en": "Sides",      "id": "Pendamping" },  "sort": 5, "tone": "#E6DCD3" },
    { "id": "cat_drinks",   "name": { "en": "Drinks",     "id": "Minuman" },     "sort": 6, "tone": "#DFE3DC" },
    { "id": "cat_desserts", "name": { "en": "Desserts",   "id": "Pencuci mulut" }, "sort": 7, "tone": "#EADCCB" }
  ],
  "combosCategory": { "name": { "en": "Meals", "id": "Paket" }, "sort": 4 },
  "featuredProductIds": ["b2", "c1", "r1", "d1"],
  "promoBanners": [
    { "id": "pb1", "tag": { "en": "New", "id": "Baru" },             "title": { "en": "Smoky Double", "id": "Smoky Double" }, "priceText": { "en": "Rp 52.000", "id": "Rp 52.000" },                 "imageUrl": null, "target": { "type": "PRODUCT", "id": "b2" } },
    { "id": "pb2", "tag": { "en": "Meal deal", "id": "Paket hemat" }, "title": { "en": "Burger Meal", "id": "Paket Burger" },   "priceText": { "en": "Only Rp 56.000", "id": "Hanya Rp 56.000" },      "imageUrl": null, "target": { "type": "COMBO", "id": "combo_burger" } },
    { "id": "pb3", "tag": { "en": "Share it", "id": "Untuk berbagi" }, "title": { "en": "Family Bucket", "id": "Family Bucket" }, "priceText": { "en": "8 pc · Rp 129.000", "id": "8 pcs · Rp 129.000" }, "imageUrl": null, "target": { "type": "PRODUCT", "id": "c4" } }
  ],
  "products": [
    { "id": "b1", "categoryId": "cat_burgers", "name": { "en": "Classic Cheeseburger", "id": "Cheeseburger Klasik" }, "description": null, "price": 38000, "kcal": 540, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    {
      "id": "b2",
      "categoryId": "cat_burgers",
      "name": { "en": "Smoky Double Burger", "id": "Smoky Double Burger" },
      "description": { "en": "Two flame-grilled beef patties, smoked cheddar, pickles, onion and smoky house sauce on a toasted sesame bun.", "id": "Dua patty sapi panggang, keju cheddar asap, acar, bawang, dan saus asap khas di roti wijen panggang." },
      "price": 52000,
      "kcal": 780,
      "badge": { "type": "NEW", "label": { "en": "New", "id": "Baru" } },
      "tags": { "en": "Beef · Contains gluten, dairy", "id": "Sapi · Mengandung gluten, susu" },
      "imageUrl": null,
      "available": true,
      "modifierGroupIds": ["mg_b2_size", "mg_meal_upgrade", "mg_b2_ingredients", "mg_burger_extras"],
      "role": "MAIN",
      "kidFriendly": false,
      "servesAdults": 1,
      "shareable": false,
      "plannerWeight": 5
    },
    { "id": "b3", "categoryId": "cat_burgers", "name": { "en": "Hot Honey Chicken Burger", "id": "Burger Ayam Madu Pedas" }, "description": null, "price": 42000, "kcal": 620, "badge": { "type": "SPICY", "label": { "en": "Spicy", "id": "Pedas" } }, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "b4", "categoryId": "cat_burgers", "name": { "en": "BBQ Beef Bacon Burger", "id": "Burger Beef Bacon BBQ" }, "description": null, "price": 45000, "kcal": 710, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "c1", "categoryId": "cat_chicken", "name": { "en": "Crispy Chicken (2 pc)", "id": "Ayam Krispi (2 pcs)" }, "description": { "en": "Original recipe", "id": "Resep original" }, "price": 38000, "kcal": 560, "badge": { "type": "BEST_SELLER", "label": { "en": "Best seller", "id": "Terlaris" } }, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "c2", "categoryId": "cat_chicken", "name": { "en": "Spicy Wings (6 pc)", "id": "Sayap Pedas (6 pcs)" }, "description": null, "price": 36000, "kcal": 480, "badge": { "type": "SPICY", "label": { "en": "Spicy", "id": "Pedas" } }, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "c3", "categoryId": "cat_chicken", "name": { "en": "Chicken Nuggets (9 pc)", "id": "Nugget Ayam (9 pcs)" }, "description": null, "price": 32000, "kcal": 430, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "c4", "categoryId": "cat_chicken", "name": { "en": "Family Bucket (8 pc)", "id": "Family Bucket (8 pcs)" }, "description": null, "price": 129000, "kcal": 2240, "badge": { "type": "SHARE", "label": { "en": "Share", "id": "Untuk berbagi" } }, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": ["mg_bucket_flavour"], "role": "MAIN", "kidFriendly": false, "servesAdults": 4, "shareable": true, "plannerWeight": 0 },
    { "id": "r1", "categoryId": "cat_bowls", "name": { "en": "Sambal Matah Rice Bowl", "id": "Nasi Mangkuk Sambal Matah" }, "description": null, "price": 34000, "kcal": 610, "badge": { "type": "LOCAL", "label": { "en": "Local", "id": "Lokal" } }, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": ["mg_spice_level"], "role": "MAIN", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "r2", "categoryId": "cat_bowls", "name": { "en": "Teriyaki Chicken Bowl", "id": "Nasi Mangkuk Ayam Teriyaki" }, "description": null, "price": 32000, "kcal": 590, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "r3", "categoryId": "cat_bowls", "name": { "en": "Ayam Geprek Bowl", "id": "Nasi Mangkuk Ayam Geprek" }, "description": null, "price": 30000, "kcal": 640, "badge": { "type": "SPICY", "label": { "en": "Spicy", "id": "Pedas" } }, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "r4", "categoryId": "cat_bowls", "name": { "en": "Black Pepper Beef Bowl", "id": "Nasi Mangkuk Sapi Lada Hitam" }, "description": null, "price": 39000, "kcal": 660, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "MAIN", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "s1", "categoryId": "cat_sides", "name": { "en": "French Fries", "id": "Kentang Goreng" }, "description": { "en": "Sea salt", "id": "Garam laut" }, "price": 18000, "kcal": 320, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": ["mg_fries_size"], "role": "SIDE", "kidFriendly": true, "servesAdults": 1, "shareable": true, "plannerWeight": 0 },
    { "id": "s2", "categoryId": "cat_sides", "name": { "en": "Cheese Fries", "id": "Kentang Keju" }, "description": { "en": "Melted cheddar", "id": "Cheddar leleh" }, "price": 26000, "kcal": 450, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "SIDE", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "s3", "categoryId": "cat_sides", "name": { "en": "Chicken Nuggets (4 pc)", "id": "Nugget Ayam (4 pcs)" }, "description": null, "price": 16000, "kcal": 190, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "SIDE", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "s4", "categoryId": "cat_sides", "name": { "en": "Spicy Wings (3 pc)", "id": "Sayap Pedas (3 pcs)" }, "description": null, "price": 19000, "kcal": 240, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "SIDE", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "d1", "categoryId": "cat_drinks", "name": { "en": "Iced Lychee Tea", "id": "Es Teh Leci" }, "description": null, "price": 16000, "kcal": 140, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": ["mg_ice"], "role": "DRINK", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "d2", "categoryId": "cat_drinks", "name": { "en": "Cola (Regular)", "id": "Cola (Reguler)" }, "description": null, "price": 14000, "kcal": 150, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": ["mg_cola_type"], "role": "DRINK", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "d3", "categoryId": "cat_drinks", "name": { "en": "Iced Coffee Latte", "id": "Es Kopi Latte" }, "description": null, "price": 18000, "kcal": 180, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "DRINK", "kidFriendly": false, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "d4", "categoryId": "cat_drinks", "name": { "en": "Mineral Water 600 ml", "id": "Air Mineral 600 ml" }, "description": null, "price": 8000, "kcal": 0, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "DRINK", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "e1", "categoryId": "cat_desserts", "name": { "en": "Caramel Sundae", "id": "Sundae Karamel" }, "description": { "en": "Soft serve", "id": "Es krim lembut" }, "price": 15000, "kcal": 330, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "DESSERT", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "e2", "categoryId": "cat_desserts", "name": { "en": "Chocolate Sundae", "id": "Sundae Cokelat" }, "description": null, "price": 15000, "kcal": 350, "badge": null, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "DESSERT", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 },
    { "id": "e3", "categoryId": "cat_desserts", "name": { "en": "Pandan Soft Serve", "id": "Es Krim Pandan" }, "description": { "en": "Local favourite", "id": "Favorit lokal" }, "price": 12000, "kcal": 210, "badge": { "type": "LOCAL", "label": { "en": "Local", "id": "Lokal" } }, "tags": null, "imageUrl": null, "available": true, "modifierGroupIds": [], "role": "DESSERT", "kidFriendly": true, "servesAdults": 1, "shareable": false, "plannerWeight": 0 }
  ],
  "modifierGroups": [
    {
      "id": "mg_b2_size",
      "name": { "en": "Choose a size", "id": "Pilih ukuran" },
      "type": "SINGLE",
      "required": true,
      "min": 1, "max": 1,
      "options": [
        { "id": "opt_b2_regular", "name": { "en": "Regular", "id": "Reguler" }, "description": { "en": "Two patties", "id": "Dua patty" }, "priceDelta": 0, "default": false, "imageUrl": null },
        { "id": "opt_b2_large", "name": { "en": "Large", "id": "Besar" }, "description": { "en": "Three patties", "id": "Tiga patty" }, "priceDelta": 6000, "default": false, "imageUrl": null }
      ]
    },
    {
      "id": "mg_meal_upgrade",
      "name": { "en": "Make it a meal?", "id": "Jadikan paket?" },
      "purpose": "MEAL_UPGRADE",
      "type": "SINGLE",
      "required": true,
      "min": 1, "max": 1,
      "options": [
        { "id": "opt_burger_only", "name": { "en": "Burger only", "id": "Burger saja" }, "description": { "en": "Just the burger", "id": "Hanya burger" }, "priceDelta": 0, "default": true, "imageUrl": null },
        { "id": "opt_make_meal", "name": { "en": "Make it a meal", "id": "Jadikan paket" }, "description": { "en": "Adds fries & a drink", "id": "Tambah kentang & minuman" }, "priceDelta": 18000, "default": false, "imageUrl": null }
      ]
    },
    {
      "id": "mg_b2_ingredients",
      "name": { "en": "Customize", "id": "Sesuaikan" },
      "type": "LEVEL",
      "required": false,
      "options": [
        { "id": "ing_cheddar", "name": { "en": "Smoked cheddar", "id": "Cheddar asap" }, "note": { "en": "Extra +Rp 5.000", "id": "Ekstra +Rp 5.000" }, "levels": [ { "level": "NONE", "priceDelta": 0 }, { "level": "REGULAR", "priceDelta": 0 }, { "level": "EXTRA", "priceDelta": 5000 } ], "defaultLevel": "REGULAR" },
        { "id": "ing_onion",   "name": { "en": "Onion", "id": "Bawang" },            "note": { "en": "Fresh, diced", "id": "Segar, dipotong dadu" }, "levels": [ { "level": "NONE", "priceDelta": 0 }, { "level": "REGULAR", "priceDelta": 0 }, { "level": "EXTRA", "priceDelta": 0 } ], "defaultLevel": "REGULAR" },
        { "id": "ing_pickles", "name": { "en": "Pickles", "id": "Acar" },            "note": { "en": "Dill", "id": "Dill" },                         "levels": [ { "level": "NONE", "priceDelta": 0 }, { "level": "REGULAR", "priceDelta": 0 }, { "level": "EXTRA", "priceDelta": 0 } ], "defaultLevel": "REGULAR" },
        { "id": "ing_sauce",   "name": { "en": "Smoky sauce", "id": "Saus asap" },   "note": { "en": "House recipe", "id": "Resep khas" },           "levels": [ { "level": "NONE", "priceDelta": 0 }, { "level": "REGULAR", "priceDelta": 0 }, { "level": "EXTRA", "priceDelta": 0 } ], "defaultLevel": "REGULAR" }
      ]
    },
    {
      "id": "mg_burger_extras",
      "name": { "en": "Add extras", "id": "Tambahan" },
      "type": "MULTI",
      "required": false,
      "min": 0, "max": 3,
      "options": [
        { "id": "ext_bacon", "name": { "en": "Beef bacon", "id": "Beef bacon" }, "priceDelta": 8000, "default": false, "imageUrl": null },
        { "id": "ext_egg",   "name": { "en": "Fried egg", "id": "Telur goreng" }, "priceDelta": 6000, "default": false, "imageUrl": null },
        { "id": "ext_jala",  "name": { "en": "Jalapeños", "id": "Jalapeño" },     "priceDelta": 4000, "default": false, "imageUrl": null }
      ]
    },
    {
      "id": "mg_fries_size", "name": { "en": "Size", "id": "Ukuran" }, "type": "SINGLE", "required": true, "min": 1, "max": 1,
      "options": [
        { "id": "opt_regular", "name": { "en": "Regular", "id": "Reguler" }, "priceDelta": 0, "default": true },
        { "id": "opt_large",   "name": { "en": "Large", "id": "Besar" },     "priceDelta": 4000, "default": false }
      ]
    },
    {
      "id": "mg_ice", "name": { "en": "Ice", "id": "Es" }, "type": "SINGLE", "required": true, "min": 1, "max": 1,
      "options": [
        { "id": "opt_ice_regular", "name": { "en": "Regular ice", "id": "Es normal" }, "priceDelta": 0, "default": true },
        { "id": "opt_ice_less",    "name": { "en": "Less ice", "id": "Sedikit es" },   "priceDelta": 0, "default": false }
      ]
    },
    {
      "id": "mg_bucket_flavour", "name": { "en": "Choose a flavour", "id": "Pilih rasa" }, "type": "SINGLE", "required": true, "min": 1, "max": 1,
      "options": [
        { "id": "opt_flavour_original", "name": { "en": "Original", "id": "Original" }, "priceDelta": 0, "default": false },
        { "id": "opt_flavour_spicy", "name": { "en": "Spicy", "id": "Pedas" }, "priceDelta": 0, "default": false },
        { "id": "opt_flavour_half", "name": { "en": "Half & half", "id": "Setengah-setengah" }, "priceDelta": 0, "default": false }
      ]
    },
    {
      "id": "mg_spice_level", "name": { "en": "Spice level", "id": "Tingkat pedas" }, "type": "SINGLE", "required": true, "min": 1, "max": 1,
      "options": [
        { "id": "opt_spice_mild", "name": { "en": "Mild", "id": "Tidak pedas" }, "priceDelta": 0, "default": false },
        { "id": "opt_spice_medium", "name": { "en": "Medium", "id": "Sedang" }, "priceDelta": 0, "default": false },
        { "id": "opt_spice_hot", "name": { "en": "Hot", "id": "Pedas" }, "priceDelta": 0, "default": false }
      ]
    },
    {
      "id": "mg_cola_type", "name": { "en": "Choose a cola", "id": "Pilih cola" }, "type": "SINGLE", "required": true, "min": 1, "max": 1,
      "options": [
        { "id": "opt_cola_classic", "name": { "en": "Classic", "id": "Klasik" }, "priceDelta": 0, "default": false },
        { "id": "opt_cola_zero", "name": { "en": "Zero sugar", "id": "Tanpa gula" }, "priceDelta": 0, "default": false }
      ]
    }
  ],
  "combos": [
    {
      "id": "combo_burger",
      "name": { "en": "Cheeseburger Meal", "id": "Paket Cheeseburger" },
      "shortName": { "en": "Cheeseburger Meal", "id": "Paket Cheeseburger" },
      "includes": { "en": "Cheeseburger, side, drink", "id": "Cheeseburger, pendamping, minuman" },
      "price": 56000,
      "separatePrice": 70000,
      "serves": 1,
      "imageUrl": null,
      "tone": "#E9DED0",
      "available": true
    },
    { "id": "combo_chicken", "name": { "en": "Crispy Chicken Meal", "id": "Paket Ayam Krispi" }, "shortName": { "en": "Chicken Meal", "id": "Paket Ayam" }, "includes": { "en": "2 pc chicken, side, drink", "id": "2 pcs ayam, pendamping, minuman" }, "price": 59000, "separatePrice": 70000, "serves": 1, "imageUrl": null, "tone": "#E3D4C2", "available": true },
    { "id": "combo_bowl", "name": { "en": "Rice Bowl Combo", "id": "Paket Nasi Mangkuk" }, "shortName": { "en": "Rice Bowl Combo", "id": "Paket Nasi Mangkuk" }, "includes": { "en": "Sambal matah bowl, side, drink", "id": "Nasi sambal matah, pendamping, minuman" }, "price": 52000, "separatePrice": 64000, "serves": 1, "imageUrl": null, "tone": "#EADCCB", "available": true },
    { "id": "combo_feast", "name": { "en": "Feast for Two", "id": "Paket Berdua" }, "shortName": { "en": "Feast for Two", "id": "Paket Berdua" }, "includes": { "en": "2 burgers, 2 sides, 2 drinks", "id": "2 burger, 2 pendamping, 2 minuman" }, "price": 109000, "separatePrice": 140000, "serves": 2, "imageUrl": null, "tone": "#E6DCD3", "available": true }
  ],
  "comboSlots": {
    "SIDE": [
      { "id": "cs_fries",   "name": { "en": "Fries", "id": "Kentang goreng" },      "upcharge": 0,    "imageUrl": null },
      { "id": "cs_cheese",  "name": { "en": "Cheese fries", "id": "Kentang keju" }, "upcharge": 8000, "imageUrl": null },
      { "id": "cs_nuggets", "name": { "en": "Nuggets", "id": "Nugget" },            "upcharge": 4000, "imageUrl": null },
      { "id": "cs_corn",    "name": { "en": "Corn cup", "id": "Jagung cup" },       "upcharge": 0,    "imageUrl": null }
    ],
    "DRINK": [
      { "id": "cd_cola",   "name": { "en": "Cola", "id": "Cola" },             "upcharge": 0,    "imageUrl": null },
      { "id": "cd_tea",    "name": { "en": "Lychee tea", "id": "Teh leci" },   "upcharge": 2000, "imageUrl": null },
      { "id": "cd_coffee", "name": { "en": "Iced latte", "id": "Es latte" },   "upcharge": 4000, "imageUrl": null },
      { "id": "cd_water",  "name": { "en": "Water", "id": "Air mineral" },     "upcharge": 0,    "imageUrl": null }
    ],
    "SIZE_LARGE_UPCHARGE": 6000
  }
}
```
Rules the mock must respect:
- `badge.type`: `NEW` | `BEST_SELLER` | `SPICY` | `SHARE` | `LOCAL`. The app colors NEW and BEST_SELLER with the accent and the others in ink.
- `ModifierGroup.type`: `SINGLE` (radio) | `MULTI` (checkbox list) | `LEVEL` (None / Regular / Extra per option).
- `ModifierGroup.purpose` (optional, FR-014, OQ-17): `MEAL_UPGRADE` marks the group that is "Make it a meal": a `SINGLE` group whose non-default option turns the product into a meal. Absent or any other value means a plain group, so older payloads still parse. The app uses it for the meal-aware Upsell gap check and the "Make it a meal" card on Your order (DEV-114); without it only combo lines count as a meal (A-48).
- Combo price = `price + (side.upcharge + drink.upcharge + (large ? SIZE_LARGE_UPCHARGE : 0)) × serves`.
- `available: false` → the item is hidden from the menu (PRD A-09).
- `comboSlots` options may carry `available` (optional, default `true`). An option with `available: false` stays listed on the Combo screen, greyed and marked "Sold out", and cannot be picked (FR-011, `S_Combo_soldOut`).
- `promoBanners`: the final design has no promo carousel on Menu, so the 1.0 app does not render them. The field stays in the contract as optional and the app ignores it.
- Budget Plan fields (PRD FR-032), required on every product:
  - `role`: `MAIN` | `SIDE` | `DRINK` | `DESSERT`.
  - `kidFriendly`: whether the item may be planned for a child.
  - `servesAdults`: portions; items with > 1 are not planned as mains in 1.0.
  - `shareable`: whether the item is meant for sharing.
  - `plannerWeight`: optional merchant preference, higher wins ties.

---

### E7 — Create order
`POST /v1/orders` · **`Idempotency-Key` required** (same key + same body → same response, never a second order)

**Request**
```json
{
  "merchantId": "mer_urbangrill",
  "orderType": "DINE_IN",
  "language": "en",
  "menuVersion": "menu-118",
  "configVersion": "cfg-42",
  "roundingStep": 0,
  "lines": [
    {
      "lineId": "l1",
      "kind": "PRODUCT",
      "productId": "b2",
      "quantity": 1,
      "selections": [
        { "groupId": "mg_b2_size", "optionId": "opt_b2_regular" },
        { "groupId": "mg_meal_upgrade", "optionId": "opt_make_meal" },
        { "groupId": "mg_b2_ingredients", "optionId": "ing_cheddar", "level": "EXTRA" },
        { "groupId": "mg_b2_ingredients", "optionId": "ing_onion", "level": "NONE" }
      ],
      "unitPrice": 75000,
      "lineTotal": 75000
    },
    { "lineId": "l2", "kind": "PRODUCT", "productId": "c1", "quantity": 1, "selections": [], "unitPrice": 38000, "lineTotal": 38000 },
    { "lineId": "l3", "kind": "PRODUCT", "productId": "s1", "quantity": 1, "selections": [ { "groupId": "mg_fries_size", "optionId": "opt_large" } ], "unitPrice": 22000, "lineTotal": 22000 },
    { "lineId": "l4", "kind": "PRODUCT", "productId": "d1", "quantity": 2, "selections": [ { "groupId": "mg_ice", "optionId": "opt_ice_less" } ], "unitPrice": 16000, "lineTotal": 32000 }
  ],
  "clientTotals": {
    "subtotal": 167000,
    "discount": 10000,
    "promoId": "promo_10k_over_100k",
    "tax": 15700,
    "rounding": 0,
    "total": 172700
  }
}
```
A combo line looks like this:
```json
{ "lineId": "l5", "kind": "COMBO", "comboId": "combo_burger", "quantity": 1,
  "comboSelections": { "sideId": "cs_cheese", "drinkId": "cd_coffee", "size": "LARGE" },
  "unitPrice": 74000, "lineTotal": 74000 }
```
Every SINGLE and MULTI choice is sent. Of the LEVEL options, only the changed ones are sent (here `cheddar: EXTRA` and `onion: NONE`); unsent ones use `defaultLevel`.

**Response 201**
```json
{
  "orderId": "ord_7Hq2k9",
  "orderNumber": "A247",
  "status": "AWAITING_PAYMENT",
  "createdAt": "2026-10-22T07:15:30Z",
  "totals": {
    "subtotal": 167000,
    "discount": 10000,
    "promoId": "promo_10k_over_100k",
    "tax": 15700,
    "rounding": 0,
    "total": 172700
  },
  "lines": [
    { "lineId": "l1", "description": { "en": "Fries · Cola · Extra cheddar · No onion", "id": "Kentang · Cola · Ekstra cheddar · Tanpa bawang" }, "lineTotal": 75000 }
  ]
}
```
- `orderNumber`: letter + 3 digits, reset daily per merchant (PRD D-21). It is shown on Complete and printed.
- The backend recomputes all prices. If its total ≠ `clientTotals.total`, it returns 409 `PRICE_CHANGED` and does not create the order.

**Error 409 PRICE_CHANGED**
```json
{
  "code": "PRICE_CHANGED",
  "message": "Prices have changed since the menu was loaded.",
  "details": { "menuVersion": "menu-119", "configVersion": "cfg-42", "serverTotals": { "subtotal": 169000, "discount": 10000, "tax": 15900, "rounding": 0, "total": 174900 } }
}
```
**Error 409 ITEM_UNAVAILABLE**
```json
{ "code": "ITEM_UNAVAILABLE", "message": "Some items are sold out.", "details": { "lineIds": ["l2"], "productIds": ["c1"] } }
```
**Other errors:** `409 MERCHANT_CLOSED`, `400 VALIDATION_ERROR` (e.g. quantity > 20, > 30 lines, missing required selection).

---

### E8 — Cancel order
`POST /v1/orders/{orderId}/cancel`

**Request**
```json
{ "reason": "CUSTOMER_BACK" }
```
`reason`: `CUSTOMER_BACK` | `TIMEOUT` | `PAYMENT_FAILED` | `APP_RECOVERY`.

**Response 200**
```json
{ "orderId": "ord_7Hq2k9", "status": "CANCELLED" }
```
**Errors:** `409 ORDER_ALREADY_PAID` if the order is already `PAID` (the app must then go to Complete).

Cancelling an order that is already `CANCELLED` also answers `200` with status `CANCELLED`, so a retry after a lost response and the app's recovery cancel (`APP_RECOVERY`) are safe. The app sends `Idempotency-Key` on E8 and reuses it for every retry of one cancel.

---

### E9 — Start payment
`POST /v1/orders/{orderId}/payments` · **`Idempotency-Key` required**

**Request (QRIS)**
```json
{ "method": "QRIS", "qrExpirySeconds": 300 }
```
**Response 201 (QRIS)**
```json
{
  "paymentId": "pay_Q1x8",
  "method": "QRIS",
  "status": "PENDING",
  "amount": 172700,
  "qrString": "00020101021226650013ID.CO.EXAMPLE.WWW0118936000000000000000303UMI51440014ID.CO.QRIS.WWW0215ID1020000004721520458125303360540617270058" ,
  "expiresAt": "2026-10-22T07:20:31Z",
  "createdAt": "2026-10-22T07:15:31Z"
}
```
- `qrString` is the EMVCo QRIS payload. The app renders it as a QR code and does not parse it. The mock may return any string.
- `expiresAt` is authoritative for the countdown. The app corrects for clock skew using the `Date` header.

**Request (Card)**
```json
{ "method": "CARD" }
```
**Response 201 (Card)**
```json
{
  "paymentId": "pay_C7m2",
  "method": "CARD",
  "status": "PENDING",
  "amount": 172700,
  "terminalRef": "TRX-20261022-000247",
  "terminal": { "tid": "TID 0003 1182", "connection": "USB" },
  "timeoutSeconds": 90,
  "createdAt": "2026-10-22T07:15:31Z"
}
```
The kiosk sends `amount` and `terminalRef` to the EDC terminal (protocol per PRD OQ-03).

**Errors:** `422 PAYMENT_METHOD_UNAVAILABLE`, `409 ORDER_NOT_AWAITING_PAYMENT` (order not `AWAITING_PAYMENT`), `502 GATEWAY_ERROR`.

---

### E10 — Payment status
`GET /v1/orders/{orderId}/payments/{paymentId}` · the app polls every 2 s

**Response 200 (pending)**
```json
{ "paymentId": "pay_Q1x8", "method": "QRIS", "status": "PENDING", "amount": 172700, "expiresAt": "2026-10-22T07:20:31Z" }
```
**Response 200 (paid)**
```json
{
  "paymentId": "pay_Q1x8",
  "method": "QRIS",
  "status": "PAID",
  "amount": 172700,
  "paidAt": "2026-10-22T07:16:02Z",
  "reference": "QR-8812-4471-0091",
  "issuer": "GoPay",
  "order": { "orderId": "ord_7Hq2k9", "orderNumber": "A247", "status": "PAID" }
}
```
**Response 200 (failed)**
```json
{ "paymentId": "pay_C7m2", "method": "CARD", "status": "FAILED", "amount": 172700, "reason": "DECLINED", "message": { "en": "Your bank declined the card.", "id": "Bank Anda menolak kartu." } }
```

| `status` | Final? | App shows |
|---|---|---|
| `PENDING` | no | Waiting (QR or terminal) |
| `PROCESSING` | no | "Confirming payment" |
| `PAID` | yes | Complete + print |
| `FAILED` | yes | Failed (reason below) + "not charged" |
| `EXPIRED` | yes | Failed: "The QR code expired…" |
| `CANCELLED` | yes | Back to Your order, or Failed if cancelled in the e-wallet app |

`reason` (when `FAILED`): `DECLINED` | `CANCELLED_BY_PAYER` | `TERMINAL_TIMEOUT` | `TERMINAL_ERROR` | `GATEWAY_ERROR` | `INSUFFICIENT_FUNDS`.

---

### E11 — Cancel payment
`POST /v1/orders/{orderId}/payments/{paymentId}/cancel`

**Request**
```json
{ "reason": "EXPIRED_ON_KIOSK" }
```
`reason`: `CUSTOMER_BACK` | `SWITCH_METHOD` | `EXPIRED_ON_KIOSK` | `TIMEOUT`.

**Response 200**
```json
{ "paymentId": "pay_Q1x8", "status": "CANCELLED" }
```
**Error 409 PAYMENT_NOT_CANCELLABLE** (already paid). The app then reads E10 and goes to Complete:
```json
{ "code": "PAYMENT_NOT_CANCELLABLE", "message": "Payment already completed.", "details": { "status": "PAID" } }
```

---

### E12 — Relay terminal result (optional, card)
`POST /v1/orders/{orderId}/payments/{paymentId}/terminal-result`

Needed only if the EDC result reaches the kiosk rather than the backend (PRD OQ-03). Payment is still only `PAID` once the backend confirms it via E10.

**Request**
```json
{
  "approved": true,
  "terminalRef": "TRX-20261022-000247",
  "tid": "TID 0003 1182",
  "approvalCode": "123456",
  "cardBrand": "VISA",
  "maskedPan": "•••• 4821",
  "rawResponseCode": "00"
}
```
**Response 200**
```json
{ "paymentId": "pay_C7m2", "status": "PROCESSING" }
```

---

### E13 — Change staff PIN
`PUT /v1/devices/{deviceId}/staff-pin`

**Request**
```json
{ "currentPin": "1234", "newPin": "5678" }
```
Sent over TLS only, never logged. (Alternative for the real backend: send a proof derived from the current hash. Agree on this before 0.7.0.)

**Response 200**: the new hash, so the app can update its config immediately.
```json
{
  "staffPin": { "algorithm": "PBKDF2WithHmacSHA256", "iterations": 120000, "salt": "b64:Zm9v", "hash": "b64:YmFy…", "version": 4 }
}
```
**Errors:** `409 PIN_INCORRECT`, `400 VALIDATION_ERROR` (not 4 digits, or the same as the current PIN).

---

### E14 — Health
`GET /v1/health` · no auth

**Response 200**
```json
{
  "status": "OK",
  "serverTime": "2026-10-22T07:15:30Z",
  "components": {
    "api": "OK",
    "database": "OK",
    "paymentGateway": "OK"
  }
}
```
`components.*`: `OK` | `DEGRADED` | `DOWN`. If `paymentGateway` is not `OK`, the app treats checkout as unavailable (same as offline) and the Connection test reports it separately.

---

## 4. Mock data checklist

To make the mock match the design screens one-to-one, include:

| Category | Products (id · name · price · kcal · badge) |
|---|---|
| Burgers | b1 Classic Cheeseburger 38000 · 540; **b2** Smoky Double Burger 52000 · 780 · NEW; b3 Hot Honey Chicken Burger 42000 · 620 · SPICY; b4 BBQ Beef Bacon Burger 45000 · 710 |
| Chicken | **c1** Crispy Chicken (2 pc) 38000 · 560 · BEST_SELLER; c2 Spicy Wings (6 pc) 36000 · 480 · SPICY; c3 Chicken Nuggets (9 pc) 32000 · 430; c4 Family Bucket (8 pc) 129000 · 2240 · SHARE |
| Rice bowls | **r1** Sambal Matah Rice Bowl 34000 · 610 · LOCAL; r2 Teriyaki Chicken Bowl 32000 · 590; r3 Ayam Geprek Bowl 30000 · 640 · SPICY; r4 Black Pepper Beef Bowl 39000 · 660 |
| Sides | s1 French Fries (Regular) 18000 · 320; s2 Cheese Fries 26000 · 450; s3 Chicken Nuggets (4 pc) 16000 · 190; s4 Spicy Wings (3 pc) 19000 · 240 |
| Drinks | **d1** Iced Lychee Tea 16000 · 140; d2 Cola (Regular) 14000 · 150; d3 Iced Coffee Latte 18000 · 180; d4 Mineral Water 600 ml 8000 · 0 |
| Desserts | e1 Caramel Sundae 15000 · 330; e2 Chocolate Sundae 15000 · 350; e3 Pandan Soft Serve 12000 · 210 · LOCAL |

Bold items are in "Popular" (`featuredProductIds`). Combos and slot options are as in E6.

**Modifier groups in the sample menu** (E6 is the complete list):
- b2 Smoky Double Burger: "Choose a size" (required, no default: Regular · Two patties / Large · Three patties · +Rp 6.000), "Make it a meal?" (default Burger only), "Customize" (ingredient levels) and "Add extras" (FR-010).
- s1 French Fries: Size (default Regular). d1 Iced Lychee Tea: Ice (default Regular ice).
- c4 Family Bucket: "Choose a flavour" (Original / Spicy / Half & half). r1 Sambal Matah Rice Bowl: "Spice level" (Mild / Medium / Hot). d2 Cola: "Choose a cola" (Classic / Zero sugar). All three are required, have no default and cost Rp 0, so no price or Budget Plan total changes. The Menu artboard marks these items as needing a choice; the options themselves are sample data chosen on 2026-10-23, and the PO may replace them.
- Every other product has no groups.
- Products with a required group that has no default (b2, c4, r1, d2) open Item detail instead of quick add; s1 and d1 have defaults, so quick add uses them (FR-009, A-08). A Budget Plan uses each group's default option, or the first option when it has none (A-42).
- The Budget Plan tags above are canonical. The Budget Plan artboard also offers d1 to children and orders its mains by preference; the planner uses these tags and `plannerWeight` instead (TC-161–TC-163 depend on them).

**Budget Plan tags for the mock:**
- `role`: Burgers, Chicken, Rice bowls → `MAIN`; Sides → `SIDE`; Drinks → `DRINK`; Desserts → `DESSERT`.
- `kidFriendly: true`: b1, c3, r2 (kid mains), s1, s3 (sides), d2, d4 (drinks), e1, e2, e3 (desserts). Everything else is `false`.
- `servesAdults`: c4 Family Bucket = 4 with `shareable: true`; everything else is 1.
- With these tags, testing-plan TC-161–TC-163 expect: 2 adults + 1 child at Rp 150.000 → Rp 149.600 / Rp 150.700; 1 adult + 1 child at Rp 99.000 → Rp 99.000; 2 adults + 1 child at Rp 100.000 → Rp 116.600 ("Need Rp 16.600 more").

**Mock scenarios to support.** A custom mock switches them with the `X-Mock-Scenario` header. With Prism, use the `Prefer` header in the last column.

| Scenario | Effect | Prism `Prefer` header |
|---|---|---|
| `activate_unregistered` | E1 → 404 DEVICE_NOT_REGISTERED | `code=404` |
| `activate_disabled` | E1 → 403 DEVICE_DISABLED | `code=403` |
| `merchants_empty` | E2 → `{"merchants": []}` | `example=empty` |
| `merchant_closed` | E2 → Urban Grill `isOpen: false`, `opensAt: 2026-10-23T03:00:00Z` (10:00 in Jakarta) | `example=closed` |
| `menu_download_failed` | E6 → 503 SERVER_ERROR (E3 and E5 succeed) | not possible per endpoint with one header; use the in-app fakes |
| `order_price_changed` | E7 → 409 PRICE_CHANGED | `code=409, example=priceChanged` |
| `order_item_unavailable` | E7 → 409 ITEM_UNAVAILABLE (c1) | `code=409, example=itemUnavailable` |
| `qris_paid_after_5s` | E10 → PENDING for 5 s, then PAID | `example=pending`, then `example=paid` (switched by the app's debug options) |
| `qris_cancelled` | E10 → FAILED / CANCELLED_BY_PAYER | `example=cancelledByPayer` |
| `qris_expire_fast` | E9 `expiresAt` = now + 20 s | not possible (static examples); then `example=expired` on E10 |
| `card_declined` | E10 → FAILED / DECLINED | `example=declined` |
| `card_timeout` | E10 → FAILED / TERMINAL_TIMEOUT after 90 s | `example=terminalTimeout` |
| `slow_network` | every response delayed 8 s | not supported by Prism |
| `server_error_once` | first E7 → 503, retry → 201 (same order) | `code=503`, then no header |
| `gateway_down` | E14 `paymentGateway: DOWN` | `example=gatewayDown` |

Card start on E9: `Prefer: example=card`. Without a `Prefer` header, Prism returns the happy path: the QRIS payment on E9 and `pending` on E10.

Prism serves fixed example timestamps (e.g. `expiresAt: 2026-10-22T07:20:31Z`), so the QR countdown will show as already expired against the real clock. For realistic timing, use the in-app fakes or a custom mock that sets `expiresAt` relative to now.

---

## 5. Enum reference

| Enum | Values |
|---|---|
| `merchant.status` | `live`, `coming_soon` |
| `mode` | `SINGLE` (`FOOD_COURT` reserved, post-1.0) |
| `orderType` | `DINE_IN`, `TAKEAWAY` |
| `language` | `en`, `id` (the 1.0 app sends `en`) |
| `payment.method` | `QRIS`, `CARD` (`COUNTER` reserved, post-1.0) |
| `payment.status` | `PENDING`, `PROCESSING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELLED` |
| `order.status` | `AWAITING_PAYMENT`, `PAID`, `CANCELLED` |
| `promo.type` | `THRESHOLD_FIXED` |
| `modifierGroup.type` | `SINGLE`, `MULTI`, `LEVEL` |
| `modifierGroup.purpose` | `MEAL_UPGRADE` (optional) |
| `level` | `NONE`, `REGULAR`, `EXTRA` |
| `badge.type` | `NEW`, `BEST_SELLER`, `SPICY`, `SHARE`, `LOCAL` |
| `product.role` | `MAIN`, `SIDE`, `DRINK`, `DESSERT` |
| `rounding.mode` | `UP` |
