package com.hitachi.kiosk.core.testing.fixture

import com.hitachi.kiosk.core.domain.device.DeviceIdentity
import com.hitachi.kiosk.core.domain.model.Activation
import com.hitachi.kiosk.core.domain.model.ActivationRequest
import com.hitachi.kiosk.core.domain.model.Badge
import com.hitachi.kiosk.core.domain.model.BadgeType
import com.hitachi.kiosk.core.domain.model.Binding
import com.hitachi.kiosk.core.domain.model.BoundMerchant
import com.hitachi.kiosk.core.domain.model.BudgetPlannerConfig
import com.hitachi.kiosk.core.domain.model.CancelledOrder
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Category
import com.hitachi.kiosk.core.domain.model.Combo
import com.hitachi.kiosk.core.domain.model.ComboSelection
import com.hitachi.kiosk.core.domain.model.ComboSize
import com.hitachi.kiosk.core.domain.model.ComboSlotOption
import com.hitachi.kiosk.core.domain.model.ComboSlots
import com.hitachi.kiosk.core.domain.model.CombosCategory
import com.hitachi.kiosk.core.domain.model.CreatedOrder
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.Health
import com.hitachi.kiosk.core.domain.model.HealthStatus
import com.hitachi.kiosk.core.domain.model.KioskDevice
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.model.Level
import com.hitachi.kiosk.core.domain.model.LevelPrice
import com.hitachi.kiosk.core.domain.model.LocalizedText
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.Merchant
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.model.MerchantStatus
import com.hitachi.kiosk.core.domain.model.ModifierGroup
import com.hitachi.kiosk.core.domain.model.ModifierOption
import com.hitachi.kiosk.core.domain.model.ModifierPurpose
import com.hitachi.kiosk.core.domain.model.ModifierType
import com.hitachi.kiosk.core.domain.model.OrderLine
import com.hitachi.kiosk.core.domain.model.OrderLineSummary
import com.hitachi.kiosk.core.domain.model.OrderRequest
import com.hitachi.kiosk.core.domain.model.OrderStatus
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.PaidOrder
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentMethodConfig
import com.hitachi.kiosk.core.domain.model.PaymentState
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.domain.model.PaymentUpdate
import com.hitachi.kiosk.core.domain.model.Product
import com.hitachi.kiosk.core.domain.model.ProductRole
import com.hitachi.kiosk.core.domain.model.Promo
import com.hitachi.kiosk.core.domain.model.PromoType
import com.hitachi.kiosk.core.domain.model.ReceiptConfig
import com.hitachi.kiosk.core.domain.model.RoundingConfig
import com.hitachi.kiosk.core.domain.model.RoundingMode
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.domain.model.Selection
import com.hitachi.kiosk.core.domain.model.StaffPinHash
import com.hitachi.kiosk.core.domain.model.StartedPayment
import com.hitachi.kiosk.core.domain.model.TaxConfig
import com.hitachi.kiosk.core.domain.model.TerminalInfo
import com.hitachi.kiosk.core.domain.model.TerminalResult
import com.hitachi.kiosk.core.domain.model.Totals
import com.hitachi.kiosk.core.domain.model.UpsellConfig
import kotlin.time.Instant

/**
 * The api-spec.md examples (the "Urban Grill" design data) as domain objects. Fakes answer with these on the happy
 * path, and contract tests compare the parsed [Fixtures] with them (testing-plan §2.2).
 */
@Suppress("MagicNumber", "LargeClass")
object SampleData {
    const val SERIAL_NUMBER = "IMN24K0003118"
    const val UNREGISTERED_SERIAL_NUMBER = "IMN24K9999999"
    const val DEVICE_ID = "dev_0003"
    const val MERCHANT_ID = "mer_urbangrill"
    const val ORDER_ID = "ord_7Hq2k9"
    const val QRIS_PAYMENT_ID = "pay_Q1x8"
    const val CARD_PAYMENT_ID = "pay_C7m2"

    val deviceIdentity = DeviceIdentity(serialNumber = SERIAL_NUMBER, model = "iMin Swan 2", osVersion = "14")

    /** The E1 request example. */
    fun activationRequest(appVersion: String = "0.2.0") =
        ActivationRequest(
            serialNumber = SERIAL_NUMBER,
            model = "iMin Swan 2",
            osVersion = "14",
            appVersion = appVersion,
        )

    private fun text(
        en: String,
        id: String,
    ) = LocalizedText(en = en, id = id)

    private fun rp(value: Long) = Rupiah(value)

    // E1–E4 -------------------------------------------------------------------------------------------------------

