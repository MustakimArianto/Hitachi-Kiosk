package com.hitachi.kiosk.image

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.hitachi.kiosk.core.domain.image.ImagePrefetcher
import com.hitachi.kiosk.core.domain.log.KioskLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Downloads menu images into Coil's disk cache after a sync (FR-028, NFR-013, technical-design §7.1), skipping the
 * memory cache so nothing is decoded until a screen shows it. Requests run on Coil's own threads.
 */
class CoilImagePrefetcher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ImagePrefetcher {
        override fun prefetch(urls: Collection<String>) {
            if (urls.isEmpty()) return
            val loader = SingletonImageLoader.get(context)
            urls.forEach { url ->
                loader.enqueue(
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build(),
                )
            }
            KioskLog.i("images_prefetch", "count" to urls.size)
        }
    }
