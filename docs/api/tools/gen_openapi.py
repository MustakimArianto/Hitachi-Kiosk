"""Generate docs/api/openapi.yaml from the JSON examples in docs/api/api-spec.md.

Usage (from the repo root):
    python3 docs/api/tools/gen_openapi.py docs/api/api-spec.md docs/api/openapi.yaml

The JSON examples are read in document order (31 blocks); adding or reordering a block in
api-spec.md means updating the unpacking below. Standard library only.
"""
import copy
import json
import re
import sys

src, out = sys.argv[1], sys.argv[2]
blocks = [json.loads(b) for b in re.findall(r"```json\n(.*?)```", open(src).read(), re.S)]
assert len(blocks) == 31, len(blocks)
(ERR, E1_REQ, E1_RES, E1_404, E2_RES, E3_REQ, E3_RES, E5_RES, E6_RES, E7_REQ, COMBO_LINE,
 E7_RES, E7_PRICE, E7_UNAVAIL, E8_REQ, E8_RES, E9_REQ_QR, E9_RES_QR, E9_REQ_CARD, E9_RES_CARD,
 E10_PENDING, E10_PAID, E10_FAILED, E11_REQ, E11_RES, E11_409, E12_REQ, E12_RES, E13_REQ,
 E13_RES, E14_RES) = blocks

# Prism mock: let the debug build verify PIN 1234 (api-spec E5 note).
E5_MOCK = copy.deepcopy(E5_RES)
E5_MOCK["staffPin"] = {"algorithm": "PLAIN_MOCK_ONLY", "hash": "1234", "version": 1}

def ref(name):
    return {"$ref": f"#/components/schemas/{name}"}

def arr(item):
    return {"type": "array", "items": item}

def obj(props, required=(), nullable=False):
    s = {"type": "object", "properties": props}
    if required:
        s["required"] = list(required)
    if nullable:
        s["nullable"] = True
    return s

STR, INT, BOOL = {"type": "string"}, {"type": "integer"}, {"type": "boolean"}
MONEY = {"type": "integer", "minimum": 0, "description": "Integer Rupiah (IDR)."}
TIME = {"type": "string", "format": "date-time"}
def enum(*v, nullable=False):
    s = {"type": "string", "enum": list(v)}
    if nullable:
        s["nullable"] = True
    return s
def nstr():
    return {"type": "string", "nullable": True}
def nref(name):
    # OpenAPI 3.0: "nullable" needs a sibling "type", so a nullable $ref is spelled out inline.
    assert name == "LocalizedText"
    return {"type": "object", "nullable": True, "properties": {"en": STR, "id": STR}}

L = ref("LocalizedText")