    val urbanGrill =
        Merchant(
            id = MERCHANT_ID,
            name = "Urban Grill",
            cuisine = text("Burgers & grill", "Burger & panggangan"),
            logoUrl = "https://cdn.example/mer_urbangrill/logo.png",
            photoUrl = "https://cdn.example/mer_urbangrill/cover.jpg",
            status = MerchantStatus.LIVE,
            isOpen = true,
        )

    private fun comingSoon(
        id: String,
        name: String,
        cuisine: LocalizedText,
    ) = Merchant(
        id = id,
        name = name,
        cuisine = cuisine,
        logoUrl = null,
        photoUrl = null,
        status = MerchantStatus.COMING_SOON,
        isOpen = false,
    )

    val merchants =
        listOf(
            urbanGrill,
            comingSoon("mer_bellapasta", "Bella Pasta", text("Italian kitchen", "Dapur Italia")),
            comingSoon("mer_sakura", "Sakura Ramen", text("Japanese noodles", "Mi Jepang")),
            comingSoon("mer_tacoloco", "Taco Loco", text("Mexican street food", "Jajanan Meksiko")),
            comingSoon("mer_freshbowl", "Fresh Bowl", text("Salads & healthy bowls", "Salad & mangkuk sehat")),
        )

    val activation =
        Activation(
            deviceId = DEVICE_ID,
            deviceToken = "dvt_9f2c4a7e1b8d4f0aa3c6e2b17d5f9e01",
            tokenExpiresAt = null,
            kioskCode = "KSK-0003",
            kioskName = "Kiosk 3",
            boundMerchantId = null,
            merchants = listOf(urbanGrill),
        )

    val binding =
        Binding(
            deviceId = DEVICE_ID,
            merchantId = MERCHANT_ID,
            kioskName = "Kiosk 3",
            boundAt = Instant.parse("2026-10-22T07:00:00Z"),
        )

    /** The `merchant_closed` scenario: Urban Grill closed until 10:00 in Jakarta (api-spec §4). */
    val urbanGrillClosed = urbanGrill.copy(isOpen = false, opensAt = Instant.parse("2026-10-23T03:00:00Z"))

    val merchantsWithClosed = listOf(urbanGrillClosed) + merchants.drop(1)

    // Local device state after E1 and E3 ------------------------------------------------------------------------

    val kioskDevice = KioskDevice(deviceId = DEVICE_ID, kioskCode = "KSK-0003", kioskName = "Kiosk 3")

    val boundMerchant = BoundMerchant(id = MERCHANT_ID, name = "Urban Grill")

    val activatedState = DeviceState.Activated(kioskDevice)

    val boundState = DeviceState.Bound(device = kioskDevice, merchant = boundMerchant, mode = KioskMode.SINGLE)

    // E5 ----------------------------------------------------------------------------------------------------------

    val config =
        MerchantConfig(
            configVersion = "cfg-42",
            merchantId = MERCHANT_ID,
            mode = KioskMode.SINGLE,
            brandName = "Urban Grill",
            accentColor = "#C8552B",
            logoUrl = "https://cdn.example/mer_urbangrill/logo.png",
            heroPhotoUrl = "https://cdn.example/mer_urbangrill/hero.jpg",
            isOpen = true,
            currency = "IDR",
            tax =
                TaxConfig(
                    label = text("Restaurant tax (PB1 10%)", "Pajak restoran (PB1 10%)"),
                    rateBasisPoints = 1000,
                    inclusive = false,
                ),
            rounding = RoundingConfig(allowedSteps = listOf(0, 100, 500), defaultStep = 0, mode = RoundingMode.UP),
            promos =
                listOf(
                    Promo(
                        id = "promo_10k_over_100k",
                        type = PromoType.THRESHOLD_FIXED,
                        threshold = rp(100_000),
                        amount = rp(10_000),
                        label =
                            text(
                                "Rp 10.000 off over Rp 100.000",
                                "Potongan Rp 10.000 untuk belanja di atas Rp 100.000",
                            ),
                    ),
                ),
            orderTypes = listOf(OrderType.DINE_IN, OrderType.TAKEAWAY),
            paymentMethods =
                listOf(
                    PaymentMethodConfig(
                        method = PaymentMethod.QRIS,
                        enabled = true,
                        qrisMerchantIdMasked = "ID10 •••• 4721",
                        qrExpirySecondsAllowed = listOf(180, 300, 600),
                        qrExpirySecondsDefault = 300,
                        terminal = null,
                    ),
                    PaymentMethodConfig(
                        method = PaymentMethod.CARD,
                        enabled = true,
                        qrisMerchantIdMasked = null,
                        qrExpirySecondsAllowed = emptyList(),
                        qrExpirySecondsDefault = null,
                        terminal = TerminalInfo(tid = "TID 0003 1182", connection = "USB"),
                    ),
                ),
            staffPin =
                StaffPinHash(
                    algorithm = "PBKDF2WithHmacSHA256",
                    iterations = 120_000,
                    salt = "b64:3q2+7w==",
                    hash = "b64:8Jw3k1m0…",
                    version = 3,
                ),
            receipt =
                ReceiptConfig(
                    address = "Jl. Sudirman No. 1, Jakarta",
                    npwp = null,
                    defaultFooter = text("Thank you for dining with us!", "Terima kasih telah makan bersama kami!"),
                ),
            upsell =
                UpsellConfig(
                    categoryIds = listOf("cat_sides", "cat_drinks", "cat_desserts"),
                    maxSuggestions = 4,
                ),
            budgetPlanner =
                BudgetPlannerConfig(
                    enabled = true,
                    overCapPercent = 10,
                    maxGroupSize = 10,
                    quickBudgets = listOf(rp(50_000), rp(100_000), rp(150_000), rp(200_000)),
                ),
            idlePromoImageUrls =
                listOf(
                    "https://cdn.example/mer_urbangrill/idle1.jpg",
                    "https://cdn.example/mer_urbangrill/idle2.jpg",
                    "https://cdn.example/mer_urbangrill/idle3.jpg",
                ),
        )

