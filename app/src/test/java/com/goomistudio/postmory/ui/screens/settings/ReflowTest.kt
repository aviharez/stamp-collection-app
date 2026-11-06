package com.goomistudio.postmory.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReflowTest {
    @Test fun joinsWrappedLinesAndKeepsStructure() {
        val text = """
            |PREAMBLE
            |The goals of the Open Font License (OFL) are to stimulate worldwide
            |development of collaborative font projects.
            |
            |1) Neither the Font Software nor any of its individual components,
            |in Original or Modified Versions, may be sold by itself.
            |
            |      1. Definitions.
            |
            |      "License" shall mean the terms and conditions for use,
            |      reproduction, and distribution.
            |-----------------------------------------------------------
        """.trimMargin()
        assertEquals(
            """
            |PREAMBLE
            |The goals of the Open Font License (OFL) are to stimulate worldwide development of collaborative font projects.
            |
            |1) Neither the Font Software nor any of its individual components, in Original or Modified Versions, may be sold by itself.
            |
            |1. Definitions.
            |
            |"License" shall mean the terms and conditions for use, reproduction, and distribution.
            |-----------------------------------------------------------
            """.trimMargin(),
            reflow(text),
        )
    }
}
