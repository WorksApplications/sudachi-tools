package org.eiennohito.sudachi.diff

import kotlinx.cli.*
import java.nio.file.Path
import java.util.logging.LogManager
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


        parser.subcommands(Analyze())
        parser.parse(args)
    }
}