schemas = {
    "LocalizedText": {"type": "object", "description": "Localized text; the app falls back to the other language.",
                      "properties": {"en": STR, "id": STR}},
    "Error": obj({"code": enum("VALIDATION_ERROR", "TOKEN_INVALID", "DEVICE_DISABLED", "DEVICE_NOT_REGISTERED",
                               "NOT_FOUND", "PRICE_CHANGED", "ITEM_UNAVAILABLE", "MERCHANT_CLOSED",
                               "PAYMENT_NOT_CANCELLABLE", "PIN_INCORRECT", "PAYMENT_METHOD_UNAVAILABLE",
                               "RATE_LIMITED", "SERVER_ERROR", "GATEWAY_ERROR"),
                  "message": STR, "details": {"type": "object", "additionalProperties": True}},
                 ["code", "message"]),
    "Merchant": obj({"id": STR, "name": STR, "cuisine": L, "logoUrl": nstr(), "photoUrl": nstr(),
                     "status": enum("live", "coming_soon"), "isOpen": BOOL},
                    ["id", "name", "status", "isOpen"]),
    "ActivateRequest": obj({"serialNumber": STR, "model": STR, "osVersion": STR, "appVersion": STR},
                           ["serialNumber", "appVersion"]),
    "ActivateResponse": obj({"deviceId": STR, "deviceToken": STR, "tokenExpiresAt": {**TIME, "nullable": True},
                             "kioskCode": STR, "kioskName": STR, "boundMerchantId": nstr(),
                             "merchants": arr(ref("Merchant"))},
                            ["deviceId", "deviceToken", "kioskCode", "kioskName", "merchants"]),
    "MerchantList": obj({"merchants": arr(ref("Merchant"))}, ["merchants"]),
    "BindingRequest": obj({"merchantId": STR}, ["merchantId"]),
    "BindingResponse": obj({"deviceId": STR, "merchantId": STR, "kioskName": STR, "boundAt": TIME},
                           ["deviceId", "merchantId"]),
    "StaffPin": obj({"algorithm": enum("PBKDF2WithHmacSHA256", "PLAIN_MOCK_ONLY"), "iterations": INT,
                     "salt": STR, "hash": STR, "version": INT}, ["algorithm", "hash", "version"]),
    "Promo": obj({"id": STR, "type": enum("THRESHOLD_FIXED"), "threshold": MONEY, "amount": MONEY, "label": L},
                 ["id", "type", "threshold", "amount"]),
    "PaymentMethodConfig": obj({
        "method": enum("QRIS", "CARD"), "enabled": BOOL, "qrisMerchantIdMasked": STR,
        "qrExpirySecondsAllowed": arr(INT), "qrExpirySecondsDefault": INT,
        "terminal": obj({"tid": STR, "connection": STR})}, ["method", "enabled"]),
    "MerchantConfig": obj({
        "configVersion": STR, "merchantId": STR, "mode": enum("SINGLE", "FOOD_COURT"), "brandName": STR,
        "accentColor": {"type": "string", "pattern": "^#[0-9A-Fa-f]{6}$"}, "logoUrl": nstr(),
        "heroPhotoUrl": nstr(), "isOpen": BOOL, "currency": enum("IDR"),
        "tax": obj({"label": L, "rateBasisPoints": INT, "inclusive": BOOL}, ["rateBasisPoints", "inclusive"]),
        "rounding": obj({"allowedSteps": arr(INT), "default": INT, "mode": enum("UP")}),
        "promos": arr(ref("Promo")),
        "orderTypes": arr(enum("DINE_IN", "TAKEAWAY")),
        "paymentMethods": arr(ref("PaymentMethodConfig")),
        "staffPin": ref("StaffPin"),
        "receipt": obj({"address": nstr(), "npwp": nstr(), "defaultFooter": L}),
        "upsell": obj({"categoryIds": arr(STR), "maxSuggestions": INT}),
        "idlePromos": arr(obj({"imageUrl": nstr()}))},
        ["configVersion", "merchantId", "mode", "brandName", "tax", "orderTypes", "paymentMethods", "staffPin"]),
    "Category": obj({"id": STR, "name": L, "sort": INT, "tone": STR}, ["id", "name", "sort"]),
    "Badge": obj({"type": enum("NEW", "BEST_SELLER", "SPICY", "SHARE", "LOCAL"), "label": L}, ["type"], nullable=True),
    "Product": obj({
        "id": STR, "categoryId": STR, "name": L, "description": nref("LocalizedText"), "price": MONEY,
        "kcal": {"type": "integer", "nullable": True}, "badge": ref("Badge"), "tags": nref("LocalizedText"),
        "imageUrl": nstr(), "available": BOOL, "modifierGroupIds": arr(STR)},
        ["id", "categoryId", "name", "price", "available", "modifierGroupIds"]),
    "ModifierOption": obj({
        "id": STR, "name": L, "description": L, "note": L, "priceDelta": {"type": "integer"},
        "default": BOOL, "imageUrl": nstr(),
        "levels": arr(obj({"level": enum("NONE", "REGULAR", "EXTRA"), "priceDelta": {"type": "integer"}},
                          ["level", "priceDelta"])),
        "defaultLevel": enum("NONE", "REGULAR", "EXTRA")}, ["id", "name"]),
    "ModifierGroup": obj({"id": STR, "name": L, "type": enum("SINGLE", "MULTI", "LEVEL"), "required": BOOL,
                          "min": INT, "max": INT, "options": arr(ref("ModifierOption"))},
                         ["id", "name", "type", "required", "options"]),
    "Combo": obj({"id": STR, "name": L, "shortName": L, "includes": L, "price": MONEY, "separatePrice": MONEY,
                  "serves": {"type": "integer", "minimum": 1}, "imageUrl": nstr(), "tone": STR, "available": BOOL},
                 ["id", "name", "price", "separatePrice", "serves", "available"]),
    "ComboSlotOption": obj({"id": STR, "name": L, "upcharge": MONEY, "imageUrl": nstr()}, ["id", "name", "upcharge"]),
    "PromoBanner": obj({"id": STR, "tag": L, "title": L, "priceText": L, "imageUrl": nstr(),
                        "target": obj({"type": enum("PRODUCT", "COMBO"), "id": STR}, ["type", "id"])},
                       ["id", "title", "target"]),
    "Menu": obj({
        "menuVersion": STR, "categories": arr(ref("Category")),
        "combosCategory": obj({"name": L, "sort": INT}),
        "featuredProductIds": arr(STR), "promoBanners": arr(ref("PromoBanner")),
        "products": arr(ref("Product")), "modifierGroups": arr(ref("ModifierGroup")),
        "combos": arr(ref("Combo")),
        "comboSlots": obj({"SIDE": arr(ref("ComboSlotOption")), "DRINK": arr(ref("ComboSlotOption")),
                           "SIZE_LARGE_UPCHARGE": MONEY}, ["SIDE", "DRINK", "SIZE_LARGE_UPCHARGE"])},
        ["menuVersion", "categories", "products", "modifierGroups", "combos", "comboSlots"]),
    "Selection": obj({"groupId": STR, "optionId": STR, "level": enum("NONE", "REGULAR", "EXTRA")},
                     ["groupId", "optionId"]),
    "OrderLine": obj({
        "lineId": STR, "kind": enum("PRODUCT", "COMBO"), "productId": STR, "comboId": STR,
        "quantity": {"type": "integer", "minimum": 1, "maximum": 20},
        "selections": arr(ref("Selection")),
        "comboSelections": obj({"sideId": STR, "drinkId": STR, "size": enum("REGULAR", "LARGE")},
                               ["sideId", "drinkId", "size"]),
        "unitPrice": MONEY, "lineTotal": MONEY},
        ["lineId", "kind", "quantity", "unitPrice", "lineTotal"]),
    "Totals": obj({"subtotal": MONEY, "discount": MONEY, "promoId": nstr(), "tax": MONEY,
                   "rounding": MONEY, "total": MONEY},
                  ["subtotal", "discount", "tax", "rounding", "total"]),
    "CreateOrderRequest": obj({
        "merchantId": STR, "orderType": enum("DINE_IN", "TAKEAWAY"), "language": enum("en", "id"),
        "menuVersion": STR, "configVersion": STR, "roundingStep": {"type": "integer", "enum": [0, 100, 500]},
        "lines": {"type": "array", "minItems": 1, "maxItems": 30, "items": ref("OrderLine")},
        "clientTotals": ref("Totals")},
        ["merchantId", "orderType", "language", "menuVersion", "lines", "clientTotals"]),
    "OrderResponse": obj({
        "orderId": STR, "orderNumber": {"type": "string", "pattern": "^[A-Z][0-9]{3}$"},
        "status": enum("AWAITING_PAYMENT", "PAID", "CANCELLED"), "createdAt": TIME, "totals": ref("Totals"),
        "lines": arr(obj({"lineId": STR, "description": L, "lineTotal": MONEY}))},
        ["orderId", "orderNumber", "status", "totals"]),
    "CancelOrderRequest": obj({"reason": enum("CUSTOMER_BACK", "TIMEOUT", "PAYMENT_FAILED", "APP_RECOVERY")},
                              ["reason"]),
    "OrderStatusResponse": obj({"orderId": STR, "status": enum("AWAITING_PAYMENT", "PAID", "CANCELLED")},
                               ["orderId", "status"]),
    "StartPaymentRequest": obj({"method": enum("QRIS", "CARD"),
                                "qrExpirySeconds": {"type": "integer", "enum": [180, 300, 600]}}, ["method"]),
    "PaymentStatus": enum("PENDING", "PROCESSING", "PAID", "FAILED", "EXPIRED", "CANCELLED"),
    "Payment": obj({
        "paymentId": STR, "method": enum("QRIS", "CARD"), "status": ref("PaymentStatus"), "amount": MONEY,
        "qrString": {"type": "string", "description": "EMVCo QRIS payload (QRIS only)."},
        "expiresAt": TIME, "terminalRef": STR, "terminal": obj({"tid": STR, "connection": STR}),
        "timeoutSeconds": INT, "createdAt": TIME, "paidAt": TIME, "reference": STR, "issuer": STR,
        "reason": enum("DECLINED", "CANCELLED_BY_PAYER", "TERMINAL_TIMEOUT", "TERMINAL_ERROR",
                       "GATEWAY_ERROR", "INSUFFICIENT_FUNDS"),
        "message": L,
        "order": obj({"orderId": STR, "orderNumber": STR, "status": STR})},
        ["paymentId", "method", "status", "amount"]),
    "CancelPaymentRequest": obj({"reason": enum("CUSTOMER_BACK", "SWITCH_METHOD", "EXPIRED_ON_KIOSK", "TIMEOUT")},
                                ["reason"]),
    "PaymentStatusResponse": obj({"paymentId": STR, "status": ref("PaymentStatus")}, ["paymentId", "status"]),
    "TerminalResultRequest": obj({"approved": BOOL, "terminalRef": STR, "tid": STR, "approvalCode": STR,
                                  "cardBrand": STR, "maskedPan": STR, "rawResponseCode": STR},
                                 ["approved", "terminalRef", "tid"]),
    "ChangePinRequest": obj({"currentPin": {"type": "string", "pattern": "^[0-9]{4}$"},
                             "newPin": {"type": "string", "pattern": "^[0-9]{4}$"}}, ["currentPin", "newPin"]),
    "ChangePinResponse": obj({"staffPin": ref("StaffPin")}, ["staffPin"]),
    "Health": obj({"status": enum("OK", "DEGRADED", "DOWN"), "serverTime": TIME,
                   "components": obj({"api": enum("OK", "DEGRADED", "DOWN"), "database": enum("OK", "DEGRADED", "DOWN"),
                                      "paymentGateway": enum("OK", "DEGRADED", "DOWN")})},
                  ["status", "serverTime", "components"]),
}

