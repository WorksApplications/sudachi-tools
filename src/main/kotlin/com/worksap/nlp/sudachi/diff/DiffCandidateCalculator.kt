package com.worksap.nlp.sudachi.diff

import java.io.InputStream
import java.nio.charset.StandardCharsets

class TokenIterator(private val data: InputStream) {
    companion object {
        @JvmField
        val EOS_HASH: Long = kotlin.run {
            val bytes = "EOS\n".toByteArray()
            val search = BinarySentenceSearch()
            search.reset(bytes, 0, bytes.size)
            search.nextLine()
        }

        const val BUFFER_SIZE = 2 * 1024 * 1024
    }

    private val array = ByteArray(BUFFER_SIZE)
    private var areaEnd = BUFFER_SIZE
    private var offset = 0L
    private val search = BinarySentenceSearch()
    var sentenceStart = 0
        private set
    var sentenceEnd = 0
        private set


    fun maybeRefill() {
        if (areaEnd != BUFFER_SIZE) { // had end of stream
            return
        }
        val remaining = search.remaining
        if (remaining > 128 * 1024) {
            return
        }

        System.arraycopy(array, search.end, array, 0, remaining)
        offset += search.end

        var toRead = BUFFER_SIZE - remaining
        var readStart = remaining
        while (true) {
            val nread = data.read(array, readStart, toRead)
            if (nread == -1) {
                break
            }
            toRead -= nread
            readStart += nread
            areaEnd = readStart
            if (toRead == 0) {
                break
            }
        }
        sentenceStart = 0
        sentenceEnd = 0
        search.reset(array, 0, areaEnd)
    }

    fun nextTokenHash(): Long {
        val hash = search.nextLine()
        if (hash == EOS_HASH && search.isEos()) {
            sentenceStart = sentenceEnd
            sentenceEnd = lineEnd
            // println("\nSentence: $sentenceStart - $sentenceEnd")
            // println(String(array, sentenceStart, sentenceEnd - sentenceStart, StandardCharsets.UTF_8))
        }
        return hash
    }

    fun fullSentence(): String {
        while (!(nextTokenHash() == EOS_HASH && search.isEos())) {
            // do nothing
        }
        return String(array, sentenceStart, sentenceEnd - sentenceStart, StandardCharsets.UTF_8)
    }

    val lineStart: Int
        get() = search.start

    val lineEnd: Int
        get() = search.end

    val position: Long
        get() = offset + search.end

    fun hasData(): Boolean = search.remaining > 0
}

sealed interface SentenceDiff
object NoDiff: SentenceDiff
object Finished: SentenceDiff
data class Diff(val left: String, val right: String): SentenceDiff {
    fun computeSpans(): List<DiffSpan> {
        val leftTokens = parseTokens(left)
        val rightTokens = parseTokens(right)
        return DiffCalculator(leftTokens, rightTokens).compute()
    }

    private fun parseTokens(data: String): List<SudachiToken> {
        val tokens = data.lines().filter { it.isNotBlank() && it != "EOS" }.mapTo(ArrayList()) { SudachiToken.parse(it) }
        tokens.add(SudachiToken.EOS)
        return tokens
    }
}

/**
 * Because both sequence of tokens MUST have equal length in characters, we can get with linear
 * algorithm instead of the usual diff algorithm.
 */
class DiffCalculator(private val left: List<SudachiToken>, private val right: List<SudachiToken>) {
    private enum class State {
        EQ,
        LEFT,
        RIGHT,
        BOTH
    }

    private var leftBuf = ArrayList<SudachiToken>()
    private var rightBuf = ArrayList<SudachiToken>()
    private var state = State.EQ
    private var result = ArrayList<DiffSpan>()

    private fun maybeFinalizeSpan() {
        if (leftBuf.lastOrNull() === SudachiToken.EOS) {
            leftBuf.removeAt(leftBuf.size - 1)
        }

        if (rightBuf.lastOrNull() === SudachiToken.EOS) {
            rightBuf.removeAt(rightBuf.size - 1)
        }

        if (leftBuf.isEmpty() && rightBuf.isEmpty()) {
            return
        }


        when (state) {
            State.EQ -> {
                result.add(Equal(leftBuf))
                leftBuf = ArrayList()
            }
            State.LEFT -> {
                result.add(LeftOnly(leftBuf))
                leftBuf = ArrayList()
            }
            State.RIGHT -> {
                result.add(RightOnly(rightBuf))
                rightBuf = ArrayList()
            }
            State.BOTH -> {
                result.add(Both(leftBuf, rightBuf))
                leftBuf = ArrayList()
                rightBuf = ArrayList()
            }
        }
    }

