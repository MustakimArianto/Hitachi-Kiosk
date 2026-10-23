package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.image.ImagePrefetcher

/** Records the URLs an [ImagePrefetcher] was asked to cache (NFR-012). */
class FakeImagePrefetcher : ImagePrefetcher {
    private val recorded = mutableListOf<List<String>>()

    /** One entry per [prefetch] call. */
    val requests: List<List<String>> get() = synchronized(recorded) { recorded.toList() }

    override fun prefetch(urls: Collection<String>) {
        synchronized(recorded) { recorded += urls.toList() }
    }
}