def err(code, message, details=None):
    return {"code": code, "message": message, "details": details or {}}

def body(schema, examples):
    return {"content": {"application/json": {"schema": ref(schema),
            "examples": {k: {"summary": s, "value": v} for k, (s, v) in examples.items()}}}}

def resp(desc, schema=None, examples=None, headers=None):
    r = {"description": desc}
    if schema:
        r.update(body(schema, examples))
    if headers:
        r["headers"] = headers
    return r

def error_resp(desc, examples):
    return resp(desc, "Error", examples)

ETAG_HDR = {"ETag": {"description": "Version tag; send back in If-None-Match.", "schema": STR}}
R401 = {"$ref": "#/components/responses/Unauthorized"}
R5XX = {"$ref": "#/components/responses/ServerError"}

def p(name):
    return {"$ref": f"#/components/parameters/{name}"}

COMMON = [p("XAppVersion"), p("AcceptLanguage"), p("XMockScenario")]
AUTHED = COMMON + [p("XDeviceId")]

def op(op_id, summary, tags, params, responses, request=None, secure=True, description=None):
    o = {"operationId": op_id, "summary": summary, "tags": tags, "parameters": params, "responses": responses}
    if description:
        o["description"] = description
    if request:
        o["requestBody"] = {"required": True, **request}
    if not secure:
        o["security"] = []
    return o

