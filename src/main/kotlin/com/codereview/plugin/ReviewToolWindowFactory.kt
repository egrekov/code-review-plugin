package com.codereview.plugin

import com.codereview.plugin.actions.AddCommentAction
import com.codereview.plugin.actions.FinishReviewAction
import com.codereview.plugin.actions.StartReviewAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

class ReviewToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ReviewPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        // Add Start, Add Comment, Finish buttons to the tool window title bar
        toolWindow.setTitleActions(listOf(
            StartReviewAction(),
            AddCommentAction(),
            FinishReviewAction()
        ))

        instances[project] = panel
    }

    companion object {
        private val instances = mutableMapOf<Project, ReviewPanel>()

        fun refresh(project: Project) {
            instances[project]?.refresh()
        }
    }
}

class ReviewPanel(private val project: Project) : JPanel(BorderLayout(8, 8)) {

    private val statusLabel = JLabel()
    private val commentsPanel = JPanel()

    init {
        border = EmptyBorder(8, 8, 8, 8)
        commentsPanel.layout = BoxLayout(commentsPanel, BoxLayout.Y_AXIS)

        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        statusLabel.font = Font("Arial", Font.BOLD, 12)
        topBar.add(statusLabel)

        add(topBar, BorderLayout.NORTH)
        add(JBScrollPane(commentsPanel), BorderLayout.CENTER)

        refresh()
    }

    fun refresh() {
        SwingUtilities.invokeLater {
            val state = ReviewState.getInstance(project)
            commentsPanel.removeAll()

            when {
                !state.isReviewActive && state.comments.isEmpty() -> {
                    statusLabel.text = "No active review"
                    val hint = JLabel("<html><center>Press <b>▶</b> to start a new review session</center></html>").apply {
                        horizontalAlignment = SwingConstants.CENTER
                        foreground = Color.GRAY
                        font = Font("Arial", Font.PLAIN, 12)
                        alignmentX = Component.CENTER_ALIGNMENT
                    }
                    commentsPanel.add(Box.createVerticalGlue())
                    commentsPanel.add(hint)
                    commentsPanel.add(Box.createVerticalGlue())
                }
                state.isReviewActive -> {
                    statusLabel.text = "🟢 In progress — ${state.comments.size} comment(s)"
                    if (state.comments.isEmpty()) {
                        val hint = JLabel("<html><center>Select code and press <b>Ctrl+Alt+R</b><br>or click <b>+</b> button above to add a comment</center></html>").apply {
                            horizontalAlignment = SwingConstants.CENTER
                            foreground = Color.GRAY
                            font = Font("Arial", Font.PLAIN, 12)
                            alignmentX = Component.CENTER_ALIGNMENT
                        }
                        commentsPanel.add(Box.createVerticalGlue())
                        commentsPanel.add(hint)
                        commentsPanel.add(Box.createVerticalGlue())
                    } else {
                        state.comments.forEach { comment ->
                            commentsPanel.add(CommentCard(project, comment))
                            commentsPanel.add(Box.createVerticalStrut(6))
                        }
                    }
                }
                else -> {
                    statusLabel.text = "Finished — ${state.comments.size} comment(s)"
                    state.comments.forEach { comment ->
                        commentsPanel.add(CommentCard(project, comment))
                        commentsPanel.add(Box.createVerticalStrut(6))
                    }
                }
            }

            commentsPanel.revalidate()
            commentsPanel.repaint()
        }
    }
}

class CommentCard(private val project: Project, private val comment: ReviewComment) : JPanel(BorderLayout(4, 4)) {

    init {
        val bgColor = UIUtil.getPanelBackground()
        val fgColor = UIUtil.getLabelForeground()
        val borderColor = UIUtil.getBoundsColor()

        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1, true),
            EmptyBorder(6, 8, 6, 8)
        )
        background = bgColor
        maximumSize = Dimension(Int.MAX_VALUE, 130)

        val shortFile = comment.filePath.substringAfterLast("/")
        val lineInfo = "Line ${comment.lineStart}${if (comment.lineEnd != comment.lineStart) "-${comment.lineEnd}" else ""}"

        val header = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        header.add(JLabel("#${comment.id}").apply {
            font = Font("Arial", Font.BOLD, 12)
            foreground = fgColor
        })
        header.add(JLabel("$shortFile  $lineInfo").apply {
            font = Font("Arial", Font.PLAIN, 11)
            foreground = UIUtil.getLabelDisabledForeground()
        })

        val deleteBtn = JLabel("x").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = UIUtil.getLabelDisabledForeground()
            font = Font("Arial", Font.BOLD, 12)
            toolTipText = "Remove comment"
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    ReviewState.getInstance(project).removeComment(comment.id)
                    ReviewToolWindowFactory.refresh(project)
                }
                override fun mouseEntered(e: MouseEvent) { foreground = Color(200, 60, 60) }
                override fun mouseExited(e: MouseEvent) { foreground = UIUtil.getLabelDisabledForeground() }
            })
        }
        val headerRight = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { isOpaque = false }
        headerRight.add(deleteBtn)

        val headerPanel = JPanel(BorderLayout()).apply { isOpaque = false }
        headerPanel.add(header, BorderLayout.WEST)
        headerPanel.add(headerRight, BorderLayout.EAST)
        add(headerPanel, BorderLayout.NORTH)

        val preview = comment.selectedText.take(80).replace("\n", " ").let {
            if (comment.selectedText.length > 80) "$it..." else it
        }
        if (preview.isNotBlank()) {
            add(JLabel("<html><i>$preview</i></html>").apply {
                font = Font("JetBrains Mono", Font.PLAIN, 11)
            }, BorderLayout.CENTER)
        }

        if (comment.comment.isNotBlank()) {
            add(JLabel("<html>${comment.comment.take(120).replace("\n", "<br/>")}</html>").apply {
                font = Font("Arial", Font.PLAIN, 12)
                foreground = fgColor
            }, BorderLayout.SOUTH)
        }
    }
}
