package com.codereview.plugin

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
    var comments: MutableList<ReviewComment> = mutableListOf()
)
