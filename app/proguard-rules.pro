# R8 rules for the release build (technical-design §2.5).
# kotlinx.serialization, Retrofit, OkHttp and Hilt ship their own consumer rules. Navigation routes are
# @Serializable objects; their generated serializers are kept by the kotlinx.serialization rules.
