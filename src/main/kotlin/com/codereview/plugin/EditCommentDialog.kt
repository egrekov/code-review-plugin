package com.codereview.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

class EditCommentDialog(
    project: Project,
    private val comment: ReviewComment
) : DialogWrapper(project) {

    private val commentArea = JBTextArea(comment.comment, 5, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font("Arial", Font.PLAIN, 13)
    }

    private fun dedent(text: String): String {
        val lines = text.split("\n")
        val indentedLines = lines.drop(1).filter { it.isNotBlank() }
        val minIndent = if (indentedLines.isNotEmpty())
            indentedLines.minOf { it.length - it.trimStart().length }
        else 0
        return lines.mapIndexed { i, line ->
            when {
                i == 0 -> line
                line.isBlank() -> ""
                line.length >= minIndent -> line.substring(minIndent)
                else -> line.trimStart()
            }
        }.joinToString("\n").trim()
    }

    private val referenceField = JBTextField(comment.reference).apply {
        font = Font("JetBrains Mono", Font.PLAIN, 12)
        toolTipText = "PSI reference: \\Namespace\\Class::method"
    }

    init {
        title = "Edit Review Comment #${comment.id}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        panel.preferredSize = Dimension(640, 420)

        val shortFile = comment.filePath.substringAfterLast("/")
        val locationLabel = JLabel("$shortFile  |  Line ${comment.lineStart}${if (comment.lineEnd != comment.lineStart) "–${comment.lineEnd}" else ""}").apply {
            font = Font("Arial", Font.BOLD, 12)
        }
        panel.add(locationLabel, BorderLayout.NORTH)

        val centerPanel = JPanel(BorderLayout(0, 8))

        val refPanel = JPanel(BorderLayout(0, 4))
        refPanel.add(JLabel("Reference (method / class / file):"), BorderLayout.NORTH)
        refPanel.add(referenceField, BorderLayout.CENTER)
        centerPanel.add(refPanel, BorderLayout.NORTH)

        if (comment.selectedText.isNotBlank()) {
            val codePreview = JBTextArea(dedent(comment.selectedText)).apply {
                isEditable = false
                font = Font("JetBrains Mono", Font.PLAIN, 12)
                rows = 4
                lineWrap = true
            }
            val codePanel = JPanel(BorderLayout(0, 4))
            codePanel.add(JLabel("Selected code:"), BorderLayout.NORTH)
            codePanel.add(JBScrollPane(codePreview), BorderLayout.CENTER)
            centerPanel.add(codePanel, BorderLayout.CENTER)
        }

        val commentPanel = JPanel(BorderLayout(0, 4))
        commentPanel.add(JLabel("Comment:"), BorderLayout.NORTH)
        commentPanel.add(JBScrollPane(commentArea), BorderLayout.CENTER)
        centerPanel.add(commentPanel, BorderLayout.SOUTH)

        panel.add(centerPanel, BorderLayout.CENTER)
        return panel
    }

    override fun getPreferredFocusedComponent() = commentArea

    fun getComment(): String = commentArea.text.trim()
    fun getReference(): String = referenceField.text.trim()
}
