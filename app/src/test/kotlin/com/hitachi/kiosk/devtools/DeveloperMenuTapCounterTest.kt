package com.hitachi.kiosk.devtools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeveloperMenuTapCounterTest {
    @Test
    fun `the fifth tap opens developer options and the count starts again`() {
        val counter = DeveloperMenuTapCounter()

        val firstRound = List(5) { counter.onTap() }
        val secondRound = List(5) { counter.onTap() }

        assertThat(firstRound).containsExactly(false, false, false, false, true).inOrder()
        assertThat(secondRound).containsExactly(false, false, false, false, true).inOrder()
    }
}
