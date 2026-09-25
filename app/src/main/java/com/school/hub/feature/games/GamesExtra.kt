package com.school.hub.feature.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

// ==================== общие мелочи ====================

@Composable
private fun GameTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun AgainButton(text: String = "Ещё раз", onClick: () -> Unit) {
    Button(onClick = onClick) { Text(text) }
}

// ---------------- 🎲 Кости ----------------
@Composable
fun DiceGame() {
    var v by remember { mutableIntStateOf(1) }
    var rolls by remember { mutableIntStateOf(0) }
    val faces = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Кости")
        Text(faces[v - 1], fontSize = 96.sp)
        Text("Бросков: $rolls", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { v = (1..6).random(); rolls++ }) { Text("Кинуть 🎲") }
    }
}

// ---------------- 🪙 Орёл и решка ----------------
@Composable
fun CoinGame() {
    var res by remember { mutableStateOf<String?>(null) }
    var heads by remember { mutableIntStateOf(0) }
    var tails by remember { mutableIntStateOf(0) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Орёл и решка")
        Text(res ?: "🪙", fontSize = 96.sp)
        Text("Орлов: $heads · Решек: $tails", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            res = if ((0..1).random() == 0) "🦅" else "💰"
            if (res == "🦅") heads++ else tails++
        }) { Text("Подбросить") }
    }
}

// ---------------- ✊ Камень-ножницы-бумага ----------------
@Composable
fun RpsGame() {
    val items = listOf("✊", "✌️", "🖐")
    var me by remember { mutableStateOf<String?>(null) }
    var bot by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("Выбери жест") }
    var score by remember { mutableStateOf(Triple(0, 0, 0)) } // я, бот, ничьи
    fun beats(a: String, b: String) = (a == "✊" && b == "✌️") || (a == "✌️" && b == "🖐") || (a == "🖐" && b == "✊")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Камень-ножницы-бумага")
        Text("Ты ${score.first} : ${score.second} Бот (ничьих ${score.third})", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(me ?: "❓", fontSize = 56.sp)
            Text("VS", fontWeight = FontWeight.Bold)
            Text(bot ?: "❓", fontSize = 56.sp)
        }
        Text(msg, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { c ->
                Button(onClick = {
                    val b = items.random()
                    me = c; bot = b
                    when {
                        c == b -> { msg = "Ничья 🤝"; score = score.copy(third = score.third + 1) }
                        beats(c, b) -> { msg = "Ты победил! 🎉"; score = score.copy(first = score.first + 1) }
                        else -> { msg = "Бот победил 🤖"; score = score.copy(second = score.second + 1) }
                    }
                }) { Text(c, fontSize = 28.sp) }
            }
        }
    }
}

// ---------------- 🔢 Угадай число ----------------
@Composable
fun GuessGame() {
    var target by remember { mutableIntStateOf((1..100).random()) }
    var text by remember { mutableStateOf("") }
    var tries by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Загадал число от 1 до 100") }
    var won by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Угадай число")
        Text(msg, style = MaterialTheme.typography.titleMedium)
        Text("Попыток: $tries", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            text, { text = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Твой вариант") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
        )
        Button(onClick = {
            val n = text.toIntOrNull() ?: return@Button
            tries++
            when {
                n == target -> { msg = "Угадал за $tries попыток! 🎉"; won = true }
                n < target -> msg = "$n — бери больше ⬆️"
                else -> msg = "$n — бери меньше ⬇️"
            }
            text = ""
        }, enabled = !won) { Text("Проверить") }
        if (won) AgainButton {
            target = (1..100).random(); tries = 0; won = false; msg = "Загадал новое число!"
        }
    }
}

