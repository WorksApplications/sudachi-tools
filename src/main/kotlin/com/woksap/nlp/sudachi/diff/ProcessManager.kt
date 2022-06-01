package com.woksap.nlp.sudachi.diff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

    private fun formatDuration(duration: Duration): String {
        val secs = duration.toSecondsPart()
        val mins = duration.toMinutesPart()
        val hours = duration.toHoursPart()
        val totalHours = duration.toHours()

        return if (totalHours > 24) {
            "--:--:--"
        } else {
            "%d:%02d:%02d".format(hours, mins, secs)
        }
    }

    protected fun printProgress() {
        synchronized(lock) {
            val currentTime = System.nanoTime()
            val eplased = Duration.ofNanos(currentTime - start)
            val processed = processedBytes.get()
            val speed = processed / (eplased.toMillis() / 1000.0)
            val remainingSeconds = (toProcessBytes - processed) / speed
            val remaining = Duration.ofMillis((remainingSeconds * 1000).toLong())
            val eplacedString = formatDuration(eplased)
            val remainingString = formatDuration(remaining)
            System.err.print("\r$eplacedString: [${enqueued.get()}, ${inFlight.get()}, ${completed.get()}], R:${remainingString}")
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
        executor.use {
            runBlocking(Dispatchers.Unconfined) {
                while (filesInProgress.get() != 0L) {
                    printProgress()
                    delay(213)
                }
            }
        }
    }
}