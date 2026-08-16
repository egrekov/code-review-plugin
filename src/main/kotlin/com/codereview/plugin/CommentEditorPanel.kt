package com.codereview.plugin

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.text.BadLocationException

class CommentEditorPanel(initialText: String = "") : JPanel(BorderLayout(0, 4)) {

    val commentArea = JBTextArea(initialText, 5, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font("Arial", Font.PLAIN, 13)
    }

    private fun toolbarButton(text: String, fontStyle: Int, tooltip: String, action: () -> Unit): JButton =
        JButton(text).apply {
            font = Font("Arial", fontStyle, 12)
            toolTipText = tooltip
            margin = Insets(0, 1, 0, 1)
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            val fm = getFontMetrics(font)
            val w = (fm.stringWidth(text) + 10).coerceAtLeast(22)
            preferredSize = Dimension(w, 22)
            maximumSize = Dimension(w, 22)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                action()
                commentArea.requestFocusInWindow()
            }
        }

    private fun codeBlockButton(label: String, language: String): JButton =
        toolbarButton(label, Font.PLAIN, "$language block") {
            wrap("<pre><code class=\"$language\">\n", "\n</code></pre>")
        }

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply { isOpaque = false }
        toolbar.add(toolbarButton("B", Font.BOLD, "Bold") { wrap("*", "*") })
        toolbar.add(toolbarButton("I", Font.ITALIC, "Italic") { wrap("_", "_") })
        toolbar.add(toolbarButton("U", Font.PLAIN, "Underline") { wrap("+", "+") })
        toolbar.add(toolbarButton("S", Font.PLAIN, "Strikethrough") { wrap("-", "-") })
        toolbar.add(toolbarButton("</>", Font.PLAIN, "Insert inline code") { wrap("@", "@") })
        toolbar.add(toolbarButton("H1", Font.BOLD, "Heading 1") { applyLinePrefix("h1. ") })
        toolbar.add(toolbarButton("H2", Font.BOLD, "Heading 2") { applyLinePrefix("h2. ") })
        toolbar.add(toolbarButton("H3", Font.BOLD, "Heading 3") { applyLinePrefix("h3. ") })
        toolbar.add(toolbarButton("•", Font.BOLD, "Bulleted list") { applyLinePrefix("* ") })
        toolbar.add(toolbarButton("1.", Font.BOLD, "Numbered list") { applyLinePrefix("# ") })
        toolbar.add(codeBlockButton("{php}", "php"))
        toolbar.add(codeBlockButton("{sql}", "sql"))
        toolbar.add(codeBlockButton("{json}", "json"))

        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(commentArea), BorderLayout.CENTER)
    }

    fun getComment(): String = commentArea.text.trim()

    private fun wrap(prefix: String, suffix: String) {
        val start = commentArea.selectionStart
        val end = commentArea.selectionEnd
        if (end > start) {
            val selected = try {
                commentArea.getText(start, end - start)
            } catch (e: BadLocationException) {
                ""
            }
            commentArea.replaceRange(prefix + selected + suffix, start, end)
            commentArea.select(start + prefix.length, start + prefix.length + selected.length)
        } else {
            commentArea.replaceRange(prefix + suffix, start, start)
            commentArea.setCaretPosition(start + prefix.length)
        }
    }

    private fun applyLinePrefix(prefix: String) {
        val start = commentArea.selectionStart
        val end = commentArea.selectionEnd
        val firstLine = try {
            commentArea.getLineOfOffset(start)
        } catch (e: BadLocationException) {
            0
        }
        val lastLine = if (end > start) {
            try {
                commentArea.getLineOfOffset(end - 1)
            } catch (e: BadLocationException) {
                firstLine
            }
        } else firstLine
        val blockStart = try {
            commentArea.getLineStartOffset(firstLine)
        } catch (e: BadLocationException) {
            0
        }
        val blockEnd = try {
            commentArea.getLineEndOffset(lastLine)
        } catch (e: BadLocationException) {
            commentArea.text.length
        }

        val lines = commentArea.text.substring(blockStart, blockEnd).split("\n")
        val allPrefixed = lines.all { it.startsWith(prefix) }
        val result = lines.joinToString("\n") { line ->
            when {
                allPrefixed && line.startsWith(prefix) -> line.removePrefix(prefix)
                !line.startsWith(prefix) -> prefix + line
                else -> line
            }
        }
        commentArea.replaceRange(result, blockStart, blockEnd)
        commentArea.select(blockStart, blockStart + result.length)
    }
}