// ---------------- ⚡ Реакция ----------------
@Composable
fun ReactionGame() {
    var phase by remember { mutableIntStateOf(0) } // 0 ждёт старта, 1 жди..., 2 жми!, 3 результат
    var startAt by remember { mutableStateOf(0L) }
    var ms by remember { mutableStateOf(0L) }
    var best by remember { mutableStateOf(0L) }
    LaunchedEffect(phase) {
        if (phase == 1) {
            delay((1000..3000).random().toLong())
            if (phase == 1) { startAt = System.currentTimeMillis(); phase = 2 }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Проверка реакции")
        if (best > 0) Text("Лучшее: $best мс", style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp))
                .background(
                    when (phase) {
                        1 -> Color(0xFFE74C3C)
                        2 -> Color(0xFF2ECC71)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .clickable {
                    when (phase) {
                        0 -> phase = 1
                        1 -> phase = 0 // рано!
                        2 -> { ms = System.currentTimeMillis() - startAt; if (best == 0L || ms < best) best = ms; phase = 3 }
                        3 -> phase = 1
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (phase) {
                    0 -> "Нажми, чтобы начать"
                    1 -> "Жди зелёный…"
                    2 -> "ЖМИ! ⚡"
                    else -> "$ms мс — нажми для повтора"
                },
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
        }
        if (phase == 0 && ms == 0L) Text("Слишком рано нажал? Просто начни заново.", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- 🃏 Пары ----------------
@Composable
fun PairsGame() {
    val pics = listOf("🐶", "🐱", "🦊", "🐼", "🦁", "🐸", "🐵", "🐷")
    var deck by remember { mutableStateOf((pics + pics).shuffled()) }
    var open by remember { mutableStateOf(setOf<Int>()) }
    var done by remember { mutableStateOf(setOf<Int>()) }
    var moves by remember { mutableIntStateOf(0) }
    var lock by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open.size == 2) {
            lock = true
            delay(700)
            val (a, b) = open.toList()
            if (deck[a] == deck[b]) done = done + a + b
            open = emptySet()
            moves++
            lock = false
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Пары")
        Text(if (done.size == 16) "Все пары за $moves ходов! 🎉" else "Ходов: $moves", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..3) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..3) {
                    val i = r * 4 + c
                    val face = i in open || i in done
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (face) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = !face && !lock) { open = open + i },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (face) deck[i] else "❓", fontSize = 32.sp) }
                }
            }
        }
        AgainButton("Перемешать") {
            deck = (pics + pics).shuffled(); open = emptySet(); done = emptySet(); moves = 0
        }
    }
}

// ---------------- 💣 Сапёр ----------------
@Composable
fun MinesGame() {
    val n = 6
    val bombs = 8
    var field by remember { mutableStateOf(sampleMines(n, bombs)) }
    var open by remember { mutableStateOf(setOf<Int>()) }
    var flags by remember { mutableStateOf(setOf<Int>()) }
    var flagMode by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var win by remember { mutableStateOf(false) }
    fun neighbors(i: Int): List<Int> {
        val r = i / n; val c = i % n
        return (-1..1).flatMap { dr -> (-1..1).map { dc -> (r + dr) to (c + dc) } }
            .filter { (r2, c2) -> (r2 != r || c2 != c) && r2 in 0 until n && c2 in 0 until n }
            .map { (r2, c2) -> r2 * n + c2 }
    }
    fun count(i: Int) = neighbors(i).count { it in field }
    fun reveal(start: Int) {
        val stack = ArrayDeque<Int>()
        val res = open.toMutableSet()
        stack.add(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (i in res || i in flags) continue
            res += i
            if (count(i) == 0 && i !in field) neighbors(i).forEach { if (it !in res) stack.add(it) }
        }
        open = res
        if (open.size == n * n - bombs) win = true
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Сапёр 6×6")
        Text(
            when {
                win -> "Победа! 🎉"
                over -> "Бум! 💥"
                else -> "Флаги: ${flags.size}/$bombs"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = { flagMode = !flagMode }) { Text(if (flagMode) "🚩 Режим флажков" else "⬛ Режим открытия") }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until n) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until n) {
                    val i = r * n + c
                    val isOpen = i in open || (over && i in field)
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isOpen && i in field -> Color(0xFFE74C3C)
                                    isOpen -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            )
                            .clickable(enabled = !over && !win && !isOpen) {
                                if (flagMode) {
                                    flags = if (i in flags) flags - i else flags + i
                                } else {
                                    if (i in flags) return@clickable
                                    if (i in field) over = true else reveal(i)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                i in flags && !isOpen -> "🚩"
                                isOpen && i in field -> "💣"
                                isOpen && count(i) > 0 -> "${count(i)}"
                                else -> ""
                            },
                            fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        AgainButton {
            field = sampleMines(n, bombs); open = emptySet(); flags = emptySet(); over = false; win = false
        }
    }
}

