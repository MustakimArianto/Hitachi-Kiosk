package com.hitachi.kiosk.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.api.ApiConfig
import com.hitachi.kiosk.core.data.api.ApiErrorMapper
import com.hitachi.kiosk.core.data.api.BackendHttp
import com.hitachi.kiosk.core.data.api.InMemoryDeviceCredentialsStore
import com.hitachi.kiosk.core.data.api.NetworkTimeouts
import com.hitachi.kiosk.core.data.backend.DefaultBackendApi
import com.hitachi.kiosk.core.data.fixtureResponse
import com.hitachi.kiosk.core.data.jsonResponse
import com.hitachi.kiosk.core.data.prefs.InMemoryTokenStore
import com.hitachi.kiosk.core.data.prefs.newDeviceStore
import com.hitachi.kiosk.core.data.sync.DefaultCatalogSyncRepository
import com.hitachi.kiosk.core.data.takeRequestNow
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.BindResult
import com.hitachi.kiosk.core.testing.fake.FakeIdGenerator
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Activation and binding over HTTP against the api-spec examples (DEV-014, DEV-016; TC-008–TC-013, TC-016,
 * TC-017): the real client, error mapping, repositories and 401 handling, with in-memory token storage.
 */
class SetupIntegrationTest {
    @get:Rule
    val rule = MockWebServerRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val server get() = rule.server
    private val tokens = InMemoryTokenStore()
    private val credentials = InMemoryDeviceCredentialsStore()
    private val session by lazy { DeviceSession(tokens, newDeviceStore(folder.root), credentials) }
    private val config by lazy {
        ApiConfig(
            baseUrl = server.url("/").toString(),
            environment = "mock",
            certificatePins = emptyList(),
            appVersion = "0.2.0",
            allowCleartext = true,
        )
    }
    private val backend by lazy {
        val timeouts =
            NetworkTimeouts(
                connect = 500.milliseconds,
                read = 300.milliseconds,
                write = 300.milliseconds,
                call = 2_000.milliseconds,
            )
        val client = BackendHttp.client(config, credentials, timeouts = timeouts)
        DefaultBackendApi(BackendHttp.retrofit(config.baseUrl, client), ApiErrorMapper(SessionInvalidator(session)))
    }
    private val activation by lazy { DefaultActivationRepository(backend, session, config) }
    private val merchants by lazy {
        DefaultMerchantRepository(backend, session, DefaultCatalogSyncRepository(backend), FakeIdGenerator())
    }

    private fun envelope(code: String) = """{ "code": "$code", "message": "m", "details": {} }"""

    @Test
    fun `a registered serial number activates the kiosk and later calls carry the token`() =
        runTest {
            server.enqueue(fixtureResponse("e1_response"))
            server.enqueue(fixtureResponse("e2_response"))

            assertThat(activation.activate(SampleData.deviceIdentity)).isEqualTo(Outcome.Success(SampleData.activation))
            assertThat(merchants.merchants()).isEqualTo(Outcome.Success(SampleData.merchants))

            val activate = server.takeRequestNow()
            assertThat(activate.headers["Authorization"]).isNull()
            val list = server.takeRequestNow()
            assertThat(list.url.encodedPath).isEqualTo("/v1/devices/${SampleData.DEVICE_ID}/merchants")
            assertThat(list.headers["Authorization"]).isEqualTo("Bearer ${SampleData.activation.deviceToken}")
            assertThat(list.headers["X-Device-Id"]).isEqualTo(SampleData.DEVICE_ID)
        }

    @Test
    fun `an unregistered serial number is reported with its code`() =
        runTest {
            server.enqueue(fixtureResponse("e1_error_not_registered", 404))

            assertThat(activation.activate(SampleData.deviceIdentity))
                .isEqualTo(Outcome.Failure(DomainError.Server(code = "DEVICE_NOT_REGISTERED", httpStatus = 404)))
            assertThat(activation.state.value).isEqualTo(DeviceState.NotActivated)
        }

    @Test
    fun `a disabled device is reported with its code`() =
        runTest {
            server.enqueue(jsonResponse(envelope("DEVICE_DISABLED"), 403))

            assertThat(activation.activate(SampleData.deviceIdentity))
                .isEqualTo(Outcome.Failure(DomainError.Server(code = "DEVICE_DISABLED", httpStatus = 403)))
        }

    @Test
    fun `an activation timeout is a network error and stores nothing`() =
        runTest {
            server.enqueue(fixtureResponse("e1_response").newBuilder().headersDelay(2, TimeUnit.SECONDS).build())

            assertThat(activation.activate(SampleData.deviceIdentity))
                .isEqualTo(Outcome.Failure(DomainError.Network(isTimeout = true)))
            assertThat(tokens.token).isNull()
        }

    @Test
    fun `a 401 later wipes the token and binding`() =
        runTest {
            server.enqueue(fixtureResponse("e1_response"))
            server.enqueue(fixtureResponse("e3_response"))
            server.enqueue(fixtureResponse("e5_response", 200, "ETag" to "\"cfg-42\""))
            server.enqueue(fixtureResponse("e6_response", 200, "ETag" to "\"menu-118\""))
            server.enqueue(jsonResponse(envelope("TOKEN_INVALID"), 401))
            activation.activate(SampleData.deviceIdentity)
            assertThat(merchants.bind(SampleData.urbanGrill)).isEqualTo(BindResult.Bound(SampleData.boundState))

            // TC-013: the token is revoked in the dashboard; the next call answers 401.
            assertThat(merchants.merchants()).isEqualTo(Outcome.Failure(DomainError.Unauthorized))

            assertThat(activation.state.value).isEqualTo(DeviceState.NotActivated)
            assertThat(tokens.token).isNull()
            assertThat(credentials.current()).isNull()
            assertThat(session.load()).isEqualTo(DeviceState.NotActivated)
        }

    @Test
    fun `a menu download failure after binding keeps the kiosk on SCR-03`() =
        runTest {
            server.enqueue(fixtureResponse("e1_response"))
            server.enqueue(fixtureResponse("e3_response"))
            server.enqueue(fixtureResponse("e5_response"))
            repeat(4) { server.enqueue(jsonResponse(envelope("SERVER_ERROR"), 503)) }
            activation.activate(SampleData.deviceIdentity)

            val result = merchants.bind(SampleData.urbanGrill)

            assertThat(result).isEqualTo(BindResult.MenuDownloadFailed(DomainError.Server("SERVER_ERROR", 503)))
            assertThat(session.load()).isEqualTo(SampleData.activatedState)
        }
}
