package com.worksap.nlp.sudachi.diff

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

    @Test
    fun diffEmpty() {
        val left = listOf(token("a"), token(""), SudachiToken.EOS)
        val right = listOf(token("A"), SudachiToken.EOS)
        val diff = DiffCalculator(left, right).compute()
        assertEquals(1, diff.size)
        assertIs<Both>(diff[0]).let {
            assertEquals(2, it.left.size)
            assertEquals(1, it.right.size)
        }
    }

    @Test
    fun bug01() {
        val data = Diff("""０８	08	レイハチ	名詞	数詞	*	*	*	*
ＢＤ	BD	ビーディー	名詞	普通名詞	一般	*	*	*
−	−	キゴウ	補助記号	一般	*	*	*	*
四十八	四十八	シトヤ	名詞	固有名詞	人名	名	*	*
Ｂ０Ｂ	十		補助記号	一般	*	*	*	*
六百九十八	六百九十八	ロクヒャクキュウジュウハチ	名詞	数詞	*	*	*	*
EOS
""".trimIndent(),
        """０８	08	レイハチ	名詞	数詞	*	*	*	*
ＢＤ	BD	ビーディー	名詞	普通名詞	一般	*	*	*
−	−	キゴウ	補助記号	一般	*	*	*	*
四十八	四十八	ヨンジュウハチ	名詞	数詞	*	*	*	*
Ｂ	b		名詞	普通名詞	一般	*	*	*
０	0		ゼロ	名詞	数詞	*	*	*	*
Ｂ	b		名詞	普通名詞	一般	*	*	*
六百九十八	六百九十八	ロクヒャクキュウジュウハチ	名詞	数詞	*	*	*	*
EOS
""".trimIndent())
        val spans = data.computeSpans()
        assertEquals(4, spans.size)
        assertIs<Equal>(spans[0]).let { assertEquals(3, it.tokens.size) }
        assertIs<Both>(spans[1]).let {
            assertEquals(1, it.left.size)
            assertEquals(1, it.right.size)
        }
        assertIs<Both>(spans[2]).let {
            assertEquals(1, it.left.size)
            assertEquals(3, it.right.size)
        }
        assertIs<Equal>(spans[3]).let { assertEquals(1, it.tokens.size) }
    }
}
