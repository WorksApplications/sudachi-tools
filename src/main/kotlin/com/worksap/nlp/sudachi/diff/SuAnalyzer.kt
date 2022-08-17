package com.worksap.nlp.sudachi.diff

import java.io.PrintStream
import java.nio.file.Path

interface SuAnalyzer {
    fun analyze(input: Path, start: Long, end: Long, output: Path)
    fun analyzeSentence(sentence: String, output: PrintStream, debug: Boolean = false)
}

interface SuRuntime {
    fun analyzer(mode: String? = null): SuAnalyzer
    fun run(input: Path, output: Path, filter: String): Unit
}