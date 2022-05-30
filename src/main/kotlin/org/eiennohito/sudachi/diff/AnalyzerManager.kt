package org.eiennohito.sudachi.diff

import kotlinx.coroutines.*
import org.eiennohito.sudachi.diff.iface.SudachiRuntime
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.fileSize
import kotlin.io.path.relativeTo

@Suppress("jol")
class AnalyzerManager(private val runtime: SuRuntime, private val segmenter: FileSegmenter, private val inputRoot: Path, private val outputRoot: Path) {
    private val inFlight = AtomicLong(0)
    private val enqueued = AtomicLong(0)
    private val completed = AtomicLong(0)
    private val processedBytes = AtomicLong(0)
    private val start = System.nanoTime()
    private var toProcessBytes = 1L
    private val filesInProgress = AtomicLong(0)

    private val executor = run {
        val count = AtomicInteger(0)
        val executor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), ThreadFactory {
            val thread = Thread(it, "analyzer-thread-%03d".format(count.getAndDecrement()))
            thread.priority = Thread.MIN_PRIORITY
            thread.isDaemon = true
            thread
        })
        executor.asCoroutineDispatcher()
    }

    private val lock = Object()

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

    private fun printProgress() {
        synchronized(lock) {
            val currentTime = System.nanoTime()
            val eplased = Duration.ofNanos(currentTime - start)
            val processed = processedBytes.get()
            val speed = processed / (eplased.toMillis() / 1000.0)
            val remainingSeconds = (toProcessBytes - processed) / speed
            val remaining = Duration.ofMillis((remainingSeconds * 1000).toLong())
            System.err.print("\r$eplased: [${enqueued.get()}, ${inFlight.get()}, ${completed.get()}], R:${remaining}")
        }
    }

    private fun makeAnalysisSubtask(input: Path, start: Long, end: Long, output: Path) {
        enqueued.decrementAndGet()
        inFlight.incrementAndGet()
        val analyzer = runtime.analyzer()
        analyzer.analyze(input, start, end, output)
        inFlight.decrementAndGet()
        completed.incrementAndGet()
        processedBytes.addAndGet(end - start)
    }

    private fun makeOutputName(input: Path, segmentNum: Int): Path {
        val relative = input.relativeTo(inputRoot)
        val relativeString = relative.toString()
        val outputTemplate = "%s-%05d.txt.zstd".format(relativeString, segmentNum)
        return outputRoot.resolve(outputTemplate)
    }

    fun waitForCompletion() {
        while (filesInProgress.get() != 0L) {
            printProgress()
            Thread.sleep(1000)
        }
    }
}