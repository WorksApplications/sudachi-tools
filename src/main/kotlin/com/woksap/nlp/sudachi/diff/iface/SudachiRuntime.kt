package com.woksap.nlp.sudachi.diff.iface

import com.woksap.nlp.sudachi.diff.*
import com.worksap.nlp.sudachi.Dictionary
import com.worksap.nlp.sudachi.DictionaryFactory
import com.worksap.nlp.sudachi.Morpheme
import com.worksap.nlp.sudachi.Tokenizer.SplitMode
import com.worksap.nlp.sudachi.dictionary.WordInfo
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

class SudachiRuntime(private val classloader: ClassLoader, config: SudachiRuntimeConfig): SuRuntime {
    val sudachiDic: Dictionary = kotlin.run {
        val sudachiFactory = classloader.loadClass("com.worksap.nlp.sudachi.DictionaryFactory")
        val factory = sudachiFactory.getDeclaredConstructor().newInstance() as DictionaryFactory
        if (config.sudachiConfigFile != null) {
            val cfgData = config.sudachiConfigFile.readText()
            factory.create(config.sudachiConfigFile.parent.toString(), cfgData, false)
        } else {
            factory.create(config.sudachiConfigFile?.parent.toString(), config.addSettings, true)
        }
    }

    private val wordInfoHandle = kotlin.run {
        val morphemeImplClass = classloader.loadClass("com.worksap.nlp.sudachi.MorphemeImpl")
        val lookup = MethodHandles.privateLookupIn(morphemeImplClass, MethodHandles.lookup())
        val wordInfoClass = classloader.loadClass("com.worksap.nlp.sudachi.dictionary.WordInfo")
        lookup.findVirtual(morphemeImplClass, "getWordInfo", MethodType.methodType(wordInfoClass))
    }

    fun wordInfoOf(m: Morpheme): WordInfo {
        return wordInfoHandle.invoke(m) as WordInfo
    }

    fun mode(mode: String): SplitMode {
        return SplitMode.valueOf(mode)
    }

    // entry point, is called via reflection
    fun run(input: Path, output: Path) {
        val segmenter = FileSegmenter(5 * 1024 * 1024, 32 * 1024)
        val analyzer = AnalyzerManager(this, segmenter, input, output)
        Files.find(input, Int.MAX_VALUE, { p, attrs -> attrs.isRegularFile && p.name.endsWith(".txt") }).use {
            it.forEach { p -> analyzer.enqueue(p) }
        }
        analyzer.waitForCompletion()
    }

    override fun analyzer(mode: String): SuAnalyzer {
        return SudachiAnalyzer(this, mode)
    }
}