    fun compute(): List<DiffSpan> {
        var i = 0
        var j = 0
        var leftLengthFinished = 0
        var rightLengthFinished = 0
        var lastLevel = Int.MAX_VALUE
        while (i < left.size || j < right.size) {
            val lt = left[i]
            val rt = right[j]
            if (lt == rt) {
                if (state != State.EQ) {
                    maybeFinalizeSpan()
                    state = State.EQ
                }
                leftBuf.add(lt)
                i += 1
                j += 1
                leftLengthFinished += lt.surface.length
                rightLengthFinished += rt.surface.length
                continue
            }

            if (state != State.BOTH) {
                maybeFinalizeSpan()
                state = State.BOTH
                lastLevel = lt.diffLevel(rt)
            }

            val curLevel = lt.diffLevel(rt)
            if (lastLevel != curLevel) {
                maybeFinalizeSpan()
                lastLevel = curLevel
            }

            val leftLength = leftLengthFinished + lt.surface.length
            val rightLength = rightLengthFinished + rt.surface.length

            if (leftLength == rightLength) {
                i += 1
                j += 1
                leftBuf.add(lt)
                rightBuf.add(rt)
                leftLengthFinished += lt.surface.length
                rightLengthFinished += rt.surface.length
            } else if (leftLength > rightLength) {
                j += 1
                rightBuf.add(rt)
                rightLengthFinished += rt.surface.length
            } else {
                i += 1
                leftBuf.add(lt)
                leftLengthFinished += lt.surface.length
            }
        }
        maybeFinalizeSpan()
        return result
    }

}

data class SudachiToken(
    val surface: String,
    val normalized: String,
    val reading: String,
    val pos1: String,
    val pos2: String,
    val pos3: String,
    val pos4: String,
    val pos5: String,
    val pos6: String,
) {
    companion object {
        const val MAX_LEVEL: Int = 9

        val LevelNames = arrayOf(
            "Surface",
            "Normalized Form",
            "Reading",
            "POS1",
            "POS2",
            "POS3",
            "POS4",
            "POS5",
            "POS6",
        )

        val EOS = SudachiToken(
            "EOS", "", "", "", "", "", "", "", ""
        )

        fun parse(data: String): SudachiToken {
            val parts = data.split('\t', limit = MAX_LEVEL)
            return SudachiToken(
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                parts[4],
                parts[5],
                parts[6],
                parts[7],
                parts[8],
            )
        }
    }

    fun component(n: Int): String {
        return when (n) {
            0 -> surface
            1 -> normalized
            2 -> reading
            3 -> pos1
            4 -> pos2
            5 -> pos3
            6 -> pos4
            7 -> pos5
            8 -> pos6
            else -> ""
        }
    }

    fun diffLevel(other: SudachiToken): Int {
        if (surface != other.surface) return 0
        if (normalized != other.normalized) return 1
        if (reading != other.reading) return 2
        if (pos1 != other.pos1) return 3
        if (pos2 != other.pos2) return 4
        if (pos3 != other.pos3) return 5
        if (pos4 != other.pos4) return 6
        if (pos5 != other.pos5) return 7
        if (pos6 != other.pos6) return 8

        return Int.MAX_VALUE
    }
}

sealed interface DiffSpan
data class Equal(val tokens: List<SudachiToken>): DiffSpan
data class RightOnly(val tokens: List<SudachiToken>): DiffSpan
data class LeftOnly(val tokens: List<SudachiToken>): DiffSpan
data class Both(val left: List<SudachiToken>, val right: List<SudachiToken>): DiffSpan

class DiffCandidateCalculator(private val left: TokenIterator, private val right: TokenIterator) {
    fun processOne(): SentenceDiff {
        left.maybeRefill()
        right.maybeRefill()

        while (left.hasData() || right.hasData()) {
            val leftHash = left.nextTokenHash()
            val rightHash = right.nextTokenHash()

            if (leftHash != rightHash) {
                val leftData = left.fullSentence()
                val rightData = right.fullSentence()
                return Diff(leftData, rightData)
            } else if (leftHash == TokenIterator.EOS_HASH) {
                return NoDiff
            }
        }
        return Finished
    }
}