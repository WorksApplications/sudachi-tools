package org.eiennohito.sudachi.diff.iface

import com.github.luben.zstd.ZstdOutputStreamNoFinalizer
import com.google.common.io.ByteStreams
import com.worksap.nlp.sudachi.MorphemeList
import org.eiennohito.sudachi.diff.SuAnalyzer
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream


private fun PrintWriter.printt(data: String) {
    print(data)
    print('\t')
}

class SudachiAnalyzer(runtime: SudachiRuntime, mode: String = "C"): SuAnalyzer {
    private val tokenizer = runtime.sudachiDic.create()
    private val mode = runtime.mode(mode)

    fun process(data: InputStream, out: OutputStream) {
        val reader = InputStreamReader(data, StandardCharsets.UTF_8)
        val buffered = BufferedReader(reader)
        val writer = PrintWriter(out, false, StandardCharsets.UTF_8)
        buffered.lineSequence().forEach { analyzeAndWrite(it, writer) }
        writer.flush()
    }

    private fun analyzeAndWrite(line: String, writer: PrintWriter) {
        val morphemes = tokenizer.tokenize(mode, line)
        for (m in morphemes) {
            writer.printt(m.surface())
            writer.printt(m.dictionaryForm())
            writer.printt(m.readingForm())
            val pos = m.partOfSpeech()
            writer.printt(pos[0])
            writer.printt(pos[1])
            writer.printt(pos[2])
            writer.printt(pos[3])
            writer.printt(pos[4])
            writer.println(pos[5])
        }
        writer.println("EOS")
    }

    @Suppress("UnstableApiUsage")
    override fun analyze(input: Path, start: Long, end: Long, output: Path) {
        input.inputStream(StandardOpenOption.READ).use { is0 ->
            ByteStreams.skipFully(is0, start)
            val limited = ByteStreams.limit(is0, end - start)
            output.parent.createDirectories()
            val os = output.outputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE)
            ZstdOutputStreamNoFinalizer(os).use { compressed ->
                process(limited, compressed)
            }
        }
    }
}