# Extra derived examples (same shapes as api-spec, other states).
E10_PROCESSING = {**{k: E10_PENDING[k] for k in ("paymentId", "method", "amount")}, "status": "PROCESSING"}
E10_EXPIRED = {**E10_PENDING, "status": "EXPIRED"}
E10_CANCELLED = {"paymentId": "pay_Q1x8", "method": "QRIS", "status": "FAILED", "amount": 172700,
                 "reason": "CANCELLED_BY_PAYER",
                 "message": {"en": "The payment was cancelled in your app.", "id": "Pembayaran dibatalkan di aplikasi Anda."}}
E10_TIMEOUT = {**E10_FAILED, "reason": "TERMINAL_TIMEOUT",
               "message": {"en": "The card terminal didn't respond.", "id": "Terminal kartu tidak merespons."}}
E7_REQ_COMBO = {**{k: E7_REQ[k] for k in ("merchantId", "orderType", "language", "menuVersion", "configVersion", "roundingStep")},
                "lines": [COMBO_LINE],
                "clientTotals": {"subtotal": 74000, "discount": 0, "promoId": None, "tax": 7400, "rounding": 0, "total": 81400}}
E14_GATEWAY_DOWN = {**E14_RES, "status": "DEGRADED", "components": {**E14_RES["components"], "paymentGateway": "DOWN"}}

