package com.hitachi.kiosk.core.domain.testing

import java.io.File

/**
 * Reads a golden case table from `docs/` (`pricing-cases.md`, `budget-plan-cases.md`). The Gradle test task passes
 * the folder as the `kiosk.docsDir` system property and declares the tables as inputs, so an edited row reruns the
 * tests.
 */
internal object CaseTable {
    /** The data rows of the first Markdown table in [fileName], as trimmed cells, without the header rows. */
    fun rows(fileName: String): List<List<String>> {
        val docsDir = requireNotNull(System.getProperty("kiosk.docsDir")) { "kiosk.docsDir is not set" }
        return File(docsDir, fileName)
            .readLines()
            .filter { it.startsWith("|") }
            .map { line ->
                line
                    .trim()
                    .removePrefix("|")
                    .removeSuffix("|")
                    .split("|")
                    .map(String::trim)
            }.drop(HEADER_ROWS)
    }

    /** `172.700` → 172700; `—` and blank cells are 0 (BR-01 format). */
    fun amount(cell: String): Long = cell.replace(".", "").takeUnless { it.isBlank() || it == "—" }?.toLong() ?: 0L

    private const val HEADER_ROWS = 2
}
