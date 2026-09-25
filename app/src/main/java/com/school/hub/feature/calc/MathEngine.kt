package com.school.hub.feature.calc

import kotlin.math.*

/** Выражение → дерево → пошаговое решение. Поддержка: + − × ÷ ^ % ! √, скобки (), [], {}, sin cos tan ln log, π, e, неявное умножение 2(3+4). */
sealed interface Node
data class Num(val v: Double) : Node
data class Bin(val op: Char, val a: Node, val b: Node) : Node
data class Un(val op: String, val a: Node) : Node

class MathError(msg: String) : Exception(msg)

data class Solution(val expression: String, val steps: List<String>, val result: Double) {
    val resultText: String get() = fmt(result)
}

object MathEngine {
    fun solve(input: String): Solution {
        val src = normalize(input)
        if (src.isBlank()) throw MathError("Пустой пример")
        var node = Parser(src).parse()
        val steps = mutableListOf(render(node))
        var guard = 0
        while (node !is Num && guard++ < 200) {
            node = step(node)
            val r = render(node)
            if (r != steps.last()) steps += r
        }
        val res = (node as Num).v
        if (res.isNaN()) throw MathError("Не определено (например, корень из отрицательного)")
        if (res.isInfinite()) throw MathError("Деление на ноль")
        return Solution(steps.first(), steps, res)
    }

    fun evalOrNull(input: String): Double? = runCatching { solve(input).result }.getOrNull()

    /** Приводим «человеческую»/распознанную запись к машинной. */
    fun normalize(raw: String): String {
        var s = raw.trim()
            .replace('×', '*').replace('·', '*').replace('∙', '*').replace('÷', '/').replace(':', '/')
            .replace('−', '-').replace('–', '-').replace('—', '-').replace(',', '.')
            .replace('[', '(').replace(']', ')').replace('{', '(').replace('}', ')')
            .replace("π", "pi").replace("√", "sqrt")
            .replace("²", "^2").replace("³", "^3")
        // OCR-путаница: буквы вместо цифр/знаков между цифрами
        s = Regex("(?<=[\\d)])\\s*[xXхХ]\\s*(?=[\\d(])").replace(s) { "*" }
        s = Regex("(?<=\\d)[oOоО](?=\\d)|(?<=\\d)[oOоО]\\b").replace(s) { "0" }
        s = Regex("(?<=\\d)[lI|](?=\\d)").replace(s) { "1" }
        s = s.substringBefore('=').trim().trimEnd('?')
        return s.replace(" ", "")
    }

    private fun step(n: Node): Node = when (n) {
        is Num -> n
        is Un -> if (n.a is Num) Num(applyUn(n.op, n.a.v)) else Un(n.op, step(n.a))
        is Bin -> when {
            n.a is Num && n.b is Num -> Num(applyBin(n.op, n.a.v, n.b.v))
            n.a !is Num -> Bin(n.op, step(n.a), n.b)
            else -> Bin(n.op, n.a, step(n.b))
        }
    }

    private fun applyBin(op: Char, a: Double, b: Double): Double = when (op) {
        '+' -> a + b; '-' -> a - b; '*' -> a * b
        '/' -> if (b == 0.0) throw MathError("Деление на ноль") else a / b
        '^' -> a.pow(b)
        else -> throw MathError("Неизвестная операция $op")
    }

    private fun applyUn(op: String, a: Double): Double = when (op) {
        "-" -> -a
        "%" -> a / 100
        "!" -> if (a < 0 || a != floor(a) || a > 170) throw MathError("Факториал только для целых 0..170") else (1..a.toInt()).fold(1.0) { x, i -> x * i }
        "sqrt" -> if (a < 0) throw MathError("Корень из отрицательного числа") else sqrt(a)
        "sin" -> sin(Math.toRadians(a)); "cos" -> cos(Math.toRadians(a)); "tan" -> tan(Math.toRadians(a))
        "ln" -> ln(a); "log" -> log10(a); "abs" -> abs(a)
        else -> throw MathError("Неизвестная функция $op")
    }

