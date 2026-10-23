package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Asistan cevaplarındaki basit Markdown: başlık, madde, numaralı liste, alıntı, kod, tablo, **kalın**, *italik*, `kod`. */
private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Item(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private fun parse(src: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = src.replace("\r", "").lines()
    var i = 0
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += MdBlock.Para(para.toString().trim())
        para.clear()
    }
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()
        when {
            line.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) code.appendLine(lines[i++])
                out += MdBlock.Code(code.toString().trimEnd())
            }
            line.isEmpty() -> flush()
            Regex("^#{1,6}\\s").containsMatchIn(line) -> {
                flush()
                out += MdBlock.Heading(line.takeWhile { it == '#' }.length, line.trimStart('#').trim())
            }
            Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(line) -> {
                flush()
                out += MdBlock.Rule
            }
            line.startsWith("|") && line.endsWith("|") -> {
                flush()
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trim().let { it.startsWith("|") && it.endsWith("|") }) {
                    val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                    if (!cells.all { it.matches(Regex(":?-{2,}:?")) }) rows += cells
                    i++
                }
                out += MdBlock.Table(rows)
                continue
            }
            Regex("^([-*•+])\\s+").containsMatchIn(line) -> {
                flush()
                val indent = (raw.length - raw.trimStart().length) / 2
                out += MdBlock.Item("•", line.replaceFirst(Regex("^[-*•+]\\s+"), ""), indent)
            }
            Regex("^\\d+[.)]\\s+").containsMatchIn(line) -> {
                flush()
                val indent = (raw.length - raw.trimStart().length) / 2
                val n = line.takeWhile { it.isDigit() }
                out += MdBlock.Item("$n.", line.replaceFirst(Regex("^\\d+[.)]\\s+"), ""), indent)
            }
            line.startsWith(">") -> {
                flush()
                out += MdBlock.Quote(line.trimStart('>').trim())
            }
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
        }
        i++
    }
    flush()
    return out
}

/** Satır içi biçim: **kalın**, __kalın__, *italik*, _italik_, `kod`. */
internal fun inlineMarkdown(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val rx = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])|(?<![\\w_])_(?!\\s)(.+?)(?<!\\s)_(?![\\w_])")
    var last = 0
    for (m in rx.findAll(text)) {
        append(text.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() || g[2].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inlineMarkdown(g[1].ifEmpty { g[2] }, codeBg)) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(" ${g[3]} ") }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[4].ifEmpty { g[5] }) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parse(text) }
    val codeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val body = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp, color = color)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Heading -> Text(
                    inlineMarkdown(b.text, codeBg),
                    style = body.copy(fontSize = if (b.level <= 2) 17.sp else 15.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp),
                )
                is MdBlock.Para -> Text(inlineMarkdown(b.text, codeBg), style = body)
                is MdBlock.Item -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text(b.marker, style = body.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary), modifier = Modifier.width(24.dp))
                    Text(inlineMarkdown(b.text, codeBg), style = body)
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                    Text(inlineMarkdown(b.text, codeBg), style = body.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(start = 10.dp))
                }
                is MdBlock.Code -> Text(
                    b.text,
                    style = body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(codeBg)
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
                is MdBlock.Table -> Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(codeBg)
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    b.rows.forEachIndexed { r, cells ->
                        Row {
                            cells.forEach { c ->
                                Text(
                                    inlineMarkdown(c, codeBg),
                                    style = body.copy(fontSize = 14.sp, fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal),
                                    modifier = Modifier.width(140.dp).padding(4.dp),
                                )
                            }
                        }
                    }
                }
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
