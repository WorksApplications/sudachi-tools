package com.worksap.nlp.sudachi.diff

import kotlinx.cli.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiPredicate
import java.util.logging.LogManager
import kotlin.io.path.name
import kotlin.io.path.notExists

private fun String.existingPath(): Path {
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

        val parser = ArgParser("sudachi-cli")

        class Analyze : Subcommand("analyze", "analyze a text corpus with provided jar and dictionary") {
            val input by argument(
                ArgType.String,
                description = "directory with input text files, one sentence per line"
            )
            val output by option(ArgType.String, description = "path for outputting analyzed files").required()
            val jar by option(ArgType.String, description = "path to sudachi jar file")
            val config by option(ArgType.String, description = "path to sudachi configuration")

            override fun execute() {
                val cfg = SudachiRuntimeConfig(jar?.existingPath(), null, null, config?.existingPath())
                val runner = SudachiAnalysisTaskRunner(cfg)
                runner.process(input.existingPath(), Path.of(output))
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


        parser.subcommands(Analyze(), Diff())
        parser.parse(args)
    }
}