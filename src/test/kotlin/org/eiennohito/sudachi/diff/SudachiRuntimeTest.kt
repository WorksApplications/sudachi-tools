package org.eiennohito.sudachi.diff

import org.eiennohito.sudachi.diff.iface.SudachiRuntime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SudachiRuntimeTest {
    @Test
    fun runtimeWorks() {
        val runtime = SudachiRuntime(SudachiRuntimeConfig(null, null, null, null, addSettings = """{"systemDict": "d:/dev/rust/sudachi.rs/resources/system.dic"}"""))
        assertNotNull(runtime)
    }

    @Test
    fun wordInfoWorks() {
        val runtime = SudachiRuntime(SudachiRuntimeConfig(null, null, null, null, addSettings = """{"systemDict": "d:/dev/rust/sudachi.rs/resources/system.dic"}"""))
        val tokenizer = runtime.sudachiDic.create()
        val morphemes = tokenizer.tokenize("test")
        val wi = runtime.wordInfoOf(morphemes[0])
        assertEquals(4, wi.length)
    }
}