    /** The staff PIN in mocks and fakes (testing-plan §2.2). Never a real merchant PIN. */
    const val STAFF_PIN = "1234"

    // E6 ----------------------------------------------------------------------------------------------------------

    private fun option(
        id: String,
        en: String,
        idText: String,
        delta: Long,
        isDefault: Boolean,
    ) = ModifierOption(
        id = id,
        name = text(en, idText),
        priceDelta = rp(delta),
        isDefault = isDefault,
    )

    private fun levelOption(
        id: String,
        name: LocalizedText,
        note: LocalizedText,
        extraDelta: Long,
    ) = ModifierOption(
        id = id,
        name = name,
        note = note,
        levels =
            listOf(
                LevelPrice(Level.NONE, Rupiah.Zero),
                LevelPrice(Level.REGULAR, Rupiah.Zero),
                LevelPrice(Level.EXTRA, rp(extraDelta)),
            ),
        defaultLevel = Level.REGULAR,
    )

    private fun slot(
        id: String,
        en: String,
        idText: String,
        upcharge: Long,
    ) = ComboSlotOption(id = id, name = text(en, idText), upcharge = rp(upcharge), imageUrl = null)

    @Suppress("LongParameterList")
    private fun product(
        id: String,
        categoryId: String,
        name: LocalizedText,
        description: LocalizedText?,
        price: Long,
        kcal: Int,
        badge: Badge?,
        modifierGroupIds: List<String>,
        role: ProductRole,
        kidFriendly: Boolean,
        shareable: Boolean,
    ) = Product(
        id = id,
        categoryId = categoryId,
        name = name,
        description = description,
        price = rp(price),
        kcal = kcal,
        badge = badge,
        tags = null,
        imageUrl = null,
        available = true,
        modifierGroupIds = modifierGroupIds,
        role = role,
        kidFriendly = kidFriendly,
        servesAdults = 1,
        shareable = shareable,
        plannerWeight = 0,
    )