DEVICE_ID = p("DeviceId")
MERCHANT_ID = p("MerchantId")
ORDER_ID = p("OrderId")
PAYMENT_ID = p("PaymentId")

paths = {
    "/v1/devices/activate": {"post": op(
        "activateDevice", "E1 — Activate kiosk by serial number", ["Device"], COMMON,
        {"200": resp("Activated.", "ActivateResponse", {"default": ("Registered SN", E1_RES)}),
         "400": error_resp("Invalid body.", {"validation": ("Validation error", err("VALIDATION_ERROR", "serialNumber is required."))}),
         "403": error_resp("Device disabled.", {"disabled": ("Disabled in dashboard", err("DEVICE_DISABLED", "This kiosk has been disabled."))}),
         "404": error_resp("SN not registered.", {"unregistered": ("SN not registered", E1_404)}),
         "500": R5XX},
        body("ActivateRequest", {"default": ("Activation", E1_REQ)}), secure=False)},
    "/v1/devices/{deviceId}/merchants": {"get": op(
        "listMerchants", "E2 — Merchants this kiosk may serve", ["Device"], [DEVICE_ID] + AUTHED,
        {"200": resp("Merchant list.", "MerchantList", {
            "default": ("Urban Grill live, four coming soon", E2_RES),
            "empty": ("No merchants yet", {"merchants": []})}),
         "401": R401, "500": R5XX})},
    "/v1/devices/{deviceId}/binding": {
        "put": op("bindMerchant", "E3 — Bind kiosk to a merchant", ["Device"], [DEVICE_ID, p("IdempotencyKeyOptional")] + AUTHED,
                  {"200": resp("Bound.", "BindingResponse", {"default": ("Bound to Urban Grill", E3_RES)}),
                   "401": R401,
                   "404": error_resp("Unknown merchant.", {"notFound": ("Unknown merchant", err("NOT_FOUND", "Merchant not found."))}),
                   "409": error_resp("Merchant not live.", {"comingSoon": ("Merchant is coming soon", err("VALIDATION_ERROR", "Merchant is not live yet."))}),
                   "500": R5XX},
                  body("BindingRequest", {"default": ("Urban Grill", E3_REQ)})),
        "delete": op("unbindMerchant", "E4 — Unbind / log out", ["Device"], [DEVICE_ID] + AUTHED,
                     {"204": {"description": "Unbound."}, "401": R401, "500": R5XX})},
    "/v1/merchants/{merchantId}/config": {"get": op(
        "getMerchantConfig", "E5 — Merchant config (tax, promos, payment, staff PIN, branding)", ["Catalog"],
        [MERCHANT_ID, p("IfNoneMatch")] + AUTHED,
        {"200": resp("Config.", "MerchantConfig", {
            "default": ("Urban Grill (mock PIN 1234, debug builds only)", E5_MOCK),
            "production": ("Production-shaped hashed PIN", E5_RES)}, ETAG_HDR),
         "304": {"description": "Not modified (If-None-Match matched)."},
         "401": R401, "500": R5XX})},
    "/v1/merchants/{merchantId}/menu": {"get": op(
        "getMenu", "E6 — Full menu", ["Catalog"], [MERCHANT_ID, p("IfNoneMatch")] + AUTHED,
        {"200": resp("Menu.", "Menu", {"default": ("Urban Grill menu (abbreviated, see api-spec §4)", E6_RES)}, ETAG_HDR),
         "304": {"description": "Not modified (If-None-Match matched)."},
         "401": R401, "500": R5XX})},
    "/v1/orders": {"post": op(
        "createOrder", "E7 — Create order (backend re-prices and assigns order number)", ["Orders"],
        [p("IdempotencyKey")] + AUTHED,
        {"201": resp("Order created.", "OrderResponse", {"default": ("Order A247, Rp 172.700", E7_RES)}),
         "400": error_resp("Invalid order.", {
             "invalidRequest": ("Missing header or invalid body", err("VALIDATION_ERROR", "Invalid order request (check Idempotency-Key and body).")),
             "quantityTooHigh": ("Quantity above 20", err("VALIDATION_ERROR", "quantity must be at most 20.", {"lineId": "l1"}))}),
         "401": R401,
         "409": error_resp("Order rejected.", {
             "priceChanged": ("Prices changed", E7_PRICE),
             "itemUnavailable": ("Item sold out", E7_UNAVAIL),
             "merchantClosed": ("Merchant closed", err("MERCHANT_CLOSED", "The restaurant isn't taking orders right now."))}),
         "500": R5XX,
         "503": error_resp("Temporary failure; retry with the same Idempotency-Key.", {"serverError": ("Server error", err("SERVER_ERROR", "Temporary failure."))})},
        body("CreateOrderRequest", {"default": ("Design sample cart", E7_REQ), "combo": ("One combo line", E7_REQ_COMBO)}))},
    "/v1/orders/{orderId}/cancel": {"post": op(
        "cancelOrder", "E8 — Abandon an unpaid order", ["Orders"], [ORDER_ID, p("IdempotencyKeyOptional")] + AUTHED,
        {"200": resp("Cancelled.", "OrderStatusResponse", {"default": ("Cancelled", E8_RES)}),
         "401": R401,
         "409": error_resp("Already paid.", {"alreadyPaid": ("Order already paid", err("VALIDATION_ERROR", "Order is already paid.", {"status": "PAID"}))}),
         "500": R5XX},
        body("CancelOrderRequest", {"default": ("Customer went back", E8_REQ)}))},
    "/v1/orders/{orderId}/payments": {"post": op(
        "startPayment", "E9 — Start a QRIS or card payment", ["Payments"], [ORDER_ID, p("IdempotencyKey")] + AUTHED,
        {"201": resp("Payment started.", "Payment", {
            "qris": ("QRIS payment", E9_RES_QR),
            "card": ("Card payment on EDC terminal", E9_RES_CARD)}),
         "401": R401,
         "409": error_resp("Order not awaiting payment.", {"wrongState": ("Order not awaiting payment", err("VALIDATION_ERROR", "Order is not awaiting payment."))}),
         "422": error_resp("Method unavailable.", {"methodUnavailable": ("Method disabled", err("PAYMENT_METHOD_UNAVAILABLE", "Card payments are not enabled for this merchant."))}),
         "502": error_resp("Gateway error.", {"gatewayError": ("Gateway error", err("GATEWAY_ERROR", "Payment gateway unavailable."))}),
         "500": R5XX},
        body("StartPaymentRequest", {"qris": ("QRIS, 5 min", E9_REQ_QR), "card": ("Card", E9_REQ_CARD)}),
        description="Prism returns the `qris` example by default; send `Prefer: example=card` for the card response.")},
    "/v1/orders/{orderId}/payments/{paymentId}": {"get": op(
        "getPayment", "E10 — Payment status (polled every 2 s)", ["Payments"], [ORDER_ID, PAYMENT_ID] + AUTHED,
        {"200": resp("Current status.", "Payment", {
            "pending": ("Waiting for customer", E10_PENDING),
            "processing": ("Confirming", E10_PROCESSING),
            "paid": ("Paid", E10_PAID),
            "declined": ("Card declined", E10_FAILED),
            "cancelledByPayer": ("Cancelled in e-wallet", E10_CANCELLED),
            "terminalTimeout": ("Terminal timeout", E10_TIMEOUT),
            "expired": ("QR expired", E10_EXPIRED)}),
         "401": R401,
         "404": error_resp("Unknown payment.", {"notFound": ("Not found", err("NOT_FOUND", "Payment not found."))}),
         "500": R5XX},
        description="Prism returns `pending` by default. Use `Prefer: example=paid` (or declined, expired, …) to simulate outcomes.")},
    "/v1/orders/{orderId}/payments/{paymentId}/cancel": {"post": op(
        "cancelPayment", "E11 — Cancel a pending payment", ["Payments"],
        [ORDER_ID, PAYMENT_ID, p("IdempotencyKeyOptional")] + AUTHED,
        {"200": resp("Cancelled.", "PaymentStatusResponse", {"default": ("Cancelled", E11_RES)}),
         "401": R401,
         "409": error_resp("Already final.", {"alreadyPaid": ("Already paid", E11_409)}),
         "500": R5XX},
        body("CancelPaymentRequest", {"default": ("QR expired on kiosk", E11_REQ)}))},
    "/v1/orders/{orderId}/payments/{paymentId}/terminal-result": {"post": op(
        "relayTerminalResult", "E12 — Relay EDC terminal result (only if the terminal reports to the kiosk)",
        ["Payments"], [ORDER_ID, PAYMENT_ID] + AUTHED,
        {"200": resp("Accepted.", "PaymentStatusResponse", {"default": ("Processing", E12_RES)}),
         "401": R401, "500": R5XX},
        body("TerminalResultRequest", {"default": ("Approved", E12_REQ)}))},
    "/v1/devices/{deviceId}/staff-pin": {"put": op(
        "changeStaffPin", "E13 — Change staff PIN", ["Device"], [DEVICE_ID, p("IdempotencyKeyOptional")] + AUTHED,
        {"200": resp("Changed.", "ChangePinResponse", {"default": ("New hash", E13_RES)}),
         "400": error_resp("Invalid PIN.", {"validation": ("Not 4 digits", err("VALIDATION_ERROR", "PIN must be 4 digits."))}),
         "401": R401,
         "409": error_resp("Wrong current PIN.", {"incorrect": ("Wrong current PIN", err("PIN_INCORRECT", "Current PIN is incorrect."))}),
         "500": R5XX},
        body("ChangePinRequest", {"default": ("1234 → 5678", E13_REQ)}))},
    "/v1/health": {"get": op(
        "getHealth", "E14 — Backend and gateway reachability", ["System"], COMMON,
        {"200": resp("Health.", "Health", {"default": ("All OK", E14_RES), "gatewayDown": ("Gateway down", E14_GATEWAY_DOWN)})},
        secure=False)},
}

