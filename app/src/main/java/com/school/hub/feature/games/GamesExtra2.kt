package com.school.hub.feature.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------- 🎪 Виселица ----------------
@Composable
fun HangmanGame() {
    val words = listOf("ШКОЛА", "УЧЕБНИК", "ТЕТРАДЬ", "КАРАНДАШ", "ПЕРЕМЕНА", "ДНЕВНИК", "РЮКЗАК", "ФИЗИКА", "ГЛОБУС", "ПАРТА")
    var secret by remember { mutableStateOf(words.random()) }
    var guessed by remember { mutableStateOf(setOf<Char>()) }
    var errors by remember { mutableIntStateOf(0) }
    val maxErr = 6
    val win = secret.all { it in guessed }
    val lose = errors >= maxErr
    val over = win || lose
    val stages = listOf("🙂", "😐", "😟", "😨", "😰", "🥵", "💀")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Виселица", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stages[errors.coerceIn(0, 6)], fontSize = 64.sp)
        Text(secret.map { if (it in guessed) "$it " else "_ " }.joinToString(""), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            when {
                win -> "Угадал! 🎉"
                lose -> "Проигрыш. Было: $secret"
                else -> "Ошибок: $errors/$maxErr"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        ("АБВГДЕЖЗ".toList() + "ИЙКЛМНОП".toList() + "РСТУФХЦЧ".toList() + "ШЩЪЫЬЭЮЯЁ".toList()).chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { ch ->
                    val used = ch in guessed
                    Button(
                        onClick = {
                            if (used || over) return@Button
                            guessed = guessed + ch
                            if (ch !in secret) errors++
                        },
                        enabled = !used && !over,
                        modifier = Modifier.size(40.dp, 44.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("$ch", fontSize = 16.sp) }
                }
            }
        }
        Button(onClick = { secret = words.random(); guessed = emptySet(); errors = 0 }) { Text("Новое слово") }
    }
}

// ---------------- ➗ Математика на время ----------------
private data class MathQ(val text: String, val answer: Int, val options: List<Int>)

@Composable
fun MathGame() {
    fun fresh(): MathQ {
        val a = (2..20).random(); val b = (2..20).random()
        val op = listOf("+", "−", "×").random()
        val ans = when (op) { "+" -> a + b; "−" -> a - b; else -> a * b }
        val opts = mutableSetOf(ans)
        while (opts.size < 4) opts += ans + listOf(-10, -5, -2, -1, 1, 2, 3, 5, 10).random()
        return MathQ("$a $op $b = ?", ans, opts.shuffled())
    }
    var q by remember { mutableStateOf(fresh()) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var time by remember { mutableIntStateOf(30) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (playing) {
            while (time > 0) {
                delay(1000); time--
            }
            playing = false
            if (score > best) best = score
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Математика на время", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Счёт: $score · Рекорд: $best · ⏱ $time c", style = MaterialTheme.typography.titleMedium)
        if (!playing && time == 0) Text("Время вышло!", style = MaterialTheme.typography.titleLarge)
        Text(q.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            q.options.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { o ->
                        Button(
                            onClick = {
                                if (!playing) playing = true
                                if (o == q.answer) score++
                                q = fresh()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("$o", fontSize = 22.sp) }
                    }
                }
            }
        }
        Button(onClick = { score = 0; time = 30; playing = true; q = fresh() }) { Text("Заново (30 сек)") }
    }
}