private fun sampleMines(n: Int, bombs: Int): Set<Int> = (0 until n * n).shuffled().take(bombs).toSet()

// ---------------- 🧱 Арканоид ----------------
@Composable
fun BreakoutGame() {
    val cols = 8
    val rows = 5
    var paddle by remember { mutableFloatStateOf(0.5f) }
    var ball by remember { mutableStateOf(Offset(0.5f, 0.75f)) }
    var vel by remember { mutableStateOf(Offset(0.32f, -0.55f)) }
    var bricks by remember { mutableStateOf(List(cols * rows) { true }) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val won = bricks.none { it }
    val lost = lives <= 0
    LaunchedEffect(playing, won, lost) {
        while (playing && !won && !lost) {
            delay(16)
            var b = Offset(ball.x + vel.x * 0.022f, ball.y + vel.y * 0.022f)
            var v = vel
            if (b.x < 0.02f || b.x > 0.98f) { v = v.copy(x = -v.x); b = b.copy(x = b.x.coerceIn(0.02f, 0.98f)) }
            if (b.y < 0.02f) { v = v.copy(y = -v.y); b = b.copy(y = 0.02f) }
            // ракетка
            if (v.y > 0 && b.y > 0.93f && b.y < 0.98f && abs(b.x - paddle) < 0.09f) {
                v = Offset(((b.x - paddle) * 4f).coerceIn(-0.8f, 0.8f), -abs(v.y))
            }
            // кирпичи
            if (b.y < 0.42f) {
                val c = ((b.x * cols).toInt()).coerceIn(0, cols - 1)
                val r = ((b.y / 0.42f * rows).toInt()).coerceIn(0, rows - 1)
                val idx = r * cols + c
                if (bricks[idx]) {
                    bricks = bricks.toMutableList().also { it[idx] = false }
                    v = v.copy(y = -v.y)
                    score += 10
                }
            }
            if (b.y > 1f) {
                lives--
                b = Offset(0.5f, 0.75f); v = Offset(0.32f, -0.55f)
            }
            ball = b; vel = v
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Арканоид")
        Text("Счёт: $score · Жизни: $lives" + when {
            won -> " · Победа! 🎉"
            lost -> " · Игра окончена"
            else -> ""
        }, style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(Unit) {
                    detectDragGestures { _, d -> paddle = (paddle + d.x / 900f).coerceIn(0.07f, 0.93f) }
                },
        ) {
            val bw = size.width / cols
            val bh = size.height * 0.42f / rows
            bricks.forEachIndexed { i, alive ->
                if (!alive) return@forEachIndexed
                val c = i % cols; val r = i / cols
                drawRoundRect(
                    Color.hsv((r * 40).toFloat(), 0.7f, 0.9f),
                    Offset(c * bw + 2, r * bh + 2), Size(bw - 4, bh - 4), CornerRadius(8f, 8f),
                )
            }
            drawRoundRect(Color.White, Offset((paddle - 0.08f) * size.width, size.height * 0.94f), Size(size.width * 0.16f, 14f), CornerRadius(7f, 7f))
            drawCircle(Color(0xFFFFD54F), 12f, Offset(ball.x * size.width, ball.y * size.height))
        }
        Text("Тяни по полю, чтобы двигать ракетку", style = MaterialTheme.typography.bodySmall)
        AgainButton {
            bricks = List(cols * rows) { true }; lives = 3; score = 0
            ball = Offset(0.5f, 0.75f); vel = Offset(0.32f, -0.55f); playing = true
        }
    }
}

// ---------------- 🏓 Понг ----------------
@Composable
fun PongGame() {
    var py by remember { mutableFloatStateOf(0.5f) }
    var by by remember { mutableFloatStateOf(0.5f) }
    var ball by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var vel by remember { mutableStateOf(Offset(0.5f, 0.3f)) }
    var me by remember { mutableIntStateOf(0) }
    var bot by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val end = me >= 5 || bot >= 5
    LaunchedEffect(playing, end) {
        while (playing && !end) {
            delay(16)
            var b = Offset(ball.x + vel.x * 0.02f, ball.y + vel.y * 0.02f)
            var v = vel
            if (b.y < 0.03f || b.y > 0.97f) { v = v.copy(y = -v.y); b = b.copy(y = b.y.coerceIn(0.03f, 0.97f)) }
            if (v.x < 0 && b.x < 0.06f && abs(b.y - py) < 0.12f) v = Offset(-v.x, ((b.y - py) * 3f).coerceIn(-0.8f, 0.8f))
            if (v.x > 0 && b.x > 0.94f && abs(b.y - by) < 0.12f) v = Offset(-v.x, ((b.y - by) * 3f).coerceIn(-0.8f, 0.8f))
            by = (by + (b.y - by) * 0.06f).coerceIn(0.1f, 0.9f)
            if (b.x < 0f) { bot++; b = Offset(0.5f, 0.5f); v = Offset(0.5f, 0.3f) }
            if (b.x > 1f) { me++; b = Offset(0.5f, 0.5f); v = Offset(-0.5f, 0.3f) }
            ball = b; vel = v
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Понг с ботом")
        Text("Ты $me : $bot Бот" + if (end) (if (me > bot) " · Победа! 🎉" else " · Проигрыш 🤖") else " (до 5)",
            style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(Unit) {
                    detectDragGestures { _, d -> py = (py + d.y / 700f).coerceIn(0.1f, 0.9f) }
                },
        ) {
            val pw = 14f; val ph = size.height * 0.22f
            drawRoundRect(Color.White, Offset(8f, py * size.height - ph / 2), Size(pw, ph), CornerRadius(7f, 7f))
            drawRoundRect(Color.White, Offset(size.width - 8f - pw, by * size.height - ph / 2), Size(pw, ph), CornerRadius(7f, 7f))
            drawCircle(Color(0xFFFFD54F), 11f, Offset(ball.x * size.width, ball.y * size.height))
            var y = 10f
            while (y < size.height) {
                drawRect(Color.White.copy(alpha = 0.4f), Offset(size.width / 2 - 2, y), Size(4f, 14f))
                y += 30f
            }
        }
        Text("Тяни вверх-вниз, чтобы двигать ракетку", style = MaterialTheme.typography.bodySmall)
        AgainButton { me = 0; bot = 0; ball = Offset(0.5f, 0.5f); vel = Offset(0.5f, 0.3f); playing = true }
    }
}

// ---------------- 🐤 Флэппи ----------------
@Composable
fun FlappyGame() {
    var y by remember { mutableFloatStateOf(0.5f) }
    var v by remember { mutableFloatStateOf(0f) }
    var pipes by remember { mutableStateOf(listOf(0.9f to 0.45f)) }
    var score by remember { mutableIntStateOf(0) }
    var dead by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(started, dead) {
        while (started && !dead) {
            delay(30)
            v += 0.012f
            y += v
            pipes = pipes.map { (x, g) -> (x - 0.012f) to g }
                .filter { (x, _) -> x > -0.1f }
                .let { list ->
                    if (list.isEmpty() || list.last().first < 0.55f) list + ((1.05f) to (0.2f + Math.random() * 0.6).toFloat()) else list
                }
            pipes.forEach { (x, g) ->
                if (abs(x - 0.3f) < 0.045f && (y < g - 0.13f || y > g + 0.13f)) dead = true
            }
            if (pipes.any { (x, _) -> abs(x - 0.3f) < 0.006f }) score++
            if (y < 0f || y > 1f) dead = true
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Флэппи")
        Text("Счёт: $score" + if (dead) " · Столкнулся!" else "", style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF87CEEB))
                .clickable {
                    if (dead) return@clickable
                    started = true
                    v = -0.11f
                },
        ) {
            pipes.forEach { (x, g) ->
                val px = x * size.width
                val pw = size.width * 0.09f
                drawRect(Color(0xFF2ECC71), Offset(px, 0f), Size(pw, (g - 0.13f) * size.height))
                drawRect(Color(0xFF2ECC71), Offset(px, (g + 0.13f) * size.height), Size(pw, size.height))
            }
            drawCircle(Color(0xFFFFD54F), size.width * 0.045f, Offset(size.width * 0.3f, y * size.height))
            drawCircle(Color.Black, 4f, Offset(size.width * 0.3f + 10f, y * size.height - 6f))
        }
        Text("Тапай по полю — птичка машет крыльями", style = MaterialTheme.typography.bodySmall)
        AgainButton { y = 0.5f; v = 0f; pipes = listOf(0.9f to 0.45f); score = 0; dead = false; started = false }
    }
}

// ---------------- 🎯 Ловушка ----------------
@Composable
fun CatchGame() {
    var basket by remember { mutableFloatStateOf(0.5f) }
    var drops by remember { mutableStateOf(listOf<Drop>()) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var playing by remember { mutableStateOf(true) }
    val over = lives <= 0
    LaunchedEffect(playing, over) {
        while (playing && !over) {
            delay(50)
            val fall = 0.02f + score * 0.0004f
            val next = drops.map { it.copy(y = it.y + fall) }.toMutableList()
            val caught = next.filter { it.y >= 0.86f && abs(it.x - basket) < 0.09f }
            caught.forEach { c ->
                if (c.good) score++ else lives--
            }
            drops = next.filter { it.y < 0.95f && it !in caught }.let { list ->
                if ((0..9).random() < 3) list + Drop((0..100).random() / 100f, 0f, (0..9).random() < 8) else list
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Ловушка")
        Text("Счёт: $score · Жизни: ${"❤️".repeat(lives)}" + if (over) " · Игра окончена" else "", style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1B2A4A))
                .pointerInput(Unit) {
                    detectDragGestures { _, d -> basket = (basket + d.x / 900f).coerceIn(0.08f, 0.92f) }
                },
        ) {
            drops.forEach { d ->
                drawCircle(if (d.good) Color(0xFF2ECC71) else Color(0xFFE74C3C), 20f, Offset(d.x * size.width, d.y * size.height))
            }
            drawRoundRect(Color(0xFFC98A3D), Offset((basket - 0.09f) * size.width, size.height * 0.88f), Size(size.width * 0.18f, 26f), CornerRadius(10f, 10f))
        }
        Text("Тяни корзину. Зелёные лови, красных избегай!", style = MaterialTheme.typography.bodySmall)
        AgainButton { drops = emptyList(); score = 0; lives = 3; playing = true }
    }
}

private data class Drop(val x: Float, val y: Float, val good: Boolean)

// ---------------- 🔔 Саймон ----------------
@Composable
fun SimonGame() {
    val colors = listOf(Color(0xFFE74C3C), Color(0xFF2ECC71), Color(0xFF3498DB), Color(0xFFF1C40F))
    var seq by remember { mutableStateOf(listOf((0..3).random())) }
    var showing by remember { mutableStateOf(true) }
    var lit by remember { mutableIntStateOf(-1) }
    var pos by remember { mutableIntStateOf(0) }
    var level = seq.size
    var over by remember { mutableStateOf(false) }
    LaunchedEffect(seq, showing) {
        if (showing && !over) {
            delay(600)
            seq.forEach { c ->
                lit = c; delay(450); lit = -1; delay(200)
            }
            showing = false; pos = 0
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Саймон")
        Text(if (over) "Ошибся! Дошёл до уровня $level" else if (showing) "Смотри…" else "Повтори! Уровень $level",
            style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..1) {
                    val i = r * 2 + c
                    Button(
                        onClick = {
                            if (showing || over) return@Button
                            if (i == seq[pos]) {
                                pos++
                                if (pos == seq.size) {
                                    seq = seq + (0..3).random()
                                    showing = true
                                }
                            } else over = true
                        },
                        modifier = Modifier.size(120.dp, 80.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (lit == i) colors[i] else colors[i].copy(alpha = 0.35f)),
                    ) { Text("${i + 1}", fontSize = 24.sp) }
                }
            }
        }
        AgainButton { seq = listOf((0..3).random()); showing = true; over = false }
    }
}

// ---------------- 🧩 Пятнашки 3×3 ----------------
@Composable
fun SlideGame() {
    fun shuffled(): List<Int> {
        var t = (1..8).toList() + 0
        repeat(120) {
            val e = t.indexOf(0)
            val r = e / 3; val c = e % 3
            val moves = mutableListOf<Int>()
            if (r > 0) moves += e - 3
            if (r < 2) moves += e + 3
            if (c > 0) moves += e - 1
            if (c < 2) moves += e + 1
            val m = moves.random()
            t = t.toMutableList().also { it[e] = it[m]; it[m] = 0 }
        }
        return t
    }
    var tiles by remember { mutableStateOf(shuffled()) }
    var moves by remember { mutableIntStateOf(0) }
    val win = tiles == (1..8).toList() + 0
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Пятнашки 3×3")
        Text(if (win) "Собрал за $moves ходов! 🎉" else "Ходов: $moves", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..2) {
                    val i = r * 3 + c
                    val v = tiles[i]
                    val e = tiles.indexOf(0)
                    val near = (e / 3 == r && abs(e % 3 - c) == 1) || (e % 3 == c && abs(e / 3 - r) == 1)
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (v == 0) Color.Transparent else MaterialTheme.colorScheme.primaryContainer)
                            .clickable(enabled = v != 0 && near && !win) {
                                tiles = tiles.toMutableList().also { it[e] = v; it[i] = 0 }
                                moves++
                            },
                        contentAlignment = Alignment.Center,
                    ) { if (v != 0) Text("$v", fontSize = 40.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        AgainButton { tiles = shuffled(); moves = 0 }
    }
}

// ---------------- 💡 Выключи свет ----------------
@Composable
fun LightsGame() {
    fun fresh(): List<Boolean> {
        var g = List(25) { false }
        repeat(12) {
            val i = (0..24).random()
            val r = i / 5; val c = i % 5
            val t = g.toMutableList()
            t[i] = !t[i]
            if (r > 0) t[i - 5] = !t[i - 5]
            if (r < 4) t[i + 5] = !t[i + 5]
            if (c > 0) t[i - 1] = !t[i - 1]
            if (c < 4) t[i + 1] = !t[i + 1]
            g = t
        }
        return g
    }
    var grid by remember { mutableStateOf(fresh()) }
    var moves by remember { mutableIntStateOf(0) }
    val win = grid.none { it }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Выключи свет")
        Text(if (win) "Всё погасло за $moves ходов! 🎉" else "Ходов: $moves · Погаси все клетки", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..4) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0..4) {
                    val i = r * 5 + c
                    Box(
                        Modifier.size(60.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (grid[i]) Color(0xFFF1C40F) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = !win) {
                                val t = grid.toMutableList()
                                t[i] = !t[i]
                                if (r > 0) t[i - 5] = !t[i - 5]
                                if (r < 4) t[i + 5] = !t[i + 5]
                                if (c > 0) t[i - 1] = !t[i - 1]
                                if (c < 4) t[i + 1] = !t[i + 1]
                                grid = t; moves++
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (grid[i]) "💡" else "", fontSize = 24.sp) }
                }
            }
        }
        AgainButton { grid = fresh(); moves = 0 }
    }
}

// ---------------- 🐂 Быки и коровы ----------------
@Composable
fun BullsGame() {
    var secret by remember { mutableStateOf((0..9).shuffled().take(4).joinToString("")) }
    var text by remember { mutableStateOf("") }
    var hist by remember { mutableStateOf(listOf<String>()) }
    var won by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameTitle("Быки и коровы")
        Text(if (won) "Угадал за ${hist.size} попыток! 🎉" else "Я загадал 4 разные цифры", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            text, { text = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("4 цифры") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !won,
        )
        Button(onClick = {
            if (text.length != 4 || text.toSet().size != 4) return@Button
            val bulls = text.indices.count { text[it] == secret[it] }
            val cows = text.count { it in secret } - bulls
            hist = hist + "$text — 🐂$bulls 🐄$cows"
            if (bulls == 4) won = true
            text = ""
        }, enabled = !won) { Text("Проверить") }
        hist.takeLast(6).reversed().forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (won) AgainButton {
            secret = (0..9).shuffled().take(4).joinToString(""); hist = emptyList(); won = false
        }
    }
}
