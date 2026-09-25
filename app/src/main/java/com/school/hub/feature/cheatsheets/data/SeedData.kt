package com.school.hub.feature.cheatsheets.data

/** Стартовые шпаргалки. У них фиксированные uuid, чтобы при обмене не плодились дубли. */
object SeedData {
    private const val T = 1735689600000L // 01.01.2025

    private fun seed(uuid: String, subject: String, title: String, content: String) = CheatSheetEntity(
        uuid = uuid, title = title, content = content.trimIndent(), subject = subject,
        author = "SchoolHub", createdAt = T, updatedAt = T, dirty = false, originDevice = "seed",
    )

    val items = listOf(
        seed("seed-math-quadratic", "MATH", "Квадратное уравнение", """
            ax² + bx + c = 0

            D = b² − 4ac
            x₁,₂ = (−b ± √D) / 2a

            D > 0 → два корня
            D = 0 → один корень x = −b / 2a
            D < 0 → действительных корней нет

            Теорема Виета: x₁ + x₂ = −b/a, x₁·x₂ = c/a
        """),
        seed("seed-physics-newton", "PHYSICS", "Законы Ньютона", """
            1️⃣ Тело сохраняет покой или равномерное прямолинейное движение, пока на него не действуют силы.
            2️⃣ F = m·a
            3️⃣ Силы взаимодействия равны по модулю и противоположны: F₁₂ = −F₂₁

            Вес: P = m·g, g ≈ 9,8 м/с²
            Сила трения: Fтр = μ·N
        """),
        seed("seed-russian-n-nn", "RUSSIAN", "Н и НН в прилагательных", """
            НН:
            • -онн-, -енн- (станционный, клюквенный)
            • корень на н + суффикс н (длинный, туманный)
            Исключение: ветреный (но безветренный)

            Н:
            • -ан-, -ян-, -ин- (кожаный, серебряный, лебединый)
            Исключения: стеклянный, оловянный, деревянный
        """),
        seed("seed-english-present", "ENGLISH", "Present Simple vs Continuous", """
            Present Simple — регулярно, факты:
            I play football every Sunday.
            Маркеры: always, usually, often, every day

            Present Continuous — прямо сейчас:
            I am playing football now.
            Маркеры: now, at the moment, look!, listen!

            he/she/it в Simple → +s: She plays.
        """),
        seed("seed-chem-valence", "CHEMISTRY", "Постоянная валентность", """
            I:  H, Li, Na, K, F, Ag
            II: O, Be, Mg, Ca, Ba, Zn
            III: B, Al

            Правило: сумма валентностей в формуле уравновешена.
            Al₂O₃ → 2·III = 3·II
        """),
    )
}
