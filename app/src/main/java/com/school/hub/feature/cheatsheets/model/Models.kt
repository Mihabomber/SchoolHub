package com.school.hub.feature.cheatsheets.model

enum class Subject(val title: String, val emoji: String, val color: Long) {
    MATH("Математика", "📐", 0xFF6C5CE7),
    ALGEBRA("Алгебра", "➗", 0xFF5B4CF0),
    GEOMETRY("Геометрия", "📏", 0xFF8E44AD),
    RUSSIAN("Русский", "📝", 0xFFE17055),
    LITERATURE("Литература", "📚", 0xFFD63031),
    ENGLISH("Английский", "🇬🇧", 0xFF0984E3),
    PHYSICS("Физика", "⚛️", 0xFF00A884),
    CHEMISTRY("Химия", "🧪", 0xFF00B5B8),
    BIOLOGY("Биология", "🌿", 0xFF4CAF50),
    HISTORY("История", "🏛️", 0xFFB7791F),
    GEOGRAPHY("География", "🌍", 0xFF1E88E5),
    INFORMATICS("Информатика", "💻", 0xFF7E57C2),
    SOCIAL("Общество", "⚖️", 0xFFE84393),
    PE("Физкультура", "🏃", 0xFFFF7043),
    ART("ИЗО", "🎨", 0xFFEC407A),
    MUSIC("Музыка", "🎵", 0xFFAB47BC),
    TECH("Технология", "🛠️", 0xFF78909C),
    SAFETY("ОБЗР", "🛡️", 0xFF26A69A),
    OTHER("Другое", "✨", 0xFF8E8E93);

    companion object {
        fun from(name: String?): Subject = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

data class CheatSheet(
    val id: Long,
    val uuid: String,
    val title: String,
    val content: String,
    val subject: Subject,
    val imagePath: String?,
    val author: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val isMine: Boolean,
)

data class CheatSheetDraft(
    val id: Long?,
    val title: String,
    val content: String,
    val subject: Subject,
    val imagePath: String?,
    val author: String,
)