    private fun prec(n: Node): Int = when (n) {
        is Num -> if (n.v < 0) 2 else 9
        is Un -> when (n.op) { "-" -> 2; "%", "!" -> 8; else -> 9 }
        is Bin -> when (n.op) { '+', '-' -> 1; '*', '/' -> 3; else -> 5 }
    }

    fun render(n: Node): String = when (n) {
        is Num -> fmt(n.v)
        is Un -> when (n.op) {
            "-" -> "−" + wrap(n.a, 3)
            "%", "!" -> wrap(n.a, 9) + n.op
            "sqrt" -> "√(" + render(n.a) + ")"
            else -> n.op + "(" + render(n.a) + ")"
        }
        is Bin -> {
            val p = prec(n)
            val left = wrap(n.a, if (n.op == '^') p + 1 else p)
            val right = wrap(n.b, if (n.op == '-' || n.op == '/' || n.op == '^') p + 1 else p)
            val sym = when (n.op) { '*' -> "×"; '/' -> "÷"; '-' -> "−"; else -> n.op.toString() }
            if (n.op == '^') "$left^$right" else "$left $sym $right"
        }
    }

    private fun wrap(n: Node, need: Int) = if (prec(n) < need) "(" + render(n) + ")" else render(n)
}

fun fmt(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    val r = (v * 1e10).roundToLong() / 1e10
    return if (r == floor(r) && abs(r) < 1e15) r.toLong().toString()
    else "%.10f".format(java.util.Locale.US, r).trimEnd('0').trimEnd('.')
}

private class Parser(private val s: String) {
    private var i = 0

    fun parse(): Node {
        val n = expr()
        if (i < s.length) throw MathError("Непонятный символ «${s[i]}» на позиции ${i + 1}")
        return n
    }

    private fun peek() = if (i < s.length) s[i] else '\u0000'

    private fun expr(): Node {
        var n = term()
        while (peek() == '+' || peek() == '-') { val op = s[i++]; n = Bin(op, n, term()) }
        return n
    }

    private fun term(): Node {
        var n = unary()
        while (true) {
            val c = peek()
            n = when {
                c == '*' || c == '/' -> { i++; Bin(c, n, unary()) }
                c == '(' || c.isLetter() || c.isDigit() || c == '.' -> Bin('*', n, unary()) // неявное умножение
                else -> return n
            }
        }
    }

    private fun unary(): Node = when (peek()) {
        '-' -> { i++; Un("-", unary()) }
        '+' -> { i++; unary() }
        else -> power()
    }

    private fun power(): Node {
        val base = postfix()
        return if (peek() == '^') { i++; Bin('^', base, unary()) } else base
    }

    private fun postfix(): Node {
        var n = atom()
        while (peek() == '%' || peek() == '!') n = Un(s[i++].toString(), n)
        return n
    }

    private fun atom(): Node {
        val c = peek()
        return when {
            c == '(' -> {
                i++
                val n = expr()
                if (peek() != ')') throw MathError("Не хватает закрывающей скобки «)»")
                i++; n
            }
            c.isDigit() || c == '.' -> {
                val st = i
                while (peek().isDigit() || peek() == '.') i++
                s.substring(st, i).toDoubleOrNull()?.let { Num(it) } ?: throw MathError("Неверное число «${s.substring(st, i)}»")
            }
            c.isLetter() -> {
                val st = i
                while (peek().isLetter()) i++
                when (val w = s.substring(st, i).lowercase()) {
                    "pi" -> Num(PI)
                    "e" -> Num(E)
                    "sqrt", "sin", "cos", "tan", "tg", "ln", "log", "lg", "abs" -> {
                        val f = when (w) { "tg" -> "tan"; "lg" -> "log"; else -> w }
                        Un(f, if (peek() == '(') atom() else postfix())
                    }
                    else -> throw MathError("Неизвестное слово «$w»")
                }
            }
            c == ')' -> throw MathError("Лишняя закрывающая скобка")
            c == '\u0000' -> throw MathError("Пример обрывается — допиши его")
            else -> throw MathError("Непонятный символ «$c»")
        }
    }
}
