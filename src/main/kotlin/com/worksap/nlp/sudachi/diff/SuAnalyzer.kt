package com.worksap.nlp.sudachi.diff

import java.nio.file.Path

interface SuAnalyzer {
    fun analyze(input: Path, start: Long, end: Long, output: Path)
}

interface SuRuntime {
    fun analyzer(mode: String = "C"): SuAnalyzer
    fun run(input: Path, output: Path): Unit
}