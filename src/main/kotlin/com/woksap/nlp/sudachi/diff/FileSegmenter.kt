package com.woksap.nlp.sudachi.diff

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.fileSize

class FileSegmenter(private val segmentSize: Int, private val windowSize: Int) {
    private val search = BinarySentenceSearch()

    fun segments(file: Path): List<Pair<Long, Long>> {
        val array = ByteArray(windowSize)
        val buffer = ByteBuffer.wrap(array)
        var start = 0L
        val length = file.fileSize()
        val channel = Files.newByteChannel(file, StandardOpenOption.READ)

        val result = ArrayList<Pair<Long, Long>>()

        while (start < length) {
            var roughEnd = start + segmentSize
            // condition 1: last segment is larger than remaining part of file
            if (roughEnd >= length) {
                result.add(start to length)
                return result
            }
            while (roughEnd < length) {
                channel.position(roughEnd)
                buffer.clear()
                val nread = channel.read(buffer)
                if (nread == -1) {
                    result.add(start to length)
                    return result
                }
                search.reset(array, 0, nread)
                search.nextLine()
                // found eol character
                if (search.remaining != 0) {
                    val realEnd = roughEnd + search.end
                    result.add(start to realEnd)
                    start = realEnd
                    break
                }
                roughEnd += nread
            }

        }

        return result
    }
}