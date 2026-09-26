package com.school.hub.feature.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.school.hub.SchoolApp
import kotlin.math.abs

// delay() в этом пакете — игровой (GameSupport.kt), он учитывает паузу.

private enum class Game(val title: String, val emoji: String, val desc: String) {
    SNAKE("Змейка", "🐍", "Классика: собирай яблоки, не врезайся"),
    TTT_BOT("Крестики-нолики с ботом", "🤖", "Бот играет почти идеально — попробуй не проиграть"),
    TTT_DUO("Крестики-нолики вдвоём", "👥", "На одном экране с другом"),
    G2048("2048", "🔢", "Складывай плитки свайпами"),
    DICE("Кости", "🎲", "Кидай кубик"),
    COIN("Орёл и решка", "🪙", "Подбрось монетку"),
    RPS("Камень-ножницы-бумага", "✊", "Против бота"),
    GUESS("Угадай число", "🔢", "От 1 до 100 за меньше попыток"),
    REACTION("Реакция", "⚡", "Жми на зелёный как можно быстрее"),
    PAIRS("Пары", "🃏", "Найди все 8 пар"),
    MINES("Сапёр", "💣", "Поле 6×6, 8 мин"),
    BREAKOUT("Арканоид", "🧱", "Разбей все кирпичи"),
    PONG("Понг", "🏓", "Против бота до 5 очков"),
    FLAPPY("Флэппи", "🐤", "Лети между трубами"),
    CATCH("Ловушка", "🎯", "Лови зелёные, избегай красных"),
    SIMON("Саймон", "🔔", "Повтори последовательность цветов"),
    SLIDE("Пятнашки", "🧩", "Собери 3×3"),
    LIGHTS("Выключи свет", "💡", "Погаси все клетки 5×5"),
    BULLS("Быки и коровы", "🐂", "Угадай 4 цифры"),
    HANGMAN("Виселица", "🎪", "Угадай слово по буквам"),
    MATH("Математика на время", "➗", "Решай 30 секунд"),
    MAZE("Лабиринт", "🌀", "Доведи мышку к флагу"),
    MOLE("Крот", "🔨", "Стукни всех за 30 секунд"),
    STROOP("Цвета-ловушка", "🎨", "Жми цвет, а не слово"),
    HILO("Больше-меньше", "🃏", "Угадай следующую карту"),
    BJ("Двадцать одно", "♠️", "Обыграй дилера"),
    SLOTS("Слоты", "🎰", "Три одинаковых — джекпот"),
    BATTLE("Морской бой", "⚓", "Потопи флот бота 5×5"),
    TAPRACE("Тап-гонка", "🏎", "Кто быстрее тапает 5 сек"),
    DIGITS("Запомни цифры", "🧠", "Память на числа"),
    HOOPS("Баскетбол", "🏀", "Фликни мяч в кольцо"),
    FOOTBALL3D("Футбол 3D", "⚽", "Пенальти в 3D: обыграй вратаря"),
    CUBE3D("Куб 3D", "🧊", "Попади по красной грани"),
    ANAGRAM("Анаграмма", "📝", "Составь слово из букв"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(onBack: () -> Unit) {
    var game by rememberSaveable { mutableStateOf<Game?>(null) }
    val stats = (LocalContext.current.applicationContext as SchoolApp).container.stats
    val currentGame by rememberUpdatedState(game)

    // Пауза, когда приложение уходит в фон
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && currentGame != null) GamePause.paused = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            GamePause.paused = false
        }
    }

    fun closeGame() {
        GamePause.paused = false
        game = null
    }

    BackHandler(enabled = game != null) { closeGame() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(game?.let { "${it.emoji} ${it.title}" } ?: "Мини-игры") },
            navigationIcon = {
                IconButton(onClick = { if (game != null) closeGame() else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
            },
            actions = {
                if (game != null) {
                    IconButton(onClick = { GamePause.paused = !GamePause.paused }) {
                        if (GamePause.paused) Icon(Icons.Filled.PlayArrow, "Продолжить")
                        else Icon(Icons.Filled.Pause, "Пауза")
                    }
                }
            },
        )
    }) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
            val g0 = game
            if (g0 == null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(Game.entries) { g ->
                        Card(onClick = { GamePause.paused = false; game = g; stats.inc("games") }, shape = RoundedCornerShape(22.dp)) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(g.emoji, fontSize = 36.sp)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(g.title, style = MaterialTheme.typography.titleMedium)
                                    Text(g.desc, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else {
                key(g0) { GameContent(g0) }
                if (GamePause.paused) PauseOverlay(onResume = { GamePause.paused = false }, onExit = { closeGame() })
            }
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onExit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Пауза", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Button(onClick = onResume) { Text("Продолжить") }
                OutlinedButton(onClick = onExit) { Text("К списку игр") }
            }
        }
    }
}

