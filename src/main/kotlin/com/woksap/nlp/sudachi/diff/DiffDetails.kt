package com.woksap.nlp.sudachi.diff

import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.math.min

data class SentenceDiffState(val parts: List<DiffSpan>)

class DiffDetails(private val outputPath: Path, private val resources: Path) {
    private val stored = ArrayList<SentenceDiffState>()
    private val result = ArrayList<ProcessedSpan>()

    companion object {
        inline fun SPAN.renderTokens(
            tokens: List<SudachiToken>,
            block: SPAN.(SudachiToken) -> Unit,
            separator: SPAN.() -> Unit
        ) {
            tokens.forEachIndexed { idx, token ->
                block(this, token)
                if (idx != tokens.size - 1) {
                    separator(this)
                }
            }
        }

        fun DIV.renderDiff(left: List<SudachiToken>, right: List<SudachiToken>, level: Int) {
            if (level == 0) {
                span("diff-lvl diff-lvl-0") {
                    +"["
                    span("diff-left") {
                        renderTokens(left, { +it.surface }, { span("tok-sep") { +"¦" } })
                    }
                    +"/"
                    span("diff-right") {
                        renderTokens(right, { +it.surface }, { span("tok-sep") { +"¦" } })
                    }
                    +"]"
                }
            } else {
                span("diff-lvl diff-lvl-$level") {
                    +"["
                    renderTokens(left, { +it.surface }, { span("tok-sep") { +"¦" } })
                    + " $level: "
                    span("diff-left") {
                        renderTokens(left, { +it.component(level) }, { span("tok-sep") { +"¦" } })
                    }
                    +"/"
                    span("diff-right") {
                        renderTokens(right, { +it.component(level) }, { span("tok-sep") { +"¦" } })
                    }
                    +"]"
                }
            }
        }
    }

    private fun writeHtml() {
        if (stored.isEmpty()) {
            return
        }

        outputPath.parent.createDirectories()
        outputPath.deleteIfExists()

        val out =
            outputPath.bufferedWriter(Charsets.UTF_8, 32 * 1024, StandardOpenOption.CREATE, StandardOpenOption.WRITE)

        out.appendHTML().html {
            lang = "ja"
            head {
                link {
                    rel = "stylesheet"
                    href = "$resources/style.css"
                }
            }
            body {
                div(classes = "sentence-diffs") {
                    stored.forEachIndexed { sidx, sent ->
                        val sentId = "s-%08d".format(sidx)
                        div(classes = "sentence-diff") {
                            id = sentId
                            sent.parts.forEachIndexed { pidx, span ->
                                when (span) {
                                    is Both -> {
                                        val lvl = diffLevel(span.left, span.right)
                                        renderDiff(span.left, span.right, lvl)
                                        result.add(ProcessedSpan(sentId, span, lvl))
                                    }
                                    is Equal -> renderSame(span.tokens)
                                    else -> throw IllegalStateException("should not happen")
                                }
                            }
                        }
                    }
                }
            }
        }

        out.close()
    }

    private fun DIV.renderSame(tokens: List<SudachiToken>) {
        span(classes = "diff-same") {
            tokens.forEach { t -> +t.surface }
        }
    }


    private fun diffLevel(left: List<SudachiToken>, right: List<SudachiToken>): Int {
        if (left.size != right.size) {
            return 0
        }

        var level = Int.MAX_VALUE

        left.zip(right).forEach { (a, b) ->
            level = min(level, a.diffLevel(b))
        }
        return level
    }


    fun addDiff(spans: List<DiffSpan>) {
        stored.add(SentenceDiffState(spans))
    }

    fun makePack(): DiffPack {
        writeHtml()
        return DiffPack(result, outputPath)
    }

}
