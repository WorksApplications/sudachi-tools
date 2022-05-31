package org.eiennohito.sudachi.diff

import kotlinx.coroutines.asCoroutineDispatcher
import java.time.Duration
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

abstract class ProcessManager {
    protected val inFlight = AtomicLong(0)
    protected val enqueued = AtomicLong(0)
    protected val completed = AtomicLong(0)
    protected val processedBytes = AtomicLong(0)
    protected val start = System.nanoTime()
    protected var toProcessBytes = 1L
    protected val filesInProgress = AtomicLong(0)

    protected val executor = run {
        val count = AtomicInteger(0)
        val executor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), ThreadFactory {
            val thread = Thread(it, "analyzer-thread-%03d".format(count.getAndIncrement()))
            thread.priority = Thread.MIN_PRIORITY
            thread.isDaemon = true
            thread
        })
        executor.asCoroutineDispatcher()
    }

    private val lock = Object()

    protected fun printProgress() {
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

    protected inline fun runSubtask(body: () -> Long) {
        enqueued.decrementAndGet()
        inFlight.incrementAndGet()
        val numProcessed = body()
        inFlight.decrementAndGet()
        completed.incrementAndGet()
        processedBytes.addAndGet(numProcessed)
    }

    open fun waitForCompletion() {
        while (filesInProgress.get() != 0L) {
            printProgress()
            Thread.sleep(1000)
        }
        executor.close()
    }
}