@Composable
private fun GameContent(game: Game) {
    when (game) {
        Game.SNAKE -> Snake()
        Game.TTT_BOT -> TicTacToe(bot = true)
        Game.TTT_DUO -> TicTacToe(bot = false)
        Game.G2048 -> Game2048()
        Game.DICE -> DiceGame()
        Game.COIN -> CoinGame()
        Game.RPS -> RpsGame()
        Game.GUESS -> GuessGame()
        Game.REACTION -> ReactionGame()
        Game.PAIRS -> PairsGame()
        Game.MINES -> MinesGame()
        Game.BREAKOUT -> BreakoutGame()
        Game.PONG -> PongGame()
        Game.FLAPPY -> FlappyGame()
        Game.CATCH -> CatchGame()
        Game.SIMON -> SimonGame()
        Game.SLIDE -> SlideGame()
        Game.LIGHTS -> LightsGame()
        Game.BULLS -> BullsGame()
        Game.HANGMAN -> HangmanGame()
        Game.MATH -> MathGame()
        Game.MAZE -> MazeGame()
        Game.MOLE -> MoleGame()
        Game.STROOP -> StroopGame()
        Game.HILO -> HiloGame()
        Game.BJ -> BjGame()
        Game.SLOTS -> SlotsGame()
        Game.BATTLE -> BattleGame()
        Game.TAPRACE -> TapRaceGame()
        Game.DIGITS -> DigitsGame()
        Game.HOOPS -> HoopsGame()
        Game.FOOTBALL3D -> Football3DGame()
        Game.CUBE3D -> Cube3DGame()
        Game.ANAGRAM -> AnagramGame()
    }
}

// ---------------- Змейка ----------------
private const val N = 18

@Composable
private fun Snake() {
    var snake by remember { mutableStateOf(listOf(9 to 9, 8 to 9, 7 to 9)) }
    var dir by remember { mutableStateOf(1 to 0) }
    var nextDir by remember { mutableStateOf(1 to 0) }
    var food by remember { mutableStateOf(4 to 4) }
    var alive by remember { mutableStateOf(true) }
    var score by remember { mutableIntStateOf(0) }
    var best by rememberBest("snake")
    val head = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh

    LaunchedEffect(alive) {
        while (alive) {
            delay((170 - score * 3).coerceAtLeast(70).toLong())
            if (nextDir.first != -dir.first || nextDir.second != -dir.second) dir = nextDir
            val h = snake.first()
            val nh = (h.first + dir.first + N) % N to (h.second + dir.second + N) % N
            if (nh in snake.dropLast(1)) { alive = false; best = maxOf(best, score); break }
            snake = if (nh == food) {
                score++
                if (score > best) best = score
                var f: Pair<Int, Int>
                do { f = (0 until N).random() to (0 until N).random() } while (f in snake || f == nh)
                food = f
                listOf(nh) + snake
            } else listOf(nh) + snake.dropLast(1)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Счёт: $score   Рекорд: $best", style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(bg)
                .pointerInput(Unit) {
                    detectDragGestures { _, d ->
                        if (abs(d.x) > abs(d.y) && abs(d.x) > 6) nextDir = (if (d.x > 0) 1 else -1) to 0
                        else if (abs(d.y) > 6) nextDir = 0 to (if (d.y > 0) 1 else -1)
                    }
                },
        ) {
            val c = size.width / N
            drawCircle(Color(0xFFE74C3C), c * 0.42f, Offset(food.first * c + c / 2, food.second * c + c / 2))
            snake.forEachIndexed { i, p ->
                drawRoundRect(if (i == 0) head else body, Offset(p.first * c + 1, p.second * c + 1), Size(c - 2, c - 2), CornerRadius(c / 3))
            }
        }
        if (!alive) Button(onClick = {
            snake = listOf(9 to 9, 8 to 9, 7 to 9); dir = 1 to 0; nextDir = 1 to 0; score = 0; alive = true
        }) { Text("Ещё раз") } else Text("Свайпай по полю, чтобы поворачивать", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- Крестики-нолики ----------------
private val lines = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))
private fun winner(b: List<Char>): Char? = lines.firstOrNull { (a, c, d) -> b[a] != ' ' && b[a] == b[c] && b[a] == b[d] }?.let { b[it[0]] }
    ?: if (b.none { it == ' ' }) '=' else null

private fun minimax(b: MutableList<Char>, me: Char, turn: Char, depth: Int): Int {
    when (winner(b)) { me -> return 10 - depth; '=' -> return 0; null -> {}; else -> return depth - 10 }
    val scores = b.indices.filter { b[it] == ' ' }.map { i ->
        b[i] = turn; val s = minimax(b, me, if (turn == 'X') 'O' else 'X', depth + 1); b[i] = ' '; s
    }
    return if (turn == me) scores.max() else scores.min()
}

private fun botMove(b: List<Char>): Int {
    val free = b.indices.filter { b[it] == ' ' }
    if ((0..9).random() == 0) return free.random() // иногда ошибается — чтобы можно было выиграть
    return free.maxBy { i -> val m = b.toMutableList(); m[i] = 'O'; minimax(m, 'O', 'X', 0) }
}

@Composable
private fun TicTacToe(bot: Boolean) {
    var board by remember { mutableStateOf(List(9) { ' ' }) }
    var turn by remember { mutableStateOf('X') }
    var score by remember { mutableStateOf(Triple(0, 0, 0)) }
    val w = winner(board)
    LaunchedEffect(board, turn) {
        if (bot && turn == 'O' && winner(board) == null) {
            delay(350)
            board = board.toMutableList().also { it[botMove(board)] = 'O' }
            turn = 'X'
        }
    }
    LaunchedEffect(w) {
        when (w) { 'X' -> score = score.copy(first = score.first + 1); 'O' -> score = score.copy(second = score.second + 1); '=' -> score = score.copy(third = score.third + 1) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("❌ ${score.first}  :  ${score.second} ⭕   (ничьих ${score.third})", style = MaterialTheme.typography.titleMedium)
        Text(
            when (w) { 'X' -> "Победили крестики! 🎉"; 'O' -> if (bot) "Бот победил 🤖" else "Победили нолики! 🎉"; '=' -> "Ничья 🤝"; else -> "Ходят: " + if (turn == 'X') "❌" else "⭕" },
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..2) {
                    val i = r * 3 + c
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = board[i] == ' ' && w == null && !(bot && turn == 'O')) {
                                board = board.toMutableList().also { it[i] = turn }; turn = if (turn == 'X') 'O' else 'X'
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(when (board[i]) { 'X' -> "❌"; 'O' -> "⭕"; else -> "" }, fontSize = 44.sp)
                    }
                }
            }
        }
        Button(onClick = { board = List(9) { ' ' }; turn = 'X' }) { Text("Новая партия") }
    }
}

