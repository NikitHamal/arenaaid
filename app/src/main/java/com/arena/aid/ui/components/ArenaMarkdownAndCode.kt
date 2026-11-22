package com.arena.aid.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arena.aid.ui.theme.ArenaCodeTextStyle

sealed interface MarkdownSegment {
    data class Paragraph(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String, val filename: String?, val code: String) : MarkdownSegment
}

fun parseMarkdownSegments(raw: String): List<MarkdownSegment> {
    if (raw.isBlank()) return emptyList()
    val segments = mutableListOf<MarkdownSegment>()
    val fenceRegex = Regex("```([a-zA-Z0-9_+.-]*)(?:\\s+([^\\n`]+))?\\n([\\s\\S]*?)(?:```|$)")
    var lastIndex = 0
    for (match in fenceRegex.findAll(raw)) {
        if (match.range.first > lastIndex) {
            val textBefore = raw.substring(lastIndex, match.range.first).trim()
            if (textBefore.isNotEmpty()) {
                segments.add(MarkdownSegment.Paragraph(textBefore))
            }
        }
        val lang = match.groupValues[1].trim().ifEmpty { "code" }
        val fileHint = match.groupValues[2].trim().ifEmpty { null }
        val codeContent = match.groupValues[3].trimEnd()
        segments.add(MarkdownSegment.CodeBlock(lang, fileHint, codeContent))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < raw.length) {
        val remaining = raw.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            segments.add(MarkdownSegment.Paragraph(remaining))
        }
    }
    return segments
}

private val KeywordRegex = Regex(
    "\\b(fun|val|var|class|object|interface|package|import|return|if|else|when|for|while|suspend|override|private|public|internal|data|sealed|enum|const|async|await|function|export|default|from|let|def|self|None|True|False|SELECT|FROM|WHERE|INSERT|UPDATE|CREATE|TABLE)\\b"
)
private val StringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
private val CommentRegex = Regex("//.*$|#.*$", RegexOption.MULTILINE)

@Composable
fun highlightSyntax(
    code: String,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    baseColor: Color,
    lowRamPlain: Boolean = false
): AnnotatedString {
    return remember(code, keywordColor, stringColor, commentColor, baseColor, lowRamPlain) {
        if (lowRamPlain || code.length > 10_000) {
            AnnotatedString(code)
        } else {
            buildAnnotatedString {
                withStyle(SpanStyle(color = baseColor)) {
                    append(code)
                }
                for (m in KeywordRegex.findAll(code)) {
                    addStyle(
                        SpanStyle(color = keywordColor, fontWeight = FontWeight.W600),
                        m.range.first,
                        m.range.last + 1
                    )
                }
                for (m in StringRegex.findAll(code)) {
                    addStyle(
                        SpanStyle(color = stringColor),
                        m.range.first,
                        m.range.last + 1
                    )
                }
                for (m in CommentRegex.findAll(code)) {
                    addStyle(
                        SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
                        m.range.first,
                        m.range.last + 1
                    )
                }
            }
        }
    }
}

@Composable
fun ArenaReasoningDrawer(
    reasoning: String,
    durationMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    if (reasoning.isBlank()) return
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = if (durationMs > 0) "Reasoning trace (${durationMs / 1000.0}s)" else "Reasoning trace",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = reasoning.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ArenaCodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    filename: String? = null,
    showLineNumbers: Boolean = true,
    lowRamMode: Boolean = false,
    onOpenInCodeArena: ((String, String, String?) -> Unit)? = null,
    onCopied: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val lines = remember(code) { code.lines() }
    val highlighted = highlightSyntax(
        code = code,
        keywordColor = scheme.primary,
        stringColor = scheme.tertiary,
        commentColor = scheme.outline,
        baseColor = scheme.onSurface,
        lowRamPlain = lowRamMode
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (language == "bash" || language == "sh") Icons.Outlined.Terminal else Icons.Outlined.Code,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = filename ?: language.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface
                )
                Text(
                    text = "${lines.size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onOpenInCodeArena != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.secondaryContainer)
                            .clickable { onOpenInCodeArena(language, code, filename) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Open in Code mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSecondaryContainer
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceContainerLowest)
                        .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable {
                            copyToClipboard(context, code)
                            onCopied?.invoke("Copied code block")
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurface
                    )
                }
            }
        }
        SelectionContainer {
            Row(
                modifier = Modifier
                    .arenaHorizontalScroll()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (showLineNumbers && lines.size > 1) {
                    val lineNums = remember(lines.size) {
                        (1..lines.size).joinToString("\n") { it.toString().padStart(2, ' ') }
                    }
                    Text(
                        text = lineNums,
                        style = ArenaCodeTextStyle,
                        color = scheme.outline,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Text(
                    text = highlighted,
                    style = ArenaCodeTextStyle
                )
            }
        }
    }
}

@Composable
fun ArenaMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    lowRamMode: Boolean = false,
    onOpenInCodeArena: ((String, String, String?) -> Unit)? = null,
    onCopied: ((String) -> Unit)? = null
) {
    val segments = remember(markdown) { parseMarkdownSegments(markdown) }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Paragraph -> {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            segment.text.lines().forEach { line ->
                                val trimmed = line.trim()
                                when {
                                    trimmed.startsWith("### ") -> Text(
                                        text = trimmed.removePrefix("### "),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = scheme.onSurface
                                    )
                                    trimmed.startsWith("## ") -> Text(
                                        text = trimmed.removePrefix("## "),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = scheme.onSurface
                                    )
                                    trimmed.startsWith("# ") -> Text(
                                        text = trimmed.removePrefix("# "),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = scheme.onSurface
                                    )
                                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                                                color = scheme.primary
                                            )
                                            Text(
                                                text = formatInlineMarkdown(trimmed.substring(2), scheme.primary, scheme.surfaceContainerHigh),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = scheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    trimmed.isNotEmpty() -> {
                                        Text(
                                            text = formatInlineMarkdown(trimmed, scheme.primary, scheme.surfaceContainerHigh),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = scheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is MarkdownSegment.CodeBlock -> {
                    ArenaCodeBlockCard(
                        language = segment.language,
                        code = segment.code,
                        filename = segment.filename,
                        lowRamMode = lowRamMode,
                        onOpenInCodeArena = onOpenInCodeArena,
                        onCopied = onCopied
                    )
                }
            }
        }
    }
}

private fun formatInlineMarkdown(
    line: String,
    boldColor: Color,
    codeBgColor: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        if (line.startsWith("**", i)) {
            val end = line.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.W600, color = boldColor)) {
                    append(line.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (line[i] == '`') {
            val end = line.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        background = codeBgColor
                    )
                ) {
                    append(" " + line.substring(i + 1, end) + " ")
                }
                i = end + 1
                continue
            }
        }
        append(line[i])
        i++
    }
}

fun copyToClipboard(context: Context, text: String, label: String = "Arena") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}
