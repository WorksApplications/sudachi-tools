package org.eiennohito.sudachi.diff

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class DiffCandidateCalculatorTest {
    @Test
    fun noDiff() {
        val data1 = TokenIterator("""
            THIS
            IS
            EOS
            AND
            THIS
            EOS
        """.trimIndent().byteInputStream())
        val data2 = TokenIterator("""
            THIS
            IS
            EOS
            AND
            THIS
            EOS
        """.trimIndent().byteInputStream())
        val diffcalc = DiffCandidateCalculator(data1, data2)
        assertEquals(NoDiff, diffcalc.processOne())
        assertEquals(NoDiff, diffcalc.processOne())
        assertEquals(Finished, diffcalc.processOne())
    }

    @Test
    fun hasDiff() {
        val data1 = TokenIterator("""
            THIS
            ARE
            EOS
            AND
            THIS
            EOS
        """.trimIndent().byteInputStream())
        val data2 = TokenIterator("""
            THIS
            IS
            EOS
            AND
            THIS
            EOS
        """.trimIndent().byteInputStream())
        val diffcalc = DiffCandidateCalculator(data1, data2)
        assertEquals(Diff("THIS\nARE\nEOS\n", "THIS\nIS\nEOS\n"), diffcalc.processOne())
        assertEquals(NoDiff, diffcalc.processOne())
        assertEquals(Finished, diffcalc.processOne())
    }
}

private fun token(data: String): SudachiToken = SudachiToken(data, data, data, data, data, data, data, data, data)

class DiffCalculatorTest {
    @Test
    fun diffEq() {
        val left = listOf(token("a"), token("b"), token("c"))
        val right = listOf(token("a"), token("b"), token("c"))
        val diff = DiffCalculator(left, right).compute()
        assertEquals(1, diff.size)
        val el = assertIs<Equal>(diff[0])
        assertEquals(3, el.tokens.size)
    }

    @Test
    fun diffStart() {
        val left = listOf(token("a"), token("b"), token("c"))
        val right = listOf(token("ab"), token("c"))
        val diff = DiffCalculator(left, right).compute()
        assertEquals(2, diff.size)
        val el1 = assertIs<Both>(diff[0])
        assertEquals(2, el1.left.size)
        assertEquals(1, el1.right.size)
        val el2 = assertIs<Equal>(diff[1])
        assertEquals(1, el2.tokens.size)
    }

    @Test
    fun diffEnd() {
        val left = listOf(token("a"), token("bc"))
        val right = listOf(token("a"), token("b"), token("c"))
        val diff = DiffCalculator(left, right).compute()
        assertEquals(2, diff.size)
        val el0 = assertIs<Equal>(diff[0])
        assertEquals(1, el0.tokens.size)
        val el1 = assertIs<Both>(diff[1])
        assertEquals(1, el1.left.size)
        assertEquals(2, el1.right.size)
    }

    @Test
    fun diffMiddle() {
        val left = listOf(token("a"), token("bc"), token("d"))
        val right = listOf(token("a"), token("b"), token("c"), token("d"))
        val diff = DiffCalculator(left, right).compute()
        assertEquals(3, diff.size)
        val el0 = assertIs<Equal>(diff[0])
        assertEquals(1, el0.tokens.size)
        val el1 = assertIs<Both>(diff[1])
        assertEquals(1, el1.left.size)
        assertEquals(2, el1.right.size)
        val el2 = assertIs<Equal>(diff[2])
        assertEquals(1, el2.tokens.size)
    }

    @Test
    fun diffLong() {
        val left = listOf(token("a"), token("bc"), token("d"))
        val right = listOf(token("a"), token("b"), token("cd"))
        val diff = DiffCalculator(left, right).compute()
        assertEquals(2, diff.size)
        val el0 = assertIs<Equal>(diff[0])
        assertEquals(1, el0.tokens.size)
        val el1 = assertIs<Both>(diff[1])
        assertEquals(2, el1.left.size)
        assertEquals(2, el1.right.size)
    }
}
