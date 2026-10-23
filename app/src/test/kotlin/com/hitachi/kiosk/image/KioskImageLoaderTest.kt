package com.hitachi.kiosk.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** NFR-013, A-37, TC-157: images use a disk cache capped at 200 MB in `cacheDir/images`. */
@RunWith(RobolectricTestRunner::class)
class KioskImageLoaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the disk cache is capped at 200 MB`() {
        val diskCache = requireNotNull(kioskImageLoader(context).diskCache)

        assertThat(diskCache.maxSize).isEqualTo(200L * 1024 * 1024)
        assertThat(diskCache.directory.toFile()).isEqualTo(context.cacheDir.resolve("images"))
    }

    @Test
    fun `decoded images have a memory cache`() {
        assertThat(kioskImageLoader(context).memoryCache).isNotNull()
    }
}
