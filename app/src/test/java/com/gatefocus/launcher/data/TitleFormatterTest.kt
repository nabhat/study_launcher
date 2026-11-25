package com.gatefocus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleFormatterTest {

    @Test
    fun stripsFullWidthPipeSuffixesAndTrailingVideoId() {
        val raw = "02 - Basics of Matrices & Determinants - 1 ｜｜ Engineering Mathematics ｜｜ PrepFusion [5ZKlDBuxVEg]"
        assertEquals("02 - Basics of Matrices & Determinants - 1", TitleFormatter.cleanLectureTitle(raw))
    }

    @Test
    fun stripsTruncatedTrailingMetadataAndAsciiPipes() {
        val raw = "42 - Analysis of Inductors and RL Circuits || Network Analysis || GATE 2025-26 || Pre [AJC-rPX4V7w]"
        assertEquals("42 - Analysis of Inductors and RL Circuits", TitleFormatter.cleanLectureTitle(raw))
    }

    @Test
    fun preservesSinglePipeInsideMathParentheses() {
        val raw = "15 - Conditional Probability P(A | B) ｜｜ Probability & Statistics [xYz123_ab]"
        assertEquals("15 - Conditional Probability P(A | B)", TitleFormatter.cleanLectureTitle(raw))
    }

    @Test
    fun preservesBracketNotationInsideTitleAndReturnsPlainTitle() {
        val raw = "08 - Null Space, Discussing [A][B]=0 & Some Questions"
        assertEquals("08 - Null Space, Discussing [A][B]=0 & Some Questions", TitleFormatter.cleanLectureTitle(raw))
    }
}
