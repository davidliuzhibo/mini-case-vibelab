package com.vrgames.minicase.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every character speaks: each question has its recording in the app. */
class VoiceTest {
    @Test fun everyAnswerHasARecording() {
        val dir = File("src/main/assets/narration")
        val missing = Cases.all.flatMap { it.people }.flatMap { it.questions }.map { it.id }.filter { !File(dir, "$it.ogg").isFile }
        assertTrue("missing $missing", missing.isEmpty())
    }
}
