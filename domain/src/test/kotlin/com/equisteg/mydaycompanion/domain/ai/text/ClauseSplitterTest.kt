package com.equisteg.mydaycompanion.domain.ai.text

import kotlin.test.Test
import kotlin.test.assertEquals

class ClauseSplitterTest {
    @Test
    fun `splits on new actions only`() {
        assertEquals(listOf("call the bank", "pay the bill"), ClauseSplitter.split("call the bank and pay the bill"))
        assertEquals(listOf("meeting with Tom and Jerry at 3"), ClauseSplitter.split("meeting with Tom and Jerry at 3"))
        assertEquals(listOf("meeting between 3 and 4"), ClauseSplitter.split("meeting between 3 and 4"))
        assertEquals(listOf("call Raj", "meeting at 11"), ClauseSplitter.split("call Raj and meeting at 11"))
        assertEquals(listOf("buy milk", "maybe clean the garage"), ClauseSplitter.split("buy milk and maybe clean the garage"))
    }

    @Test
    fun `keeps shopping lists together`() {
        assertEquals(listOf("buy eggs, milk and bread"), ClauseSplitter.split("buy eggs, milk and bread"))
        assertEquals(listOf("buy eggs", "call mom"), ClauseSplitter.split("buy eggs, call mom"))
    }

    @Test
    fun `a time on its own belongs to the next item`() {
        assertEquals(listOf("at 5 pm call mom"), ClauseSplitter.split("at 5 pm, call mom"))
        assertEquals(listOf("tomorrow at 10 dentist", "gym"), ClauseSplitter.split("tomorrow at 10, dentist, gym"))
    }

    @Test
    fun `splits sentences, lines and semicolons`() {
        assertEquals(
            listOf("dentist at 4pm", "gym", "call mom.", "pay rent"),
            ClauseSplitter.split("dentist at 4pm; gym\ncall mom. pay rent"),
        )
        assertEquals(listOf("meeting at 9 a.m. with Dr. Rao"), ClauseSplitter.split("meeting at 9 a.m. with Dr. Rao"))
    }
}