parameters = {
    "DeviceId": {"name": "deviceId", "in": "path", "required": True, "schema": STR, "example": "dev_0003"},
    "MerchantId": {"name": "merchantId", "in": "path", "required": True, "schema": STR, "example": "mer_urbangrill"},
    "OrderId": {"name": "orderId", "in": "path", "required": True, "schema": STR, "example": "ord_7Hq2k9"},
    "PaymentId": {"name": "paymentId", "in": "path", "required": True, "schema": STR, "example": "pay_Q1x8"},
    "IdempotencyKey": {"name": "Idempotency-Key", "in": "header", "required": True,
                       "schema": {"type": "string", "format": "uuid"},
                       "description": "Same key + same body returns the same result and never creates a duplicate."},
    "IdempotencyKeyOptional": {"name": "Idempotency-Key", "in": "header", "required": False,
                               "schema": {"type": "string", "format": "uuid"}},
    "IfNoneMatch": {"name": "If-None-Match", "in": "header", "required": False, "schema": STR, "example": "\"cfg-42\""},
    "XAppVersion": {"name": "X-App-Version", "in": "header", "required": False, "schema": STR, "example": "0.3.0"},
    "XDeviceId": {"name": "X-Device-Id", "in": "header", "required": False, "schema": STR, "example": "dev_0003"},
    "AcceptLanguage": {"name": "Accept-Language", "in": "header", "required": False, "schema": enum("en", "id")},
    "XMockScenario": {"name": "X-Mock-Scenario", "in": "header", "required": False, "schema": STR,
                      "description": "Scenario name from api-spec.md §4. Ignored by Prism (use the Prefer header); "
                                     "honoured by a custom mock server."},
}

