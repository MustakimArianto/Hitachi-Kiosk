package com.hitachi.kiosk.core.testing.fixture

/**
 * The JSON examples of `docs/api/api-spec.md`, extracted to `src/main/resources/fixtures/<name>.json` by
 * `./gradlew extractApiFixtures` and checked by `checkApiFixtures` (DEV-006, technical-design §14, TR-NET-02).
 * Names are listed in `docs/api/tools/spec_examples.py`, for example `e7_request` or `e10_response_paid`.
 */
object Fixtures {
    /** The fixture [name] (without `.json`), exactly as written in the spec. */
    fun read(name: String): String {
        val path = "fixtures/$name.json"
        val stream =
            Fixtures::class.java.classLoader.getResourceAsStream(path)
                ?: error("No API fixture '$name' on the classpath ($path). See docs/api/tools/spec_examples.py.")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
