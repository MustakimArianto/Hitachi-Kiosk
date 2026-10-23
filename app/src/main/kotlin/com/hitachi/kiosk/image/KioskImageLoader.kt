package com.hitachi.kiosk.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/** Disk cache for menu and promo images: 200 MB (A-37, NFR-013, TR-DAT-05). */
const val IMAGE_DISK_CACHE_BYTES: Long = 200L * 1024 * 1024

/** Share of the app's memory for decoded images (technical-design §4.1). */
const val IMAGE_MEMORY_CACHE_PERCENT: Double = 0.2

/**
 * The app's Coil [ImageLoader] (technical-design §4.1): a 200 MB disk cache in `cacheDir/images`, a memory cache of
 * 20 % of app memory, and a plain OkHttp client. Image URLs are public CDN links, so the backend client with its
 * device token is never used for them.
 */
fun kioskImageLoader(context: Context): ImageLoader =
    ImageLoader
        .Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, IMAGE_MEMORY_CACHE_PERCENT).build() }
        .diskCache {
            DiskCache
                .Builder()
                .directory(context.cacheDir.resolve("images"))
                .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                .build()
        }.build()