components = {
    "securitySchemes": {"deviceToken": {"type": "http", "scheme": "bearer",
                                        "description": "deviceToken returned by E1."}},
    "parameters": parameters,
    "responses": {
        "Unauthorized": error_resp("Device token revoked or missing → app returns to Sign in.",
                                   {"tokenInvalid": ("Token invalid", err("TOKEN_INVALID", "Device token is invalid."))}),
        "ServerError": error_resp("Server error; idempotent calls are retried (1 s / 2 s / 4 s).",
                                  {"serverError": ("Server error", err("SERVER_ERROR", "Unexpected error."))}),
    },
    "schemas": schemas,
}

DESCRIPTION = """Contract between the Hitachi Kiosk Android app and its backend.
Generated from docs/api/api-spec.md (the human-readable source of truth). Change that file first, then regenerate.

## Run as a mock with Prism
    npx @stoplight/prism-cli mock docs/api/openapi.yaml --port 4010

From the Android emulator use base URL http://10.0.2.2:4010/ .
Authenticated calls need any `Authorization: Bearer <token>` header (Prism checks presence only).

## Simulating scenarios with Prism (Prefer header)
| api-spec §4 scenario | Request | Prefer header |
|---|---|---|
| activate_unregistered | E1 | code=404 |
| activate_disabled | E1 | code=403 |
| order_price_changed | E7 | code=409, example=priceChanged |
| order_item_unavailable | E7 | code=409, example=itemUnavailable |
| server_error_once | E7 | code=503 |
| card payment | E9 | example=card |
| qris_paid_after_5s | E10 | example=paid |
| qris_cancelled | E10 | example=cancelledByPayer |
| qris expired | E10 | example=expired |
| card_declined | E10 | example=declined |
| card_timeout | E10 | example=terminalTimeout |
| gateway_down | E14 | example=gatewayDown |

Prism serves static examples, so time-based scenarios (paid after 5 s, fast expiry, slow network)
need either the Prefer header switched by the app's debug Developer options or a custom mock server."""

