package com.codereview.plugin

enum class ReviewFormat(val displayName: String) {
    TEXTILE("Redmine (Textile)"),
    MARKDOWN("Markdown");

    // Человекочитаемое имя в комбобоксе; сериализация Gson идёт по name(), toString() не участвует
    override fun toString(): String = displayName
}

data class ReviewComment(
    var id: Int = 0,
    var filePath: String = "",
    var lineStart: Int = 0,
    var lineEnd: Int = 0,
    var selectedText: String = "",
    var comment: String = "",
    var reference: String = "",
    var timestamp: Long = System.currentTimeMillis()
)

data class ReviewSession(
    var id: String = "",
    var name: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var isActive: Boolean = false,
    var comments: MutableList<ReviewComment> = mutableListOf(),
    // Nullable, а не с дефолтом TEXTILE: у сессий из старых файлов поля нет (Gson оставит null),
    // а при невозможном значении enum Gson тоже записывает null — в обоих случаях работаем как Textile
    var format: ReviewFormat? = null
) {
    val sessionFormat: ReviewFormat
        get() = format ?: ReviewFormat.TEXTILE
}
