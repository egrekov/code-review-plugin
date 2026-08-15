package com.codereview.plugin

import com.intellij.openapi.project.Project

class ReviewState(private val project: Project) {

    private val manager: ReviewSessionManager
        get() = ReviewSessionManager.getInstance(project)

    val isReviewActive: Boolean
        get() = manager.activeSession?.isActive == true

    val comments: List<ReviewComment>
        get() = manager.activeSession?.comments ?: emptyList()

    fun startReview(name: String) {
        manager.newSession(name)
    }

    fun stopReview() {
        manager.markActiveFinished()
    }

    fun addComment(
        filePath: String,
        lineStart: Int,
        lineEnd: Int,
        selectedText: String,
        reference: String = ""
    ): ReviewComment {
        val session = manager.activeSession ?: throw IllegalStateException("No active session")
        val comment = ReviewComment(
            id = (session.comments.maxOfOrNull { it.id } ?: 0) + 1,
            filePath = filePath,
            lineStart = lineStart,
            lineEnd = lineEnd,
            selectedText = selectedText,
            reference = reference
        )
        session.comments.add(comment)
        session.isActive = true
        manager.saveActiveSession()
        return comment
    }

    fun removeComment(id: Int) {
        val session = manager.activeSession ?: return
        session.comments.removeIf { it.id == id }
        manager.saveActiveSession()
    }

    fun updateComment(id: Int, newComment: String, newReference: String, newSelectedText: String? = null) {
        val session = manager.activeSession ?: return
        session.comments.find { it.id == id }?.let { comment ->
            comment.comment = newComment
            comment.reference = newReference
            newSelectedText?.let { comment.selectedText = it }
        }
        manager.saveActiveSession()
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
        fun getInstance(project: Project): ReviewState = ReviewState(project)
    }
}