spec = {
    "openapi": "3.0.3",
    "info": {"title": "Hitachi Kiosk Backend API", "version": "1.0.0-draft", "description": DESCRIPTION},
    "servers": [
        {"url": "http://127.0.0.1:4010", "description": "Prism mock (host)"},
        {"url": "http://10.0.2.2:4010", "description": "Prism mock (from Android emulator)"},
        {"url": "https://dev-api.hitachikiosk.example", "description": "dev"},
        {"url": "https://staging-api.hitachikiosk.example", "description": "staging"},
        {"url": "https://api.hitachikiosk.example", "description": "prod"},
    ],
    "tags": [{"name": n} for n in ("Device", "Catalog", "Orders", "Payments", "System")],
    "security": [{"deviceToken": []}],
    "paths": paths,
    "components": components,
}

# --- minimal YAML writer (no PyYAML on this machine) ---
PLAIN_KEY = re.compile(r"^[A-Za-z_][A-Za-z0-9_\-]*$")
RESERVED = {"y", "n", "yes", "no", "true", "false", "null", "on", "off", "~"}

def key(k):
    k = str(k)
    return k if PLAIN_KEY.match(k) and k.lower() not in RESERVED else json.dumps(k, ensure_ascii=False)

def scalar(v):
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return repr(v)
    return json.dumps(v, ensure_ascii=False)

def emit(v, ind, lines):
    pad = "  " * ind
    if isinstance(v, dict):
        for k, x in v.items():
            if isinstance(x, (dict, list)) and x:
                lines.append(f"{pad}{key(k)}:")
                emit(x, ind + 1, lines)
            elif isinstance(x, str) and "\n" in x:
                lines.append(f"{pad}{key(k)}: |-")
                lines.extend((f"{pad}  {ln}" if ln else "") for ln in x.split("\n"))
            else:
                lines.append(f"{pad}{key(k)}: {('{}' if isinstance(x, dict) else '[]') if isinstance(x, (dict, list)) else scalar(x)}")
    else:
        for x in v:
            if isinstance(x, dict) and x:
                sub = []
                emit(x, ind + 1, sub)
                sub[0] = f"{pad}- " + sub[0][len(pad) + 2:]
                lines.extend(sub)
            elif isinstance(x, list) and x:
                lines.append(f"{pad}-")
                emit(x, ind + 1, lines)
            else:
                lines.append(f"{pad}- {('{}' if isinstance(x, dict) else '[]') if isinstance(x, (dict, list)) else scalar(x)}")

lines = ["# GENERATED from docs/api/api-spec.md by docs/api/tools/gen_openapi.py — edit the spec, then regenerate.", ""]
emit(spec, 0, lines)
open(out, "w").write("\n".join(lines) + "\n")
print("wrote", out, len(lines), "lines")
