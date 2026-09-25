package com.school.hub.navigation

object Routes {
    const val HOME = "home"
    const val CHEATS = "cheats"
    const val SCHEDULE = "schedule"
    const val HOMEWORK = "homework"
    const val AI = "ai"
    const val AI_CHAT = "ai_chat/{modelId}"
    const val TRANSLATOR = "translator"
    const val CAMERA = "camera"
    const val GRADES = "grades"
    const val FOCUS = "focus"
    const val SETTINGS = "settings"
    const val SYNC = "sync"
    const val CALC = "calc"
    const val GAMES = "games"
    const val BROWSER = "browser"
    const val CHAT = "chat"
    const val ADMIN = "admin"
    const val CHEAT_DETAIL = "cheat/{id}"
    const val CHEAT_EDIT = "cheat_edit?id={id}"

    fun cheatDetail(id: Long) = "cheat/$id"
    fun cheatEdit(id: Long? = null) = if (id == null) "cheat_edit" else "cheat_edit?id=$id"
    fun aiChat(modelId: String) = "ai_chat/$modelId"
}
