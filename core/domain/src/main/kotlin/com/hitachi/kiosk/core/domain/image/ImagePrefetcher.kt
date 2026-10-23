package com.hitachi.kiosk.core.domain.image

/**
 * Puts images in the disk cache ahead of use, so the menu shows them offline (FR-028, NFR-013). `:app` implements
 * it with Coil, so neither `:core:domain` nor `:core:data` depends on an image library (technical-design §7.1).
 */
fun interface ImagePrefetcher {
    /** Starts downloading [urls] into the disk cache. Returns at once; failures are only logged. */
    fun prefetch(urls: Collection<String>)
}