// ---------------- 🌀 Лабиринт ----------------
@Composable
fun MazeGame() {
    val maze = listOf(
        "#########",
        "#S..#...#",
        "##.#.#..#",
        "#..#.#.##",
        "#.##.#..#",
        "#....##.#",
        "##.#...##",
        "#..#.##F#",
        "#########",
    )
    fun start(): Pair<Int, Int> {
        maze.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> if (ch == 'S') return r to c } }
        return 1 to 1
    }
    var pos by remember { mutableStateOf(start()) }
    var steps by remember { mutableIntStateOf(0) }
    var win by remember { mutableStateOf(false) }
    fun wall(r: Int, c: Int) = r !in maze.indices || c !in maze[0].indices || maze[r][c] == '#'
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Лабиринт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (win) "Вышел за $steps шагов! 🎉" else "Шагов: $steps · Веди к 🏁", style = MaterialTheme.typography.titleMedium)
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp)
                .pointerInput(Unit) {
                    var acc = Offset.Zero
                    detectDragGestures(onDragStart = { acc = Offset.Zero }, onDragEnd = {
                        if (win || acc.getDistance() < 24) return@detectDragGestures
                        val (dr, dc) = if (abs(acc.x) > abs(acc.y)) (if (acc.x < 0) 0 to -1 else 0 to 1) else (if (acc.y < 0) -1 to 0 else 1 to 0)
                        val nr = pos.first + dr; val nc = pos.second + dc
                        if (!wall(nr, nc)) {
                            pos = nr to nc; steps++
                            if (maze[nr][nc] == 'F') win = true
                        }
                    }) { _, d -> acc += d }
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            maze.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEachIndexed { c, ch ->
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (ch == '#') MaterialTheme.colorScheme.surfaceContainerHighest else Color(0xFF2ECC71).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when {
                                    pos == r to c -> "🐭"
                                    ch == 'F' -> "🏁"
                                    else -> ""
                                },
                                fontSize = 20.sp,
                            )
                        }
                    }
                }
            }
        }
        Text("Свайпай по лабиринту, чтобы идти", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { pos = start(); steps = 0; win = false }) { Text("Заново") }
    }
}

// ---------------- 🔨 Крот ----------------
@Composable
fun MoleGame() {
    var active by remember { mutableIntStateOf(-1) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var time by remember { mutableIntStateOf(30) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (playing) {
            while (time > 0) {
                active = (0..8).random()
                delay(650)
                time--
            }
            playing = false; active = -1
            if (score > best) best = score
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Поймай крота 🔨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Счёт: $score · Рекорд: $best · ⏱ $time", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..2) {
                    val i = r * 3 + c
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (i == active) Color(0xFF8B5E34) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = playing) {
                                if (i == active) { score++; active = -1 }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (i == active) "🐹" else "🕳️", fontSize = 44.sp) }
                }
            }
        }
        Button(onClick = { score = 0; time = 30; playing = true }) { Text(if (playing) "Идёт игра…" else "Играть 30 сек") }
    }
}

// ---------------- 🎨 Цвета-ловушка ----------------
@Composable
fun StroopGame() {
    val names = listOf("КРАСНЫЙ", "ЗЕЛЁНЫЙ", "СИНИЙ", "ЖЁЛТЫЙ")
    val cols = listOf(Color(0xFFE74C3C), Color(0xFF2ECC71), Color(0xFF3498DB), Color(0xFFF1C40F))
    var wi by remember { mutableIntStateOf((0..3).random()) }
    var ci by remember { mutableIntStateOf((0..3).random()) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var time by remember { mutableIntStateOf(30) }
    var playing by remember { mutableStateOf(false) }
    fun next() {
        wi = (0..3).random()
        var c = (0..3).random()
        while (c == wi) c = (0..3).random()
        ci = c
    }
    LaunchedEffect(playing) {
        if (playing) {
            while (time > 0) {
                delay(1000); time--
            }
            playing = false
            if (score > best) best = score
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Цвета-ловушка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Счёт: $score · Рекорд: $best · ⏱ $time", style = MaterialTheme.typography.titleMedium)
        Text("Нажимай ЦВЕТ букв, а не слово!", style = MaterialTheme.typography.bodyMedium)
        Text(names[wi], fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = cols[ci])
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i ->
                        Button(
                            onClick = {
                                if (!playing) playing = true
                                if (i == ci) score++ else score--
                                next()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(names[i], maxLines = 1) }
                    }
                }
            }
        }
        Button(onClick = { score = 0; time = 30; playing = true; next() }) { Text("Заново") }
    }
}

