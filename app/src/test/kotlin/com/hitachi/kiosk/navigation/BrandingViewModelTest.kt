package com.hitachi.kiosk.navigation

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.testing.fake.FakeCatalogRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The theme accent follows config `accentColor` (A-38). */
class BrandingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()

    @Test
    fun `the accent comes from the cached config, and none without a catalog`() =
        runTest(main.dispatcher) {
            val vm = BrandingViewModel(catalog)
            backgroundScope.launch { vm.accent.collect {} }
            runCurrent()
            assertThat(vm.accent.value).isEqualTo(Color(0xFFC8552B))

            catalog.set(SampleData.catalog.copy(config = SampleData.config.copy(accentColor = "#2F5D4A")))
            runCurrent()
            assertThat(vm.accent.value).isEqualTo(Color(0xFF2F5D4A))

            catalog.set(null)
            runCurrent()
            assertThat(vm.accent.value).isNull()
        }
}
