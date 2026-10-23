package com.hitachi.kiosk.core.data.backend

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.BackendHarness
import com.hitachi.kiosk.core.data.takeRequestNow
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/** E5/E6 send `If-None-Match`; a 304 keeps the cached copy (TR-NET-09, technical-design §5.4). */
class ConditionalGetTest {
    @get:Rule
    val rule = MockWebServerRule()

    private val server get() = rule.server
    private val harness by lazy { BackendHarness(server) }

    @Test
    fun `E5 sends If-None-Match and maps 304 to NotModified`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(304)
                    .addHeader("ETag", "\"cfg-42\"")
                    .build(),
            )

            val result = harness.api.config(SampleData.MERCHANT_ID, etag = "\"cfg-42\"")

            assertThat(result).isEqualTo(Outcome.Success(Conditional.NotModified))
            assertThat(server.takeRequestNow().headers["If-None-Match"]).isEqualTo("\"cfg-42\"")
        }

    @Test
    fun `E6 sends If-None-Match and maps 304 to NotModified`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(304).build())

            val result = harness.api.menu(SampleData.MERCHANT_ID, etag = "\"menu-118\"")

            assertThat(result).isEqualTo(Outcome.Success(Conditional.NotModified))
            assertThat(server.takeRequestNow().headers["If-None-Match"]).isEqualTo("\"menu-118\"")
        }
}