// ---------------- 🃏 Больше-меньше ----------------
@Composable
fun HiloGame() {
    fun card() = (6..14).random()
    fun name(v: Int) = when (v) { 11 -> "В"; 12 -> "Д"; 13 -> "К"; 14 -> "Т"; else -> "$v" }
    var cur by remember { mutableIntStateOf(card()) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Будет больше или меньше?") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Больше-меньше", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Счёт: $score · Рекорд: $best", style = MaterialTheme.typography.titleMedium)
        Text("🂠 ${name(cur)}", fontSize = 72.sp)
        Text(msg, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val n = card()
                if (n >= cur) { score++; msg = "Верно! Было ${name(n)}" } else {
                    msg = "Мимо! Было ${name(n)}"
                    if (score > best) best = score
                    score = 0
                }
                cur = n
            }) { Text("⬆️ Больше") }
            Button(onClick = {
                val n = card()
                if (n <= cur) { score++; msg = "Верно! Было ${name(n)}" } else {
                    msg = "Мимо! Было ${name(n)}"
                    if (score > best) best = score
                    score = 0
                }
                cur = n
            }) { Text("⬇️ Меньше") }
        }
    }
}

// ---------------- ♠️ Двадцать одно ----------------
@Composable
fun BjGame() {
    fun draw() = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 10, 10, 11).random()
    fun total(h: List<Int>): Int {
        var t = h.sum(); var aces = h.count { it == 11 }
        while (t > 21 && aces > 0) { t -= 10; aces-- }
        return t
    }
    var player by remember { mutableStateOf(listOf(draw(), draw())) }
    var dealer by remember { mutableStateOf(listOf(draw(), draw())) }
    var balance by remember { mutableIntStateOf(100) }
    var phase by remember { mutableIntStateOf(0) } // 0 игра, 1 конец
    var msg by remember { mutableStateOf("Твой ход: ещё или хватит?") }
    fun finish(stand: Boolean) {
        var d = dealer
        if (stand) while (total(d) < 17) d = d + draw()
        dealer = d
        val p = total(player); val t = total(d)
        val res = when {
            p > 21 -> -1
            t > 21 -> 1
            p > t -> 1
            p < t -> -1
            else -> 0
        }
        balance += res * 10
        msg = when (res) {
            1 -> "Победа! +10 🎉"
            -1 -> "Проигрыш −10"
            else -> "Ничья"
        }
        phase = 1
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Двадцать одно", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Баланс: $balance", style = MaterialTheme.typography.titleMedium)
        Text("Дилер: ${if (phase == 0) "${dealer[0]} ?" else dealer.joinToString(" + ") + " = ${total(dealer)}"}", fontSize = 20.sp)
        Text("Ты: ${player.joinToString(" + ")} = ${total(player)}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(msg, style = MaterialTheme.typography.titleMedium)
        if (phase == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    player = player + draw()
                    if (total(player) >= 21) finish(false)
                }) { Text("Ещё 🃏") }
                Button(onClick = { finish(true) }) { Text("Хватит ✋") }
            }
        } else {
            Button(onClick = {
                if (balance < 10) balance = 100
                player = listOf(draw(), draw()); dealer = listOf(draw(), draw())
                phase = 0; msg = "Твой ход: ещё или хватит?"
            }) { Text("Новая раздача") }
        }
    }
}

// ---------------- 🎰 Слоты ----------------
@Composable
fun SlotsGame() {
    val syms = listOf("🍒", "🍋", "🔔", "⭐", "7️⃣")
    var reels by remember { mutableStateOf(listOf(0, 0, 0)) }
    var coins by remember { mutableIntStateOf(50) }
    var spinning by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("Крути! 3 одинаковых — джекпот") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Слоты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Монет: $coins 🪙", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            reels.forEach { r ->
                Box(
                    Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Text(syms[r], fontSize = 48.sp) }
            }
        }
        Text(msg, style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            if (spinning || coins < 5) return@Button
            spinning = true
            coins -= 5
            val r = List(3) { syms.indices.random() }
            reels = r
            var win = 0
            if (r[0] == r[1] && r[1] == r[2]) win = if (r[0] == 4) 100 else 50
            else if (r[0] == r[1] || r[1] == r[2] || r[0] == r[2]) win = 10
            coins += win
            msg = if (win > 0) "Выигрыш +$win! 🎉" else "Не повезло, крути ещё"
            spinning = false
        }) { Text("Крутить (−5)") }
        if (coins < 5) Button(onClick = { coins = 50 }) { Text("Взять 50 монет") }
    }
}