    val menu =
        Menu(
            menuVersion = "menu-118",
            categories =
                listOf(
                    Category("cat_burgers", text("Burgers", "Burger"), 1, "#E9DED0"),
                    Category("cat_chicken", text("Chicken", "Ayam"), 2, "#E3D4C2"),
                    Category("cat_bowls", text("Rice bowls", "Nasi mangkuk"), 3, "#EADCCB"),
                    Category("cat_sides", text("Sides", "Pendamping"), 5, "#E6DCD3"),
                    Category("cat_drinks", text("Drinks", "Minuman"), 6, "#DFE3DC"),
                    Category("cat_desserts", text("Desserts", "Pencuci mulut"), 7, "#EADCCB"),
                ),
            combosCategory = CombosCategory(text("Meals", "Paket"), 4),
            featuredProductIds = listOf("b2", "c1", "r1", "d1"),
            products =
                listOf(
                    product(
                        id = "b1",
                        categoryId = "cat_burgers",
                        name = text("Classic Cheeseburger", "Cheeseburger Klasik"),
                        description = null,
                        price = 38_000,
                        kcal = 540,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = true,
                        shareable = false,
                    ),
                    product(
                        id = "b2",
                        categoryId = "cat_burgers",
                        name = text("Smoky Double Burger", "Smoky Double Burger"),
                        description =
                            text(
                                "Two flame-grilled beef patties, smoked cheddar, pickles, onion and smoky house " +
                                    "sauce on a toasted sesame bun.",
                                "Dua patty sapi panggang, keju cheddar asap, acar, bawang, dan saus asap khas di " +
                                    "roti wijen panggang.",
                            ),
                        price = 52_000,
                        kcal = 780,
                        badge = Badge(BadgeType.NEW, text("New", "Baru")),
                        modifierGroupIds =
                            listOf(
                                "mg_b2_size",
                                "mg_meal_upgrade",
                                "mg_b2_ingredients",
                                "mg_burger_extras",
                            ),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = false,
                    ).copy(
                        tags = text("Beef · Contains gluten, dairy", "Sapi · Mengandung gluten, susu"),
                        plannerWeight = 5,
                    ),
                    product(
                        id = "b3",
                        categoryId = "cat_burgers",
                        name = text("Hot Honey Chicken Burger", "Burger Ayam Madu Pedas"),
                        description = null,
                        price = 42_000,
                        kcal = 620,
                        badge = Badge(BadgeType.SPICY, text("Spicy", "Pedas")),
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "b4",
                        categoryId = "cat_burgers",
                        name = text("BBQ Beef Bacon Burger", "Burger Beef Bacon BBQ"),
                        description = null,
                        price = 45_000,
                        kcal = 710,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "c1",
                        categoryId = "cat_chicken",
                        name = text("Crispy Chicken (2 pc)", "Ayam Krispi (2 pcs)"),
                        description = text("Original recipe", "Resep original"),
                        price = 38_000,
                        kcal = 560,
                        badge = Badge(BadgeType.BEST_SELLER, text("Best seller", "Terlaris")),
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "c2",
                        categoryId = "cat_chicken",
                        name = text("Spicy Wings (6 pc)", "Sayap Pedas (6 pcs)"),
                        description = null,
                        price = 36_000,
                        kcal = 480,
                        badge = Badge(BadgeType.SPICY, text("Spicy", "Pedas")),
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "c3",
                        categoryId = "cat_chicken",
                        name = text("Chicken Nuggets (9 pc)", "Nugget Ayam (9 pcs)"),
                        description = null,
                        price = 32_000,
                        kcal = 430,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = true,
                        shareable = false,
                    ),
                    product(
                        id = "c4",
                        categoryId = "cat_chicken",
                        name = text("Family Bucket (8 pc)", "Family Bucket (8 pcs)"),
                        description = null,
                        price = 129_000,
                        kcal = 2240,
                        badge = Badge(BadgeType.SHARE, text("Share", "Untuk berbagi")),
                        modifierGroupIds = listOf("mg_bucket_flavour"),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = true,
                    ).copy(servesAdults = 4),
                    product(
                        id = "r1",
                        categoryId = "cat_bowls",
                        name = text("Sambal Matah Rice Bowl", "Nasi Mangkuk Sambal Matah"),
                        description = null,
                        price = 34_000,
                        kcal = 610,
                        badge = Badge(BadgeType.LOCAL, text("Local", "Lokal")),
                        modifierGroupIds = listOf("mg_spice_level"),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "r2",
                        categoryId = "cat_bowls",
                        name = text("Teriyaki Chicken Bowl", "Nasi Mangkuk Ayam Teriyaki"),
                        description = null,
                        price = 32_000,
                        kcal = 590,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = true,
                        shareable = false,
                    ),
                    product(
                        id = "r3",
                        categoryId = "cat_bowls",
                        name = text("Ayam Geprek Bowl", "Nasi Mangkuk Ayam Geprek"),
                        description = null,
                        price = 30_000,
                        kcal = 640,
                        badge = Badge(BadgeType.SPICY, text("Spicy", "Pedas")),
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "r4",
                        categoryId = "cat_bowls",
                        name = text("Black Pepper Beef Bowl", "Nasi Mangkuk Sapi Lada Hitam"),
                        description = null,
                        price = 39_000,
                        kcal = 660,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.MAIN,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "s1",
                        categoryId = "cat_sides",
                        name = text("French Fries", "Kentang Goreng"),
                        description = text("Sea salt", "Garam laut"),
                        price = 18_000,
                        kcal = 320,
                        badge = null,
                        modifierGroupIds = listOf("mg_fries_size"),
                        role = ProductRole.SIDE,
                        kidFriendly = true,
                        shareable = true,
                    ),
                    product(
                        id = "s2",
                        categoryId = "cat_sides",
                        name = text("Cheese Fries", "Kentang Keju"),
                        description = text("Melted cheddar", "Cheddar leleh"),
                        price = 26_000,
                        kcal = 450,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.SIDE,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "s3",
                        categoryId = "cat_sides",
                        name = text("Chicken Nuggets (4 pc)", "Nugget Ayam (4 pcs)"),
                        description = null,
                        price = 16_000,
                        kcal = 190,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.SIDE,
                        kidFriendly = true,
                        shareable = false,
                    ),
                    product(
                        id = "s4",
                        categoryId = "cat_sides",
                        name = text("Spicy Wings (3 pc)", "Sayap Pedas (3 pcs)"),
                        description = null,
                        price = 19_000,
                        kcal = 240,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.SIDE,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "d1",
                        categoryId = "cat_drinks",
                        name = text("Iced Lychee Tea", "Es Teh Leci"),
                        description = null,
                        price = 16_000,
                        kcal = 140,
                        badge = null,
                        modifierGroupIds = listOf("mg_ice"),
                        role = ProductRole.DRINK,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "d2",
                        categoryId = "cat_drinks",
                        name = text("Cola (Regular)", "Cola (Reguler)"),
                        description = null,
                        price = 14_000,
                        kcal = 150,
                        badge = null,
                        modifierGroupIds = listOf("mg_cola_type"),
                        role = ProductRole.DRINK,
                        kidFriendly = true,
                        shareable = false,
                    ),
                    product(
                        id = "d3",
                        categoryId = "cat_drinks",
                        name = text("Iced Coffee Latte", "Es Kopi Latte"),
                        description = null,
                        price = 18_000,
                        kcal = 180,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.DRINK,
                        kidFriendly = false,
                        shareable = false,
                    ),
                    product(
                        id = "d4",
                        categoryId = "cat_drinks",
                        name = text("Mineral Water 600 ml", "Air Mineral 600 ml"),
                        description = null,
                        price = 8_000,
                        kcal = 0,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.DRINK,
                        kidFriendly = true,
                        shareable = false,
                    ),
                    product(
                        id = "e1",
                        categoryId = "cat_desserts",
                        name = text("Caramel Sundae", "Sundae Karamel"),
                        description = text("Soft serve", "Es krim lembut"),
                        price = 15_000,
                        kcal = 330,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.DESSERT,
                        kidFriendly = true,
                        shareable = false,
                    ),
                    product(
                        id = "e2",
                        categoryId = "cat_desserts",
                        name = text("Chocolate Sundae", "Sundae Cokelat"),
                        description = null,
                        price = 15_000,
                        kcal = 350,
                        badge = null,
                        modifierGroupIds = emptyList(),
                        role = ProductRole.DESSERT,
                        kidFriendly = true,
                        shareable = false,
                    ),
                    product(
                        id = "e3",
                        categoryId = "cat_desserts",
                        name = text("Pandan Soft Serve", "Es Krim Pandan"),
                        description = text("Local favourite", "Favorit lokal"),
                        price = 12_000,
                        kcal = 210,
                        badge = Badge(BadgeType.LOCAL, text("Local", "Lokal")),
                        modifierGroupIds = emptyList(),
                        role = ProductRole.DESSERT,
                        kidFriendly = true,
                        shareable = false,
                    ),
                ),
            modifierGroups =
                listOf(
                    ModifierGroup(
                        id = "mg_b2_size",
                        name = text("Choose a size", "Pilih ukuran"),
                        type = ModifierType.SINGLE,
                        required = true,
                        min = 1,
                        max = 1,
                        options =
                            listOf(
                                option("opt_b2_regular", "Regular", "Reguler", 0, isDefault = false)
                                    .copy(description = text("Two patties", "Dua patty")),
                                option("opt_b2_large", "Large", "Besar", 6_000, isDefault = false)
                                    .copy(description = text("Three patties", "Tiga patty")),
                            ),
                    ),
                    ModifierGroup(
                        id = "mg_meal_upgrade",
                        name = text("Make it a meal?", "Jadikan paket?"),
                        purpose = ModifierPurpose.MEAL_UPGRADE,
                        type = ModifierType.SINGLE,
                        required = true,
                        min = 1,
                        max = 1,
                        options =
                            listOf(
                                option("opt_burger_only", "Burger only", "Burger saja", 0, isDefault = true)
                                    .copy(description = text("Just the burger", "Hanya burger")),
                                option("opt_make_meal", "Make it a meal", "Jadikan paket", 18_000, isDefault = false)
                                    .copy(description = text("Adds fries & a drink", "Tambah kentang & minuman")),
                            ),
                    ),
                    ModifierGroup(
                        id = "mg_b2_ingredients",
                        name = text("Customize", "Sesuaikan"),
                        type = ModifierType.LEVEL,
                        required = false,
                        min = null,
                        max = null,
                        options =
                            listOf(
                                levelOption(
                                    "ing_cheddar",
                                    text("Smoked cheddar", "Cheddar asap"),
                                    text("Extra +Rp 5.000", "Ekstra +Rp 5.000"),
                                    5_000,
                                ),
                                levelOption(
                                    "ing_onion",
                                    text("Onion", "Bawang"),
                                    text("Fresh, diced", "Segar, dipotong dadu"),
                                    0,
                                ),
                                levelOption("ing_pickles", text("Pickles", "Acar"), text("Dill", "Dill"), 0),
                                levelOption(
                                    "ing_sauce",
                                    text("Smoky sauce", "Saus asap"),
                                    text("House recipe", "Resep khas"),
                                    0,
                                ),
                            ),
                    ),
                    ModifierGroup(
                        id = "mg_burger_extras",
                        name = text("Add extras", "Tambahan"),
                        type = ModifierType.MULTI,
                        required = false,
                        min = 0,
                        max = 3,
                        options =
                            listOf(
                                option("ext_bacon", "Beef bacon", "Beef bacon", 8_000, isDefault = false),
                                option("ext_egg", "Fried egg", "Telur goreng", 6_000, isDefault = false),
                                option("ext_jala", "Jalapeños", "Jalapeño", 4_000, isDefault = false),
                            ),
                    ),
                    ModifierGroup(
                        id = "mg_fries_size",
                        name = text("Size", "Ukuran"),
                        type = ModifierType.SINGLE,
                        required = true,
                        min = 1,
                        max = 1,
                        options =
                            listOf(
                                option("opt_regular", "Regular", "Reguler", 0, isDefault = true),
                                option("opt_large", "Large", "Besar", 4_000, isDefault = false),
                            ),
                    ),
                    ModifierGroup(
                        id = "mg_ice",
                        name = text("Ice", "Es"),
                        type = ModifierType.SINGLE,
                        required = true,
                        min = 1,
                        max = 1,
                        options =
                            listOf(
                                option("opt_ice_regular", "Regular ice", "Es normal", 0, isDefault = true),
                                option("opt_ice_less", "Less ice", "Sedikit es", 0, isDefault = false),
                            ),
                    ),
                    ModifierGroup(
                        id = "mg_bucket_flavour",
                        name = text("Choose a flavour", "Pilih rasa"),
                        type = ModifierType.SINGLE,
                        required = true,
                        min = 1,
                        max = 1,
                        options =
                            listOf(
                                option("opt_flavour_original", "Original", "Original", 0, isDefault = false),
                                option("opt_flavour_spicy", "Spicy", "Pedas", 0, isDefault = false),
                                option("opt_flavour_half", "Half & half", "Setengah-setengah", 0, isDefault = false),
                            ),
                    ),
                    ModifierGroup(
                        id = "mg_spice_level",
                        name = text("Spice level", "Tingkat pedas"),
                        type = ModifierType.SINGLE,
                        required = true,
                        min = 1,
                        max = 1,
                        options =
                            listOf(
                                option("opt_spice_mild", "Mild", "Tidak pedas", 0, isDefault = false),
                                option("opt_spice_medium", "Medium", "Sedang", 0, isDefault = false),
                                option("opt_spice_hot", "Hot", "Pedas", 0, isDefault = false),
                            ),
                    ),
                    ModifierGroup(
                        id = "mg_cola_type",
                        name = text("Choose a cola", "Pilih cola"),
                        type = ModifierType.SINGLE,
                        required = true,
                        min = 1,
                        max = 1,
                        options =
                            listOf(
                                option("opt_cola_classic", "Classic", "Klasik", 0, isDefault = false),
                                option("opt_cola_zero", "Zero sugar", "Tanpa gula", 0, isDefault = false),
                            ),
                    ),
                ),
            combos =
                listOf(
                    Combo(
                        id = "combo_burger",
                        name = text("Cheeseburger Meal", "Paket Cheeseburger"),
                        shortName = text("Cheeseburger Meal", "Paket Cheeseburger"),
                        includes = text("Cheeseburger, side, drink", "Cheeseburger, pendamping, minuman"),
                        price = rp(56_000),
                        separatePrice = rp(70_000),
                        serves = 1,
                        imageUrl = null,
                        tone = "#E9DED0",
                        available = true,
                    ),
                    Combo(
                        id = "combo_chicken",
                        name = text("Crispy Chicken Meal", "Paket Ayam Krispi"),
                        shortName = text("Chicken Meal", "Paket Ayam"),
                        includes = text("2 pc chicken, side, drink", "2 pcs ayam, pendamping, minuman"),
                        price = rp(59_000),
                        separatePrice = rp(70_000),
                        serves = 1,
                        imageUrl = null,
                        tone = "#E3D4C2",
                        available = true,
                    ),
                    Combo(
                        id = "combo_bowl",
                        name = text("Rice Bowl Combo", "Paket Nasi Mangkuk"),
                        shortName = text("Rice Bowl Combo", "Paket Nasi Mangkuk"),
                        includes = text("Sambal matah bowl, side, drink", "Nasi sambal matah, pendamping, minuman"),
                        price = rp(52_000),
                        separatePrice = rp(64_000),
                        serves = 1,
                        imageUrl = null,
                        tone = "#EADCCB",
                        available = true,
                    ),
                    Combo(
                        id = "combo_feast",
                        name = text("Feast for Two", "Paket Berdua"),
                        shortName = text("Feast for Two", "Paket Berdua"),
                        includes = text("2 burgers, 2 sides, 2 drinks", "2 burger, 2 pendamping, 2 minuman"),
                        price = rp(109_000),
                        separatePrice = rp(140_000),
                        serves = 2,
                        imageUrl = null,
                        tone = "#E6DCD3",
                        available = true,
                    ),
                ),
            comboSlots =
                ComboSlots(
                    sides =
                        listOf(
                            slot("cs_fries", "Fries", "Kentang goreng", 0),
                            slot("cs_cheese", "Cheese fries", "Kentang keju", 8_000),
                            slot("cs_nuggets", "Nuggets", "Nugget", 4_000),
                            slot("cs_corn", "Corn cup", "Jagung cup", 0),
                        ),
                    drinks =
                        listOf(
                            slot("cd_cola", "Cola", "Cola", 0),
                            slot("cd_tea", "Lychee tea", "Teh leci", 2_000),
                            slot("cd_coffee", "Iced latte", "Es latte", 4_000),
                            slot("cd_water", "Water", "Air mineral", 0),
                        ),
                    sizeLargeUpcharge = rp(6_000),
                ),
        )

