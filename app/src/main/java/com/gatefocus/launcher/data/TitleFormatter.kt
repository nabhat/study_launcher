package com.gatefocus.launcher.data

/**
 * Cleans noisy lecture filenames for display without modifying files on disk
 * and without hardcoding channel, exam, or subject names.
 */
object TitleFormatter {
    // Trailing bracketed media IDs commonly appended by downloaders (e.g., [dbdqwXhiVsg]).
    private val TRAILING_ID_REGEX = Regex("""\s*[\[\【][A-Za-z0-9_-]{4,24}[\]\】]\s*$""")

    /**
     * Strips trailing bracketed IDs and pipe-delimited metadata suffixes while
     * preserving single pipes inside parentheses (e.g., conditional probability `P(A | B)`).
     */
    fun cleanLectureTitle(rawTitle: String): String {
        val withoutId = rawTitle.replace(TRAILING_ID_REGEX, "").trim()
        val splitIdx = findMetadataPipeIndex(withoutId)
        val primary = if (splitIdx > 0) withoutId.substring(0, splitIdx) else withoutId
        return primary
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trimEnd('-', '–', '—', ':', '|', '｜')
            .trim()
            .ifEmpty { rawTitle }
    }

    private fun findMetadataPipeIndex(text: String): Int {
        var parenDepth = 0
        var bracketDepth = 0
        for (i in text.indices) {
            when (text[i]) {
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '｜' -> {
                    if (parenDepth == 0 && bracketDepth == 0) {
                        return i
                    }
                }
                '|' -> {
                    if (parenDepth == 0 && bracketDepth == 0) {
                        val isDoublePipe = i + 1 < text.length && text[i + 1] == '|'
                        val isSpacedPipe = i > 0 && text[i - 1].isWhitespace() &&
                            (i + 1 >= text.length || text[i + 1].isWhitespace())
                        if (isDoublePipe || isSpacedPipe) {
                            return i
                        }
                    }
                }
            }
        }
        return -1
    }
}
