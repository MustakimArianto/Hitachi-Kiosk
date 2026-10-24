package com.hitachi.kiosk.navigation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.ui.parseHexColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The merchant branding for the theme root (A-38, architecture §12), from the cached catalog's config: the `accentColor`,
 * or `null` (the default paprika) before a merchant is bound or when the value is malformed, and the `logoUrl`.
 */
@HiltViewModel
class BrandingViewModel
    @Inject
    constructor(
        catalog: CatalogRepository,
    ) : ViewModel() {
        val accent: StateFlow<Color?> =
            catalog.catalog
                .map { parseHexColor(it?.config?.accentColor) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        val logoUrl: StateFlow<String?> =
            catalog.catalog
                .map { it?.config?.logoUrl }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
