package com.school.hub.core.util

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Fence(val language: String, val code: String)

private val keyword = Regex("\\b(fun|val|var|class|if|else|when|for|while|return|def|import|from|function|const|let|new|public|private|static|void|int|string|SELECT|FROM|WHERE|INSERT|UPDATE|true|false|null)\\b")
private val string = Regex("(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')")
private val comment = Regex("(?m)(//.*$|#.*$|--.*$|/\\*.*?\\*/)")
private val number = Regex("\\b\\d+(?:\\.\\d+)?\\b")

fun parseMarkdownFences(text: String): List<Any> {
    val result = mutableListOf<Any>()
    var plain = StringBuilder()
    var open = false
    var lang = ""
    var code = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            result += plain.toString()
            plain = StringBuilder()
        }
    }
    text.lines().forEach { line ->
        if (!open && line.trimStart().startsWith("```")) {
            flush(); open = true; lang = line.trim().removePrefix("```").trim(); return@forEach
        }
        if (open && line.trimStart().startsWith("```")) {
            result += Fence(lang, code.toString().trimEnd('\n')); open = false; code = StringBuilder(); return@forEach
        }
        if (open) code.append(line).append('\n') else plain.append(line).append('\n')
    }
    if (open) result += Fence(lang, code.toString().trimEnd('\n')) else flush()
    return result
}

private fun detect(code: String): String = when {
    code.contains("fun main") || code.contains("val ") -> "Kotlin"
    code.contains("def ") || code.contains("import ") -> "Python"
    code.contains("<html") -> "HTML"
    code.contains("SELECT ", true) -> "SQL"
    code.contains("#!/bin/") -> "Bash"
    code.contains("=>") || code.contains("console.") -> "JavaScript"
    else -> "Код"
}

private fun highlighted(code: String, colors: ColorScheme): AnnotatedString = buildAnnotatedString {
    val spans = (
        comment.findAll(code).map { it.range to colors.outline }.toList() +
        string.findAll(code).map { it.range to colors.tertiary }.toList() +
        keyword.findAll(code).map { it.range to colors.primary }.toList() +
        number.findAll(code).map { it.range to colors.secondary }.toList()
    ).sortedBy { it.first.first }
    var at = 0
    spans.forEach { (r, c) ->
        if (r.first < at) return@forEach
        append(code.substring(at, r.first))
        pushStyle(SpanStyle(color = c))
        append(code.substring(r))
        pop()
        at = r.last + 1
    }
    if (at < code.length) append(code.substring(at))
}

@Composable
fun MarkdownWithCode(text: String, modifier: Modifier = Modifier, onCopied: () -> Unit = {}) {
    val parts = remember(text) { parseMarkdownFences(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            when (part) {
                is String -> if (part.isNotBlank()) Text(markdown(part.trimEnd()), style = MaterialTheme.typography.bodyLarge)
                is Fence -> CodeCard(part, onCopied)
            }
        }
    }
}

@Composable
private fun CodeCard(fence: Fence, onCopied: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    var wrap by remember(fence.code) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val text = remember(fence.code, colors) { highlighted(fence.code, colors) }
    val lineNumbers = remember(fence.code) { fence.code.lines().indices.joinToString("\n") { (it + 1).toString() } }
    val title = fence.language.ifBlank { detect(fence.code) }
    Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHighest)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 12.dp))
                Row {
                    TextButton(onClick = { wrap = !wrap }) { Text(if (wrap) "Без переноса" else "Перенос") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(fence.code)); onCopied() }) {
                        Icon(Icons.Filled.ContentCopy, null)
                        Text("Копировать")
                    }
                }
            }
            SelectionContainer {
                val rowModifier = if (wrap) Modifier.fillMaxWidth().padding(12.dp) else Modifier.fillMaxWidth().horizontalScroll(scroll).padding(12.dp)
                Row(rowModifier) {
                    if (!wrap) Text(lineNumbers, color = colors.outline, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = wrap)
                }
            }
        }
    }
}