    /** E5 and E6 together, as the cache serves them (FR-028). */
    val catalog = Catalog(config = config, menu = menu)

    /**
     * [menu] as an API without the optional `purpose` on modifier groups sends it (api-spec E6, OQ-17): no group is
     * marked, so nothing counts as a meal but a combo and there is no "Make it a meal" card (A-48).
     */
    val menuWithoutPurposes: Menu =
        menu.copy(modifierGroups = menu.modifierGroups.map { it.copy(purpose = null) })

    /** [catalog] over [menuWithoutPurposes]. */
    val catalogWithoutPurposes: Catalog = catalog.copy(menu = menuWithoutPurposes)

    // E7–E8 -------------------------------------------------------------------------------------------------------

    val totals =
        Totals(
            subtotal = rp(167_000),
            discount = rp(10_000),
            promoId = "promo_10k_over_100k",
            tax = rp(15_700),
            rounding = Rupiah.Zero,
            total = rp(172_700),
        )

    val orderRequest =
        OrderRequest(
            merchantId = MERCHANT_ID,
            orderType = OrderType.DINE_IN,
            menuVersion = "menu-118",
            configVersion = "cfg-42",
            roundingStep = 0,
            lines =
                listOf(
                    OrderLine.ProductLine(
                        lineId = "l1",
                        productId = "b2",
                        quantity = 1,
                        selections =
                            listOf(
                                Selection(groupId = "mg_b2_size", optionId = "opt_b2_regular"),
                                Selection(groupId = "mg_meal_upgrade", optionId = "opt_make_meal"),
                                Selection(groupId = "mg_b2_ingredients", optionId = "ing_cheddar", level = Level.EXTRA),
                                Selection(groupId = "mg_b2_ingredients", optionId = "ing_onion", level = Level.NONE),
                            ),
                        unitPrice = rp(75_000),
                        lineTotal = rp(75_000),
                    ),
                    OrderLine.ProductLine("l2", "c1", 1, emptyList(), rp(38_000), rp(38_000)),
                    OrderLine.ProductLine(
                        lineId = "l3",
                        productId = "s1",
                        quantity = 1,
                        selections = listOf(Selection(groupId = "mg_fries_size", optionId = "opt_large")),
                        unitPrice = rp(22_000),
                        lineTotal = rp(22_000),
                    ),
                    OrderLine.ProductLine(
                        lineId = "l4",
                        productId = "d1",
                        quantity = 2,
                        selections = listOf(Selection(groupId = "mg_ice", optionId = "opt_ice_less")),
                        unitPrice = rp(16_000),
                        lineTotal = rp(32_000),
                    ),
                ),
            clientTotals = totals,
        )

