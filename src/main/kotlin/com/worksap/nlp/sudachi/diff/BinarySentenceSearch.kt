package com.worksap.nlp.sudachi.diff

class BinarySentenceSearch {
    private var data: ByteArray = ByteArray(0)
    var start: Int = 0
        private set
    var end: Int = 0
        private set
    private var dataEnd: Int = 0

    companion object {
        const val EOL = '\n'.code.toByte()

        // taken from MurmurHash
        const val MULT1 = 0xcc9e2d51L
        const val MULT2 = 0x1b873593L
    }

    fun reset(data: ByteArray, offset: Int, length: Int) {
        this.data = data
        start = offset
        end = offset
        dataEnd = length
    }

    val remaining get(): Int = dataEnd - end


    fun nextLine(): Long {
        val array = data
        var hash = 0xdeadbeefL
        var count = 0
        start = end
        // eliding bound checks
        if (dataEnd < 0 || dataEnd > data.size) {
            throw java.lang.IllegalStateException("must not happen")
        }
        for (i in start until dataEnd) {
            val b = array[i]
            if (b == EOL) {
                end = i + 1
                return hash
            }
            val hashPart = ((count.toLong() shl 32) or b.toLong()) * MULT1
            hash = (hash xor hashPart) * MULT2
            count += 1
        }
        end = data.size
        return hash
    }

    fun isEos(): Boolean {
        val idx = start
        val d = data
        return end - start == 4 &&
                d[idx + 0].toInt() == 'E'.code &&
                d[idx + 1].toInt() == 'O'.code &&
                d[idx + 2].toInt() == 'S'.code &&
                d[idx + 3].toInt() == '\n'.code
    }
}