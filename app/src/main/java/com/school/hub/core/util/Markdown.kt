package com.school.hub.core.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

fun markdown(src: String, codeBg: Color = Color(0xFF228888)) = buildAnnotatedString {
    val text = src.replace("\\\\(", "(").replace("\\\\)", ")").replace("\\\\[", "[").replace("\\\\]", "]")
        .replace("\\\\cdot", "·").replace("\\\\times", "×").replace("\\\\div", "÷").replace("\\\\sqrt", "√")
    text.lines().forEachIndexed { i, raw ->
        if (i > 0) append('\n')
        var line = raw
        val heading = line.startsWith("#")
        if (heading) line = line.trimStart('#').trim()
        if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.substring(2)
        if (heading) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(line, codeBg) } else inline(line, codeBg)
    }
}

private fun AnnotatedString.Builder.inline(s: String, codeBg: Color) {
    var i = 0
    while (i < s.length) when {
        s.startsWith("**", i) -> { val e=s.indexOf("**",i+2); if(e<0){append(s.substring(i));return}; withStyle(SpanStyle(fontWeight=FontWeight.Bold)){append(s.substring(i+2,e))}; i=e+2 }
        s[i]=='`' -> { val e=s.indexOf('`',i+1); if(e<0){append(s.substring(i));return}; withStyle(SpanStyle(fontFamily=FontFamily.Monospace,background=codeBg)){append(s.substring(i+1,e))}; i=e+1 }
        else -> { append(s[i]); i++ }
    }
}
