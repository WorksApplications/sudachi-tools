package com.woksap.nlp.sudachi.diff

import kotlinx.coroutines.*
import com.woksap.nlp.sudachi.diff.iface.SudachiRuntime
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.fileSize
import kotlin.io.path.relativeTo

@Suppress("jol")
class AnalyzerManager(private val runtime: SuRuntime, private val segmenter: FileSegmenter, private val inputRoot: Path, private val outputRoot: Path): ProcessManager() {
    fun enqueue(input: Path): Job {
        val segments = segmenter.segments(input)
        filesInProgress.incrementAndGet()
        val job = CoroutineScope(Dispatchers.Default).launch {
            segments.mapIndexed { segmentNum, (start, end) ->
                val output = makeOutputName(input, segmentNum)
                enqueued.incrementAndGet()
                async(executor) {
                    makeAnalysisSubtask(input, start, end, output)
                }
            }.awaitAll()
            System.err.println("\rfinished: $input                                              ")
            filesInProgress.decrementAndGet()
            printProgress()
        }

        toProcessBytes += input.fileSize()
        return job
    }

    private fun makeAnalysisSubtask(input: Path, start: Long, end: Long, output: Path) {
        runSubtask {
            val analyzer = runtime.analyzer()
            analyzer.analyze(input, start, end, output)
            end - start
        }
    }

    private fun makeOutputName(input: Path, segmentNum: Int): Path {
        val relative = input.relativeTo(inputRoot)
        val relativeString = relative.toString()
        val outputTemplate = "%s-%05d.txt.zstd".format(relativeString, segmentNum)
        return outputRoot.resolve(outputTemplate)
    }
}