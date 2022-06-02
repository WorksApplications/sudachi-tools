package com.worksap.nlp.sudachi.diff

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TokenIteratorTest {
    @Test
    fun detectSentenceBoundaries() {
        val data = """
            HELLO
            WORLD
            EOS
            WORLD
            HELLO
            EOS
        """.trimIndent().byteInputStream()
        val siter = TokenIterator(data)
        siter.maybeRefill()
        assertNotEquals(siter.nextTokenHash(), 0)
        assertNotEquals(siter.nextTokenHash(), 0)
        assertEquals(siter.nextTokenHash(), TokenIterator.EOS_HASH)
        assertEquals(6 + 6 + 4, siter.position)
        assertEquals(12, siter.lineStart)
        assertEquals(16, siter.lineEnd)
    }
}