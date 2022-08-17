package com.worksap.nlp.sudachi.diff.iface

import com.worksap.nlp.sudachi.Config
import com.worksap.nlp.sudachi.Dictionary
import com.worksap.nlp.sudachi.DictionaryFactory
import com.worksap.nlp.sudachi.Morpheme
import com.worksap.nlp.sudachi.Tokenizer.SplitMode
import com.worksap.nlp.sudachi.dictionary.WordInfo
import com.worksap.nlp.sudachi.diff.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

class SudachiRuntime(private val classloader: ClassLoader, private val config: SudachiRuntimeConfig): SuRuntime {
    private fun makeSudachiInstanceFromConfig(): Dictionary {
        val suConf = if (config.sudachiConfigFile != null) {
            Config.fromFile(config.sudachiConfigFile).withFallback(Config.defaultConfig())
        } else {
            Config.defaultConfig()
        }
        return DictionaryFactory().create(suConf)
    }

    private fun makeSudachiInstanceLegacy(): Dictionary {
        val factory = DictionaryFactory()
        return if (config.sudachiConfigFile != null) {
            val cfgData = config.sudachiConfigFile.readText()
            factory.create(config.sudachiConfigFile.parent.toString(), cfgData, true)
        } else {
            factory.create(null, config.addSettings, true)
        }
    }

    val sudachiDic: Dictionary = run {
        try {
            makeSudachiInstanceFromConfig()
        } catch (e: Throwable) {
            when (e) {
                is NoClassDefFoundError, is NoSuchMethodError -> makeSudachiInstanceLegacy()
                else -> throw e
            }
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

    override fun run(input: Path, output: Path, filter: String) {
        val segmenter = FileSegmenter(5 * 1024 * 1024, 32 * 1024)
        val analyzer = AnalyzerManager(this, segmenter, input, output)

        val filterRegex = Regex(filter.replace("*", ".*"))

        Files.find(input, Int.MAX_VALUE, { p, attrs -> attrs.isRegularFile && filterRegex.matchEntire(p.name) != null }).use {
            it.forEach { p -> analyzer.enqueue(p) }
        }
        analyzer.waitForCompletion()
    }

    override fun analyzer(mode: String): SuAnalyzer {
        return SudachiAnalyzer(this, mode)
    }
}