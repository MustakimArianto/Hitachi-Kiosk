# R8 rules for the release build (technical-design §2.5).
# kotlinx.serialization, Retrofit, OkHttp and Hilt ship their own consumer rules. Navigation routes are
# @Serializable objects; their generated serializers are kept by the kotlinx.serialization rules.

# Readable crash traces and logs from release builds.
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Diagnostics log these types by simpleName (payment_state, order_recovery, budget_plan_unhandled_event). Without these
# rules R8 renames them and a release log reads "state=do2". Only the names are kept, so shrinking and optimising still apply.
-keepnames class com.hitachi.kiosk.core.domain.payment.PaymentUiState
-keepnames class com.hitachi.kiosk.core.domain.payment.PaymentUiState$*
-keepnames class com.hitachi.kiosk.core.domain.order.RecoveryOutcome
-keepnames class com.hitachi.kiosk.core.domain.order.RecoveryOutcome$*
-keepnames class com.hitachi.kiosk.feature.budgetplan.BudgetPlanEvent
-keepnames class com.hitachi.kiosk.feature.budgetplan.BudgetPlanEvent$*
