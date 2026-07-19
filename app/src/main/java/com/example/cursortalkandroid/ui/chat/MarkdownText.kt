package com.example.cursortalkandroid.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class Table(val rows: List<List<String>>) : MarkdownBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.padding(top = 3.dp))
            when (block) {
                is MarkdownBlock.Paragraph -> InlineMarkdownText(block.text, color = color)
                is MarkdownBlock.Heading -> InlineMarkdownText(
                    text = block.text,
                    color = color,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                )
                is MarkdownBlock.ListItem -> Row {
                    Text("${block.marker} ", color = color)
                    InlineMarkdownText(block.text, color = color, modifier = Modifier.weight(1f))
                }
                is MarkdownBlock.Quote -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                ) {
                    Text("│ ", color = MaterialTheme.colorScheme.primary)
                    InlineMarkdownText(block.text, color = color, modifier = Modifier.weight(1f))
                }
                is MarkdownBlock.Code -> Text(
                    text = block.text,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                )
                is MarkdownBlock.Table -> MarkdownTable(block.rows, color)
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val annotated = remember(text) { buildInlineMarkdown(text) }
    Text(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = color),
    )
}

@Composable
private fun MarkdownTable(rows: List<List<String>>, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row {
                row.forEach { cell ->
                    InlineMarkdownText(
                        text = cell,
                        color = color,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                        ),
                        modifier = Modifier
                            .widthIn(min = 96.dp, max = 240.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        when {
            line.isBlank() -> index++
            line.startsWith("```") -> {
                index++
                val code = mutableListOf<String>()
                while (index < lines.size && !lines[index].startsWith("```")) {
                    code += lines[index++]
                }
                if (index < lines.size) index++
                blocks += MarkdownBlock.Code(code.joinToString("\n"))
            }
            HEADING.matches(line) -> {
                val match = HEADING.matchEntire(line)!!
                blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
                index++
            }
            index + 1 < lines.size && isTableSeparator(lines[index + 1]) && line.contains('|') -> {
                val rows = mutableListOf(parseTableRow(line))
                index += 2
                while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                    rows += parseTableRow(lines[index++])
                }
                blocks += MarkdownBlock.Table(rows)
            }
            UNORDERED_LIST.matches(line) -> {
                val match = UNORDERED_LIST.matchEntire(line)!!
                blocks += MarkdownBlock.ListItem("•", match.groupValues[1])
                index++
            }
            ORDERED_LIST.matches(line) -> {
                val match = ORDERED_LIST.matchEntire(line)!!
                blocks += MarkdownBlock.ListItem("${match.groupValues[1]}.", match.groupValues[2])
                index++
            }
            line.trimStart().startsWith(">") -> {
                blocks += MarkdownBlock.Quote(line.trimStart().removePrefix(">").trimStart())
                index++
            }
            else -> {
                val paragraph = mutableListOf(line.trim())
                index++
                while (index < lines.size && lines[index].isNotBlank() && !isBlockStart(lines, index)) {
                    paragraph += lines[index++].trim()
                }
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            }
        }
    }
    return blocks
}

private fun isBlockStart(lines: List<String>, index: Int): Boolean {
    val line = lines[index]
    return line.startsWith("```") ||
        HEADING.matches(line) ||
        UNORDERED_LIST.matches(line) ||
        ORDERED_LIST.matches(line) ||
        line.trimStart().startsWith(">") ||
        (index + 1 < lines.size && line.contains('|') && isTableSeparator(lines[index + 1]))
}

private fun parseTableRow(line: String): List<String> = line.trim()
    .removePrefix("|")
    .removeSuffix("|")
    .split('|')
    .map(String::trim)

private fun isTableSeparator(line: String): Boolean = parseTableRow(line)
    .takeIf { it.isNotEmpty() }
    ?.all { cell -> cell.matches(Regex(":?-{3,}:?")) }
    ?: false

private fun buildInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end >= 0) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(text[index++])
                }
            }
            text[index] == '*' -> {
                val end = text.indexOf('*', index + 1)
                if (end >= 0) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index++])
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end >= 0) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x22000000),
                        ),
                    )
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index++])
                }
            }
            text[index] == '[' -> {
                val labelEnd = text.indexOf(']', index + 1)
                val urlStart = if (labelEnd >= 0) labelEnd + 1 else -1
                val urlEnd = if (urlStart < text.length && text[urlStart] == '(') {
                    text.indexOf(')', urlStart + 1)
                } else {
                    -1
                }
                if (labelEnd >= 0 && urlEnd >= 0) {
                    val label = text.substring(index + 1, labelEnd)
                    val url = text.substring(urlStart + 1, urlEnd)
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = Color(0xFF1565C0)),
                            ),
                        ),
                    ) {
                        append(label)
                    }
                    index = urlEnd + 1
                } else {
                    append(text[index++])
                }
            }
            else -> append(text[index++])
        }
    }
}

private val HEADING = Regex("^(#{1,6})\\s+(.+)$")
private val UNORDERED_LIST = Regex("^\\s*[-+*]\\s+(.+)$")
private val ORDERED_LIST = Regex("^\\s*(\\d+)\\.\\s+(.+)$")