    /** The combo line example of api-spec E7. */
    val comboLine =
        OrderLine.ComboLine(
            lineId = "l5",
            comboId = "combo_burger",
            quantity = 1,
            selection = ComboSelection(sideId = "cs_cheese", drinkId = "cd_coffee", size = ComboSize.LARGE),
            unitPrice = rp(74_000),
            lineTotal = rp(74_000),
        )

    val createdOrder =
        CreatedOrder(
            orderId = ORDER_ID,
            orderNumber = "A247",
            status = OrderStatus.AWAITING_PAYMENT,
            createdAt = Instant.parse("2026-10-22T07:15:30Z"),
            totals = totals,
            lines =
                listOf(
                    OrderLineSummary(
                        lineId = "l1",
                        description =
                            text(
                                "Fries · Cola · Extra cheddar · No onion",
                                "Kentang · Cola · Ekstra cheddar · Tanpa bawang",
                            ),
                        lineTotal = rp(75_000),
                    ),
                ),
        )

    /** Server totals in the E7 `PRICE_CHANGED` example. */
    val changedTotals =
        Totals(
            subtotal = rp(169_000),
            discount = rp(10_000),
            promoId = null,
            tax = rp(15_900),
            rounding = Rupiah.Zero,
            total = rp(174_900),
        )

