package com.woksap.nlp.sudachi.diff

import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import com.woksap.nlp.sudachi.diff.DiffDetails.Companion.renderDiff
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo

class DiffStatistics(private val outputRoot: Path) {
    private data class SpanLocation(val path: Path, val id: String)

    private class LevelledComparison(private val data: Both, private val level: Int) {
        val left by data::left
        val right by data::right

        private fun compareLists(left: List<SudachiToken>, right: List<SudachiToken>): Boolean {
            if (left.size != right.size) {
                return false
            }
            for (i in left.indices) {
                val l = left[i]
                val r = right[i]
                if (l.component(level) != r.component(level)) {
                    return false
                }
            }
            return true
        }

        override fun equals(other: Any?): Boolean {
            val o = other as? LevelledComparison ?: return false
            if (!compareLists(left, o.left)) {
                return false
            }

            if (!compareLists(right, o.right)) {
                return false
            }

            return true
        }

        private val cachedHashCode: Int = computeHashCode()
        override fun hashCode(): Int = cachedHashCode
        private fun computeHashCode(): Int {
            var code = 0xdeadbeeffeedL
            code = code xor (left.size.hashCode().toLong() * 31)
            left.forEach { code = code xor (it.component(level).hashCode() * 0xfeedL) }
            code = code xor (right.size.hashCode().toLong() * 31)
            right.forEach { code = code xor (it.component(level).hashCode() * 0xfeedL) }
            return (code.toInt()).xor(code.ushr(32).toInt())
        }
    }

    private val byLevel = run {
        Array(SudachiToken.MAX_LEVEL) {HashMap<LevelledComparison, MutableList<SpanLocation>>() }
    }

    fun handle(pack: DiffPack) {
        pack.diffs.forEach { span ->
            val location = SpanLocation(pack.path, span.id)
            val key = LevelledComparison(span.span, span.level)
            byLevel[span.level].computeIfAbsent(key) { ArrayList() }.add(location)
        }
    }

    fun writeStatistics() {
        val index = outputRoot.resolve("index.html")
        index.outputStream(CREATE, WRITE, TRUNCATE_EXISTING).use { os ->
            os.bufferedWriter().use {
                it.appendHTML().html {
                    lang = "ja"
                    head {
                        title("Sudachi Diff")
                        link {
                            rel = "stylesheet"
                            href = "resources/style.css"
                        }
                    }
                    body {
                        writeBody()
                    }
                }
            }
        }
    }

    private fun BODY.writeBody() {
        div ("index-wrapper") {
            div ("index-nav") {
                ul("index-nav-list") {
                    for (level in 0 until SudachiToken.MAX_LEVEL) {
                        val entries = byLevel[level]
                        if (entries.isEmpty()) {
                            continue
                        }

                        val totalEntries = entries.values.sumOf { it.size }

                        li("index-nav-item") {
                            a("#level-$level") { +SudachiToken.LevelNames[level] }
                            +" ($totalEntries)"
                        }
                    }
                }
            }
            div ("index-content") {
                for (level in 0 until SudachiToken.MAX_LEVEL) {
                    val entries = byLevel[level]
                    if (entries.isEmpty()) {
                        continue
                    }

                    val requestedNum = when (level) {
                        0 -> 500
                        else -> 100
                    }

                    div("level-content level-content-$level") {
                        id = "level-$level"
                        h2 { +"Level $level (${SudachiToken.LevelNames[level]})" }

                        val sorted = entries.toList().sortedByDescending { it.second.size }
                        for ((span, locations) in sorted.take(requestedNum)) {
                             renderItem(span, locations, level)
                        }
                    }
                }
            }
        }
    }

    private fun DIV.renderItem(span: LevelledComparison, locations: List<SpanLocation>, level: Int) {
        div("summary-item") {
            renderDiff(span.left, span.right, level)
            div("summary-links") {
                +"(${locations.size}):"
                locations.take(20).forEachIndexed { idx, loc ->
                    val ref = loc.path.relativeTo(outputRoot)
                    val url = ref.toString().replace("\\", "/") + "#${loc.id}"
                    a(href = url, target = "_blank") { +"${idx + 1}" }
                }
            }
        }
    }
}
