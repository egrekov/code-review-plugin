package com.codereview.plugin

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

data class ReviewComment(
    val id: Int,
    val filePath: String,
    val lineStart: Int,
    val lineEnd: Int,
    var selectedText: String,
    var comment: String = "",
    var reference: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class ReviewState {
    var isReviewActive: Boolean = false
    val comments: MutableList<ReviewComment> = mutableListOf()
    private var commentIdCounter = 1

    fun startReview() {
        isReviewActive = true
        comments.clear()
        commentIdCounter = 1
    }

    fun stopReview() {
        isReviewActive = false
    }

    fun addComment(
        filePath: String,
        lineStart: Int,
        lineEnd: Int,
        selectedText: String,
        reference: String = ""
    ): ReviewComment {
        val comment = ReviewComment(
            id = commentIdCounter++,
            filePath = filePath,
            lineStart = lineStart,
            lineEnd = lineEnd,
            selectedText = selectedText,
            reference = reference
        )
        comments.add(comment)
        return comment
    }

    fun removeComment(id: Int) {
        comments.removeIf { it.id == id }
    }

    fun updateComment(id: Int, newComment: String, newReference: String, newSelectedText: String? = null) {
        comments.find { it.id == id }?.let { comment ->
            comment.comment = newComment
            comment.reference = newReference
            newSelectedText?.let { comment.selectedText = it }
        }
    }

    private fun dedent(text: String): String {
        val lines = text.split("\n")
        // Skip the first line when computing min indent —
        // it may start at column 0 even if the rest is indented
        val indentedLines = lines.drop(1).filter { it.isNotBlank() }
        val minIndent = if (indentedLines.isNotEmpty())
            indentedLines.minOf { it.length - it.trimStart().length }
        else 0
        return lines.mapIndexed { i, line ->
            when {
                i == 0 -> line                          // first line: keep as-is
                line.isBlank() -> ""                    // empty lines: clear
                line.length >= minIndent -> line.substring(minIndent)
                else -> line.trimStart()
            }
        }.joinToString("\n").trim()
    }

    fun generateRedmineReport(): String {
        if (comments.isEmpty()) return "No review comments."

        val sb = StringBuilder()
        sb.appendLine("{{Collapse(Ревью)")

        comments.forEachIndexed { index, comment ->
            val num = index + 1
            val isLast = index == comments.size - 1
            sb.append("$num. ")
            if (comment.reference.isNotBlank()) {
                sb.appendLine("@${comment.reference}@")
            }
            sb.appendLine()

            if (comment.selectedText.isNotBlank()) {
                sb.appendLine("<pre><code class=\"php\">")
                sb.appendLine(dedent(comment.selectedText))
                sb.appendLine("</code></pre>")
            }

            if (comment.comment.isNotBlank()) {
                if (comment.selectedText.isNotBlank()) {
                    sb.appendLine()
                }
                sb.appendLine(comment.comment)
            }

            if (!isLast) {
                sb.appendLine()
            }
        }

        sb.append("}}")
        return sb.toString()
    }

    companion object {
        fun getInstance(project: Project): ReviewState = project.service()
    }
}
