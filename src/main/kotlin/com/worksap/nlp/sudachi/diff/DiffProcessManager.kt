package com.worksap.nlp.sudachi.diff

import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import com.google.common.io.ByteStreams
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.*


data class ProcessedSpan(val id: String, val span: Both, val level: Int)
data class DiffPack(val diffs: List<ProcessedSpan>, val path: Path)

@Suppress("jol")
class DiffProcessManager(private val leftInputRoot: Path, private val rightInputRoot: Path, private val outputRoot: Path): ProcessManager() {

    private val chan = Channel<DiffPack>(10)

    companion object {
        private val nameEndRegex = Regex("\\.txt\\.zstd$")
    }

    fun enqueue(leftPath: Path): Job {
        filesInProgress.incrementAndGet()
        enqueued.incrementAndGet()
        val rightPath = formRightPath(leftPath)
        val outputPath = formOutputPath(leftPath)
        val size = leftPath.fileSize()
        val job = CoroutineScope(executor).launch {
            runSubtask {
                val diffs = computeDiffs(leftPath, rightPath, outputPath)
                processedBytes.addAndGet(size)
                chan.send(diffs)
                size
            }
            filesInProgress.decrementAndGet()
        }
        toProcessBytes += size
        return job
    }

    private fun formOutputPath(leftPath: Path): Path {
        val relative = leftPath.relativeTo(leftInputRoot).toString().replace(nameEndRegex, ".diff.html")
        return outputRoot.resolve("diffs").resolve(relative)
    }

    private fun formRightPath(leftPath: Path): Path {
        val relative = leftPath.relativeTo(leftInputRoot)
        val rightPath = rightInputRoot.resolve(relative)
        if (rightPath.notExists()) {
            throw FileNotFoundException("File $rightPath did not exist")
        }
        return rightPath
    }

    private val resourcePathAbsolute = outputRoot.resolve("resources")

    private fun computeDiffs(leftPath: Path, rightPath: Path, outputPath: Path): DiffPack {
        val resourceRelative = resourcePathAbsolute.relativeTo(outputPath.parent)
        val diffDetails = DiffDetails(outputPath, resourceRelative)
        ZstdInputStreamNoFinalizer(leftPath.inputStream()).use { leftRaw ->
            ZstdInputStreamNoFinalizer(rightPath.inputStream()).use { rightRaw ->
                val prediff = DiffCandidateCalculator(TokenIterator(leftRaw), TokenIterator(rightRaw))
                while (true) {
                    when (val obj = prediff.processOne()) {
                        is NoDiff -> {}
                        is Finished -> break
                        is Diff -> diffDetails.addDiff(obj.computeSpans())
                    }
                }
            }
        }
        return diffDetails.makePack()
    }

    override fun waitForCompletion() {
        val diffStatistics = DiffStatistics(outputRoot)
        val numWritten = AtomicInteger(0)
        try {
            runBlocking(Dispatchers.Unconfined) {
                while (true) {
                    val packed = chan.tryReceive()
                    if (packed.isSuccess) {
                        val pack = packed.getOrThrow()
                        numWritten.addAndGet(pack.diffs.size)
                        diffStatistics.handle(pack)
                    } else if (packed.isFailure && filesInProgress.get() == 0L) {
                        break
                    } else {
                        delay(250)
                    }
                    printProgress()
                }
            }
            diffStatistics.writeStatistics()
            fillResources()
        } finally {
            chan.close()
            executor.close()
        }

        if (numWritten.get() == 0) {
            println("\rCompletely Identical!              ")
        }
    }

    private fun fillResources() {
        val css = resourcePathAbsolute.resolve("style.css")
        resourcePathAbsolute.createDirectories()
        css.deleteIfExists()
        css.outputStream(StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { it ->
            javaClass.getResourceAsStream("/style.css")!!.use { res ->
                ByteStreams.copy(res, it)
            }
        }
    }
}