// ---------------- ⚓ Морской бой ----------------
@Composable
fun BattleGame() {
    val n = 5
    fun ships(): Set<Int> = (0 until n * n).shuffled().take(4).toSet()
    var mine by remember { mutableStateOf(ships()) }
    var enemy by remember { mutableStateOf(ships()) }
    var myShots by remember { mutableStateOf(setOf<Int>()) }
    var foeShots by remember { mutableStateOf(setOf<Int>()) }
    var turn by remember { mutableStateOf(true) } // true — мой ход
    val myHits = myShots.intersect(enemy).size
    val foeHits = foeShots.intersect(mine).size
    val win = myHits == 4
    val lose = foeHits == 4
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Морской бой 5×5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            when {
                win -> "Победа! Все корабли потоплены 🎉"
                lose -> "Проигрыш — твой флот потоплен"
                else -> if (turn) "Твой ход — стреляй по правому полю" else "Ход бота…"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text("Твои попадания: $myHits/4 · Бот: $foeHits/4", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Твой флот", style = MaterialTheme.typography.bodySmall)
                for (r in 0 until n) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (c in 0 until n) {
                        val i = r * n + c
                        Box(
                            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        i in foeShots && i in mine -> Color(0xFFE74C3C)
                                        i in foeShots -> Color(0xFF3498DB)
                                        i in mine -> Color(0xFF2ECC71).copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (i in foeShots && i in mine) "🔥" else "", fontSize = 14.sp) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Флот бота", style = MaterialTheme.typography.bodySmall)
                for (r in 0 until n) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (c in 0 until n) {
                        val i = r * n + c
                        Box(
                            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        i in myShots && i in enemy -> Color(0xFFE74C3C)
                                        i in myShots -> Color(0xFF3498DB)
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                )
                                .clickable(enabled = turn && !win && !lose && i !in myShots) {
                                    myShots = myShots + i
                                    if (myShots.intersect(enemy).size < 4) {
                                        val free = (0 until n * n).filter { it !in foeShots }
                                        if (free.isNotEmpty()) foeShots = foeShots + free.random()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (i in myShots && i in enemy) "🔥" else if (i in myShots) "•" else "", fontSize = 14.sp) }
                    }
                }
            }
        }
        Button(onClick = {
            mine = ships(); enemy = ships(); myShots = emptySet(); foeShots = emptySet(); turn = true
        }) { Text("Новая битва") }
    }
}

// ---------------- 🏎 Тап-гонка ----------------
@Composable
fun TapRaceGame() {
    var taps by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var time by remember { mutableIntStateOf(5) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (playing) {
            while (time > 0) {
                delay(1000); time--
            }
            playing = false
            if (taps > best) best = taps
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Тап-гонка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Тапов: $taps · Рекорд: $best · ⏱ $time", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = {
                if (!playing && time == 0) return@Button
                if (!playing) { taps = 0; time = 5; playing = true }
                else taps++
            },
            modifier = Modifier.size(220.dp, 120.dp),
        ) { Text(if (playing) "ЖМИ! 👆" else "Старт", fontSize = 28.sp) }
        if (!playing && time == 0) Text("Финиш: $taps тапов!", style = MaterialTheme.typography.titleLarge)
    }
}

