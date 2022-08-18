package com.worksap.nlp.sudachi.diff

import com.worksap.nlp.sudachi.diff.analyze.SudachiResolver
import kotlinx.cli.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.LogManager
import kotlin.io.path.name
import kotlin.io.path.notExists

fun String.existingPath(): Path {
    val path = Path.of(this)
    if (path.notExists()) {
        throw IllegalArgumentException("File $path does not exist")
    }
    return path
}


@OptIn(ExperimentalCli::class)
object Main {
    @JvmStatic
    fun main(args: Array<String>) {

        LogManager.getLogManager().readConfiguration(Main::class.java.getResourceAsStream("/logging.properties"))

        val parser = ArgParser("sudachi-tools", prefixStyle = ArgParser.OptionPrefixStyle.GNU)

        abstract class AnalyzeBase(name: String, description: String): Subcommand(name, description) {
            val jar by option(ArgType.String, description = "path to sudachi jar file or sudachi version starting with v (e.g. v0.6.2)")
            val config by option(ArgType.String, description = "path to sudachi configuration")
            val cacheDirectory by option(ArgType.String, description = "cache directory, by default ~/.local/cache/sudachi-tools")
            val systemDict by option(ArgType.String, description = "system dictionary to use instead of the configured one (Sudachi 0.6.0+)")
            val userDict by option(ArgType.String, description = "additional user dictionary (Sudachi 0.6.0+)").multiple()
            val mode by option(ArgType.Choice(listOf("A", "B", "C"), {it}), description = "sudachi analysis mode").default("C")

            private fun resolveCacheDirectory(value: String?): Path {
                if (value != null) {
                    return Path.of(value)
                }
                val homeDir = run {
                    val homeVar = System.getenv("HOME")
                    homeVar ?: System.getenv("USERPROFILE")
                }
                return Path.of(homeDir).resolve(".local/cache/sudachi-tools")
            }

            protected fun sudachiConfig(): SudachiRuntimeConfig {
                val addSettings = SudachiAdditionalSettings(systemDict?.existingPath(), userDict.map { it.existingPath() })
                val support = SudachiResolver(resolveCacheDirectory(cacheDirectory))
                return support.config(jar, config, addSettings, mode)
            }
        }

        class Analyze : AnalyzeBase("analyze", "analyze a text corpus with provided jar and dictionary") {
            val input by argument(
                ArgType.String,
                description = "directory with input text files, one sentence per line"
            )
            val output by option(ArgType.String, description = "path for outputting analyzed files").required()
            val filter by option(ArgType.String, description = "file filter to analyze, *.txt by default")

            override fun execute() {
                val runner = SudachiAnalysisTaskRunner(sudachiConfig())
                runner.process(input.existingPath(), Path.of(output), filter ?: "*.txt")
            }
        }

        class Debug : AnalyzeBase("debug", "run sudachi in debug mode (interactive analysis)") {
            override fun execute() {
                val runner = SudachiAnalysisTaskRunner(sudachiConfig())
                runner.interactiveDebug(System.`in`, mode)
            }
        }

        class Diff: Subcommand("diff", "format diff file with results") {
            val input1 by argument(
                ArgType.String,
                description = "folder with zstd compressed files produced by analyze step"
            )

            val input2 by argument(
                ArgType.String,
                description = "folder with zstd compressed files produced by analyze step"
            )

            val output by argument(
                ArgType.String,
                description = "resulting diff htmls will be outputted here"
            )

            override fun execute() {
                val leftRoot = input1.existingPath()
                val differ = DiffProcessManager(leftRoot, input2.existingPath(), Path.of(output))
                Files.find(leftRoot, Int.MAX_VALUE, { p, attrs -> attrs.isRegularFile && p.name.endsWith(".txt.zstd") }).use { s ->
                    s.forEach { differ.enqueue(it) }
                }
                differ.waitForCompletion()
            }
        }


        parser.subcommands(Analyze(), Diff(), Debug())
        parser.parse(args)
    }
}