    val cancelledOrder = CancelledOrder(orderId = ORDER_ID, status = OrderStatus.CANCELLED)

    // E9–E12 ------------------------------------------------------------------------------------------------------

    val qrisPayment =
        StartedPayment.Qris(
            paymentId = QRIS_PAYMENT_ID,
            status = PaymentStatus.PENDING,
            amount = rp(172_700),
            qrString =
                "00020101021226650013ID.CO.EXAMPLE.WWW0118936000000000000000303UMI51440014ID.CO.QRIS.WWW0215" +
                    "ID1020000004721520458125303360540617270058",
            expiresAt = Instant.parse("2026-10-22T07:20:31Z"),
            createdAt = Instant.parse("2026-10-22T07:15:31Z"),
        )

    val cardPayment =
        StartedPayment.Card(
            paymentId = CARD_PAYMENT_ID,
            status = PaymentStatus.PENDING,
            amount = rp(172_700),
            terminalRef = "TRX-20261022-000247",
            terminal = TerminalInfo(tid = "TID 0003 1182", connection = "USB"),
            timeoutSeconds = 90,
            createdAt = Instant.parse("2026-10-22T07:15:31Z"),
        )

    val paymentPending =
        PaymentState(
            paymentId = QRIS_PAYMENT_ID,
            method = PaymentMethod.QRIS,
            status = PaymentStatus.PENDING,
            amount = rp(172_700),
            expiresAt = Instant.parse("2026-10-22T07:20:31Z"),
        )