// ---------------- 🧠 Запомни цифры ----------------
@Composable
fun DigitsGame() {
    var len by remember { mutableIntStateOf(3) }
    var digits by remember { mutableStateOf(digitsFor(3)) }
    var phase by remember { mutableIntStateOf(0) } // 0 показ, 1 ввод, 2 конец
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    fun newRound(l: Int) {
        digits = digitsFor(l)
        phase = 0
        text = ""
    }
    LaunchedEffect(digits, phase) {
        if (phase == 0 && digits.isNotEmpty()) {
            delay(2500)
            if (phase == 0) phase = 1
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Запомни цифры", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Длина: $len", style = MaterialTheme.typography.titleMedium)
        when (phase) {
            0 -> Text(digits, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
            1 -> {
                Text("Что было?", fontSize = 24.sp)
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Цифры по памяти") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                Button(onClick = {
                    if (text == digits) {
                        len++; msg = "Верно! Удлиняю 🎉"; newRound(len)
                    } else {
                        msg = "Мимо! Было $digits"; phase = 2
                    }
                }) { Text("Проверить") }
            }
            else -> {
                Text(msg, style = MaterialTheme.typography.titleMedium)
                Button(onClick = { len = 3; msg = ""; newRound(3) }) { Text("Заново") }
            }
        }
    }
}

private fun digitsFor(l: Int): String = (1..l).map { (0..9).random().toString() }.joinToString("")