// ---------------- 2048 ----------------
private fun slide(row: List<Int>): Pair<List<Int>, Int> {
    val xs = row.filter { it != 0 }.toMutableList()
    var gained = 0
    val out = mutableListOf<Int>()
    var i = 0
    while (i < xs.size) {
        if (i + 1 < xs.size && xs[i] == xs[i + 1]) { out += xs[i] * 2; gained += xs[i] * 2; i += 2 } else { out += xs[i]; i++ }
    }
    while (out.size < 4) out += 0
    return out to gained
}

private fun move(b: List<Int>, d: Int): Pair<List<Int>, Int> { // 0←,1→,2↑,3↓
    val res = MutableList(16) { 0 }; var g = 0
    for (k in 0..3) {
        val idx = when (d) { 0 -> (0..3).map { k * 4 + it }; 1 -> (3 downTo 0).map { k * 4 + it }; 2 -> (0..3).map { it * 4 + k }; else -> (3 downTo 0).map { it * 4 + k } }
        val (line, gained) = slide(idx.map { b[it] }); g += gained
        idx.forEachIndexed { j, p -> res[p] = line[j] }
    }
    return res to g
}

private fun spawn(b: List<Int>): List<Int> {
    val free = b.indices.filter { b[it] == 0 }
    if (free.isEmpty()) return b
    return b.toMutableList().also { it[free.random()] = if ((0..9).random() == 0) 4 else 2 }
}

@Composable
private fun Game2048() {
    var board by remember { mutableStateOf(spawn(spawn(List(16) { 0 }))) }
    var score by remember { mutableIntStateOf(0) }
    var best by rememberBest("g2048")
    val over = (0..3).all { move(board, it).first == board }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Счёт: $score · Рекорд: $best" + if (over) " · Игра окончена" else "", style = MaterialTheme.typography.titleMedium)
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFFBBADA0)).padding(8.dp)
                .pointerInput(board) {
                    var acc = Offset.Zero
                    detectDragGestures(onDragStart = { acc = Offset.Zero }, onDragEnd = {
                        val d = if (abs(acc.x) > abs(acc.y)) (if (acc.x < 0) 0 else 1) else (if (acc.y < 0) 2 else 3)
                        if (acc.getDistance() > 24) {
                            val (nb, g) = move(board, d)
                            if (nb != board) {
                                board = spawn(nb)
                                score += g
                                if (score > best) best = score
                            }
                        }
                    }) { _, delta -> acc += delta }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (r in 0..3) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..3) {
                    val v = board[r * 4 + c]
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(tileColor(v)), contentAlignment = Alignment.Center) {
                        if (v > 0) Text("$v", fontWeight = FontWeight.Bold, fontSize = if (v < 100) 30.sp else if (v < 1000) 24.sp else 18.sp,
                            color = if (v <= 4) Color(0xFF776E65) else Color.White)
                    }
                }
            }
        }
        Button(onClick = { board = spawn(spawn(List(16) { 0 })); score = 0 }) { Text("Заново") }
    }
}

private fun tileColor(v: Int) = when (v) {
    0 -> Color(0xFFCDC1B4); 2 -> Color(0xFFEEE4DA); 4 -> Color(0xFFEDE0C8); 8 -> Color(0xFFF2B179); 16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F); 64 -> Color(0xFFF65E3B); 128 -> Color(0xFFEDCF72); 256 -> Color(0xFFEDCC61); 512 -> Color(0xFFEDC850)
    else -> Color(0xFF3C3A32)
}