    val paymentPaid =
        PaymentState(
            paymentId = QRIS_PAYMENT_ID,
            method = PaymentMethod.QRIS,
            status = PaymentStatus.PAID,
            amount = rp(172_700),
            paidAt = Instant.parse("2026-10-22T07:16:02Z"),
            reference = "QR-8812-4471-0091",
            issuer = "GoPay",
            paidOrder = PaidOrder(orderId = ORDER_ID, orderNumber = "A247", status = OrderStatus.PAID),
        )

    val paymentDeclined =
        PaymentState(
            paymentId = CARD_PAYMENT_ID,
            method = PaymentMethod.CARD,
            status = PaymentStatus.FAILED,
            amount = rp(172_700),
            failureReason = PaymentFailureReason.DECLINED,
            message = text("Your bank declined the card.", "Bank Anda menolak kartu."),
        )

    val paymentCancelled = PaymentUpdate(paymentId = QRIS_PAYMENT_ID, status = PaymentStatus.CANCELLED)

    val terminalResult =
        TerminalResult(
            approved = true,
            terminalRef = "TRX-20261022-000247",
            tid = "TID 0003 1182",
            approvalCode = "123456",
            cardBrand = "VISA",
            maskedPan = "•••• 4821",
            rawResponseCode = "00",
        )

    val terminalRelayed = PaymentUpdate(paymentId = CARD_PAYMENT_ID, status = PaymentStatus.PROCESSING)

    // E13–E14 -----------------------------------------------------------------------------------------------------

    val newStaffPin =
        StaffPinHash(
            algorithm = "PBKDF2WithHmacSHA256",
            iterations = 120_000,
            salt = "b64:Zm9v",
            hash = "b64:YmFy…",
            version = 4,
        )

    val health =
        Health(
            status = HealthStatus.OK,
            serverTime = Instant.parse("2026-10-22T07:15:30Z"),
            api = HealthStatus.OK,
            database = HealthStatus.OK,
            paymentGateway = HealthStatus.OK,
        )

    /** E14 with the backend up and the gateway slow or partly failing: checkout is unavailable (api-spec E14). */
    val healthGatewayDegraded = health.copy(paymentGateway = HealthStatus.DEGRADED)

    /** E14 with the backend up and the gateway down: checkout is unavailable (api-spec E14). */
    val healthGatewayDown = health.copy(paymentGateway = HealthStatus.DOWN)

    /** E14 with the backend itself degraded: checkout is unavailable even though the gateway is `OK`. */
    val healthBackendDegraded = health.copy(status = HealthStatus.DEGRADED, api = HealthStatus.DEGRADED)
}