// ---------------- 🏀 Баскетбол ----------------
@Composable
fun HoopsGame() {
    var ball by remember { mutableStateOf(Offset(0.5f, 0.9f)) }
    var vel by remember { mutableStateOf(Offset.Zero) }
    var flying by remember { mutableStateOf(false) }
    var dragFrom by remember { mutableStateOf<Offset?>(null) }
    var dragCur by remember { mutableStateOf<Offset?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var shots by remember { mutableIntStateOf(0) }
    val hoop = Offset(0.72f, 0.3f)
    LaunchedEffect(flying) {
        while (flying) {
            delay(16)
            var v = Offset(vel.x, vel.y + 0.0016f)
            var b = Offset(ball.x + v.x, ball.y + v.y)
            if (b.x < 0.03f || b.x > 0.97f) { v = v.copy(x = -v.x * 0.7f); b = b.copy(x = b.x.coerceIn(0.03f, 0.97f)) }
            // кольцо
            if (v.y > 0 && abs(b.x - hoop.x) < 0.055f && abs(b.y - hoop.y) < 0.03f) {
                score++; shots++
                ball = Offset(0.5f, 0.9f); vel = Offset.Zero; flying = false
                break
            }
            if (b.y > 1f) {
                shots++
                ball = Offset(0.5f, 0.9f); vel = Offset.Zero; flying = false
                break
            }
            ball = b; vel = v
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Баскетбол", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Попаданий: $score из $shots", style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1B2A4A))
                .pointerInput(flying) {
                    if (flying) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragFrom = Offset(it.x / size.width, it.y / size.height) },
                        onDragEnd = {
                            val f = dragFrom
                            val c = dragCur
                            if (f != null && c != null) {
                                vel = Offset((f.x - c.x) * 0.09f, (f.y - c.y) * 0.09f)
                                flying = true
                            }
                            dragFrom = null; dragCur = null
                        },
                    ) { change, _ ->
                        dragCur = Offset(change.position.x / size.width, change.position.y / size.height)
                    }
                },
        ) {
            // щит и кольцо
            drawRect(Color.White, Offset(hoop.x * size.width + 40f, hoop.y * size.height - 70f), Size(10f, 90f))
            drawRoundRect(Color(0xFFE74C3C), Offset((hoop.x - 0.055f) * size.width, hoop.y * size.height), Size(size.width * 0.11f, 10f), CornerRadius(5f, 5f))
            val df = dragFrom
            val dc = dragCur
            if (df != null && dc != null) {
                drawLine(Color.Yellow, Offset(df.x * size.width, df.y * size.height), Offset(dc.x * size.width, dc.y * size.height), 6f)
            }
            drawCircle(Color(0xFFE67E22), 22f, Offset(ball.x * size.width, ball.y * size.height))
            drawCircle(Color.Black, 22f, Offset(ball.x * size.width, ball.y * size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        }
        Text("Тяни от мяча и отпускай — бросок в кольцо", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- 📝 Анаграмма ----------------
@Composable
fun AnagramGame() {
    val words = listOf("ШКОЛА", "УРОК", "КНИГА", "РУЧКА", "ДОСКА", "МЕЛ", "ЗВОНОК", "ОЦЕНКА", "ЗАДАЧА", "ОТВЕТ")
    fun scramble(w: String): String {
        var s = w.toList().shuffled().joinToString("")
        var guard = 0
        while (s == w && guard++ < 20) s = w.toList().shuffled().joinToString("")
        return s
    }
    var word by remember { mutableStateOf(words.random()) }
    var mixed by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var score by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Составь слово из букв") }
    if (mixed.isEmpty()) mixed = scramble(word)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Анаграмма", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Счёт: $score", style = MaterialTheme.typography.titleMedium)
        Text(mixed.toList().joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Text(msg, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(text, { text = it.uppercase().filter { c -> c.isLetter() }.take(10) }, label = { Text("Твоё слово") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (text == word) {
                    score++; msg = "Верно! 🎉"
                    word = words.random(); mixed = ""; text = ""
                } else msg = "Мимо, попробуй ещё"
            }) { Text("Проверить") }
            OutlinedButton(onClick = {
                word = words.random(); mixed = ""; text = ""; msg = "Пропустил, вот новое"
            }) { Text("Пропустить") }
        }
    }
}

// ==================== мини-3D ====================

private data class V3(val x: Float, val y: Float, val z: Float)

private fun rotX(p: V3, a: Float): V3 {
    val c = cos(a); val s = sin(a)
    return V3(p.x, p.y * c - p.z * s, p.y * s + p.z * c)
}

private fun rotY(p: V3, a: Float): V3 {
    val c = cos(a); val s = sin(a)
    return V3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c)
}

private fun projPt(p: V3, dist: Float, scale: Float, cx: Float, cy: Float): Offset {
    val k = scale * dist / (dist + p.z)
    return Offset(cx + p.x * k, cy - p.y * k)
}

private fun faceNormal(a: V3, b: V3, c: V3): V3 {
    val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
    val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
    return V3(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx)
}

private fun pointInPoly(pt: Offset, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val xi = poly[i].x; val yi = poly[i].y
        val xj = poly[j].x; val yj = poly[j].y
        if ((yi > pt.y) != (yj > pt.y) && pt.x < (xj - xi) * (pt.y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

private val cubeVerts = listOf(
    V3(-1f, -1f, -1f), V3(1f, -1f, -1f), V3(1f, 1f, -1f), V3(-1f, 1f, -1f),
    V3(-1f, -1f, 1f), V3(1f, -1f, 1f), V3(1f, 1f, 1f), V3(-1f, 1f, 1f),
)
// грани: 0 зад, 1 перед(красная), 2 низ, 3 верх, 4 лево, 5 право
private val cubeFaces = listOf(
    intArrayOf(0, 1, 2, 3), intArrayOf(4, 5, 6, 7), intArrayOf(0, 1, 5, 4),
    intArrayOf(2, 3, 7, 6), intArrayOf(0, 3, 7, 4), intArrayOf(1, 2, 6, 5),
)

private data class FaceDraw(val idx: Int, val pts: List<Offset>, val facing: Float, val depth: Float)

/** Кадр куба в виртуальных координатах 1000×1000, дальние грани первыми. */
private fun cubeFrame(ax: Float, ay: Float): List<FaceDraw> {
    val rot = cubeVerts.map { rotY(rotX(it, ax), ay) }
    val proj = rot.map { projPt(it, 5f, 220f, 500f, 500f) }
    return cubeFaces.indices.map { fi ->
        val vs = cubeFaces[fi].map { rot[it] }
        val n = faceNormal(vs[0], vs[1], vs[2])
        val len = sqrt(n.x * n.x + n.y * n.y + n.z * n.z) + 0.0001f
        FaceDraw(fi, cubeFaces[fi].map { vi -> proj[vi] }, n.z / len, vs.sumOf { it.z } / 4f)
    }.sortedBy { it.depth }
}

// ---------------- 🧊 Куб 3D ----------------
@Composable
fun Cube3DGame() {
    var ax by remember { mutableFloatStateOf(0.5f) }
    var ay by remember { mutableFloatStateOf(0.7f) }
    var score by remember { mutableIntStateOf(0) }
    var shots by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var time by remember { mutableIntStateOf(30) }
    var playing by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("Попади по красной грани!") }
    val frame = remember(ax, ay) { cubeFrame(ax, ay) }
    val currentFrame by rememberUpdatedState(frame)
    LaunchedEffect(playing) {
        if (playing) {
            while (time > 0) {
                delay(1000); time--
                ay += 0.15f
            }
            playing = false
            if (score > best) best = score
            msg = "Время! Попал $score из $shots"
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Куб 3D", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Попал: $score из $shots · Рекорд: $best · ⏱ $time", style = MaterialTheme.typography.titleMedium)
        Text(msg, style = MaterialTheme.typography.bodyMedium)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101020))
                .pointerInput(playing) {
                    detectTapGestures { tap ->
                        if (!playing) return@detectTapGestures
                        shots++
                        val v = Offset(tap.x / size.width * 1000f, tap.y / size.height * 1000f)
                        val hit = currentFrame.asReversed().firstOrNull { f -> pointInPoly(v, f.pts) }
                        if (hit != null && hit.idx == 1) {
                            score++
                            msg = "Точно! 🎯"
                            ax = (0..100).random() / 100f * 3f
                            ay = (0..100).random() / 100f * 3f
                        } else msg = "Мимо — целься в красную грань"
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { _, d -> ax += d.y * 0.01f; ay += d.x * 0.01f }
                },
        ) {
            val s = size.width / 1000f
            scale(s, s) {
                frame.forEach { f ->
                    if (f.pts.size < 3) return@forEach
                    val path = Path().apply {
                        moveTo(f.pts[0].x, f.pts[0].y)
                        for (k in 1 until f.pts.size) lineTo(f.pts[k].x, f.pts[k].y)
                        close()
                    }
                    val base = if (f.idx == 1) Color(0xFFE74C3C) else Color(0xFF3498DB)
                    val shade = 0.35f + 0.65f * f.facing.coerceIn(0f, 1f)
                    drawPath(path, base.copy(alpha = 0.55f + 0.45f * shade))
                    for (k in f.pts.indices) {
                        drawLine(Color.White, f.pts[k], f.pts[(k + 1) % f.pts.size], 10f)
                    }
                }
            }
        }
        Text("Тапай по красной грани · тяни, чтобы крутить", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { score = 0; shots = 0; time = 30; playing = true; msg = "Попади по красной грани!" }) {
            Text(if (playing) "Идёт игра…" else "Играть 30 сек")
        }
    }
}

// ---------------- ⚽ Футбол 3D: пенальти ----------------
@Composable
fun Football3DGame() {
    // цель в координатах ворот 0..1 (x) и 0..0.6 (y)
    var target by remember { mutableStateOf(Offset(0.5f, 0.3f)) }
    var t by remember { mutableFloatStateOf(0f) }
    var keeper by remember { mutableFloatStateOf(0.5f) }
    var keeperTo by remember { mutableFloatStateOf(0.5f) }
    var phase by remember { mutableIntStateOf(0) } // 0 наводка, 1 полёт, 2 результат
    var round by remember { mutableIntStateOf(1) }
    var goals by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Тяни по воротам, чтобы прицелиться, и жми «Бить!»") }
    val total = 5
    val over = round > total
    LaunchedEffect(phase) {
        if (phase == 1) {
            keeperTo = (target.x + (-0.25f..0.25f).random()).let { (it as Float).coerceIn(0.05f, 0.95f) }
            val k0 = keeper
            var i = 0
            while (i < 20) {
                delay(40)
                i++
                t = i / 20f
                keeper = k0 + (keeperTo - k0) * t
            }
            val inGoal = target.x in 0.05f..0.95f && target.y in 0.05f..0.6f
            val saved = abs(target.x - keeper) < 0.13f
            if (inGoal && !saved) {
                goals++
                msg = "ГОООЛ! ⚽🎉"
            } else if (!inGoal) msg = "Мимо ворот!"
            else msg = "Вратарь поймал! 🧤"
            phase = 2
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Футбол 3D: пенальти", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (over) "Итог: $goals из $total" else "Раунд $round/$total · Голов: $goals", style = MaterialTheme.typography.titleMedium)
        Text(msg, style = MaterialTheme.typography.bodyMedium)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2E7D32))
                .pointerInput(phase) {
                    if (phase != 0) return@pointerInput
                    detectDragGestures(
                        onDragStart = { target = convToGoal(Offset(it.x / size.width, it.y / size.height)) },
                        onDragEnd = {},
                    ) { change, _ -> target = convToGoal(Offset(change.position.x / size.width, change.position.y / size.height)) }
                },
        ) {
            val w = size.width; val h = size.height
            // перспектива поля
            val field = Path().apply {
                moveTo(0f, h); lineTo(w, h); lineTo(w * 0.78f, h * 0.3f); lineTo(w * 0.22f, h * 0.3f); close()
            }
            drawPath(field, Color(0xFF388E3C))
            // ворота
            val gx = w * 0.15f; val gw = w * 0.7f; val gy = h * 0.08f; val gh = h * 0.3f
            drawRect(Color.White.copy(alpha = 0.25f), Offset(gx, gy), Size(gw, gh))
            drawLine(Color.White, Offset(gx, gy), Offset(gx, gy + gh), 8f)
            drawLine(Color.White, Offset(gx + gw, gy), Offset(gx + gw, gy + gh), 8f)
            drawLine(Color.White, Offset(gx, gy), Offset(gx + gw, gy), 8f)
            // сетка
            for (k in 1..5) {
                val x = gx + gw * k / 6f
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, gy), Offset(x, gy + gh), 2f)
            }
            // вратарь
            val kx = (gx + keeper * gw)
            drawCircle(Color(0xFFF1C40F), 20f, Offset(kx, gy + gh * 0.55f))
            drawCircle(Color.Black, 20f, Offset(kx, gy + gh * 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            // прицел
            if (phase == 0) {
                val px = gx + target.x * gw
                val py2 = gy + (target.y / 0.6f).coerceIn(0f, 1f) * gh
                drawCircle(Color.Red, 12f, Offset(px, py2))
                drawCircle(Color.White, 12f, Offset(px, py2), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
            // мяч
            val start = Offset(w / 2f, h * 0.92f)
            val end = Offset(gx + target.x * gw, gy + (target.y / 0.6f).coerceIn(0f, 1f) * gh)
            val pos = if (phase == 0) start else Offset(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t - kotlin.math.sin(t * 3.14f) * h * 0.15f,
            )
            val r = 30f * (1 - t) + 11f * t
            drawOval(Color.Black.copy(alpha = 0.3f), Offset(pos.x - r, pos.y + r * 0.7f), Size(r * 2, r * 0.5f))
            drawCircle(Color.White, r, pos)
            drawCircle(Color.Black, r * 0.4f, pos)
        }
        if (!over) {
            Button(onClick = { if (phase == 0) phase = 1 }, enabled = phase == 0, modifier = Modifier.fillMaxWidth()) {
                Text("Бить! 🥅")
            }
            if (phase == 2) Button(onClick = {
                round++
                phase = 0; t = 0f
                keeper = 0.5f
                target = Offset(0.5f, 0.3f)
                msg = "Раунд $round: целься и бей!"
            }) { Text("Следующий удар") }
        } else {
            Button(onClick = {
                round = 1; goals = 0; phase = 0; t = 0f; keeper = 0.5f
                target = Offset(0.5f, 0.3f); msg = "Снова: 5 ударов!"
            }) { Text("Ещё серию") }
        }
    }
}

private fun convToGoal(p: Offset): Offset {
    // p — 0..1 по канвасу; ворота занимают x 0.15..0.85, верхнюю треть
    val x = ((p.x - 0.15f) / 0.7f).coerceIn(0f, 1f)
    val y = (p.y / 0.45f).coerceIn(0f, 0.6f)
    return Offset(x, y)
}
