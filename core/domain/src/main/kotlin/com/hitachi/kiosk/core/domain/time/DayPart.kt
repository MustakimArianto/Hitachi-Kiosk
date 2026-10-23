package com.hitachi.kiosk.core.domain.time

/** The part of the day the Menu greeting uses (FR-009, A-07). */
enum class DayPart {
    /** 05:00–10:59: "Good morning". */
    MORNING,

    /** 11:00–16:59: "Good afternoon". */
    AFTERNOON,

    /** 17:00–04:59: "Good evening". */
    EVENING,

    ;

    companion object {
        private const val MORNING_START = 5
        private const val AFTERNOON_START = 11
        private const val EVENING_START = 17
        private val HOURS = 0..23

        /** The part of the day at local [hour] (0–23), per A-07. */
        fun at(hour: Int): DayPart {
            require(hour in HOURS) { "hour must be 0–23, was $hour" }
            return when (hour) {
                in MORNING_START until AFTERNOON_START -> MORNING
                in AFTERNOON_START until EVENING_START -> AFTERNOON
                else -> EVENING
            }
        }
    }
}
