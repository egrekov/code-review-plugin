package com.codereview.plugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*

class ReviewReportDialog(
    project: Project,
    private val initialReport: String
) : DialogWrapper(project) {

    private val reportArea = JBTextArea(initialReport).apply {
        font = Font("JetBrains Mono", Font.PLAIN, 12)
        lineWrap = false
        rows = 25
        columns = 80
    }

    init {
        title = "Code Review Report — Redmine Format"
        setOKButtonText("Copy & Close")
        setCancelButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        panel.preferredSize = Dimension(800, 550)

        // Header with IntelliJ icon
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))

        val iconLabel = JLabel(AllIcons.Actions.Checked)

        val statusLabel = JLabel("Review finished! Edit if needed, then copy.").apply {
            font = Font("Arial", Font.BOLD, 13)
            foreground = Color(0, 130, 70)
        }

        val copyBtn = JButton("Copy to Clipboard", AllIcons.Actions.Copy).apply {
            font = Font("Arial", Font.PLAIN, 12)
            addActionListener { copyToClipboard() }
        }

        headerPanel.add(iconLabel)
        headerPanel.add(statusLabel)
        headerPanel.add(Box.createHorizontalStrut(16))
        headerPanel.add(copyBtn)

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(reportArea), BorderLayout.CENTER)

        val hint = JLabel("Tip: You can edit the text before copying. Ctrl+A to select all, then Ctrl+C.").apply {
            font = Font("Arial", Font.ITALIC, 11)
            foreground = Color.GRAY
        }
        panel.add(hint, BorderLayout.SOUTH)

        return panel
    }

    private fun copyToClipboard() {
        CopyPasteManager.getInstance().setContents(StringSelection(reportArea.text))
        JOptionPane.showMessageDialog(
            contentPane,
            "Report copied to clipboard! Paste it into Redmine.",
            "Copied!",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    override fun doOKAction() {
        copyToClipboard()
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent() = reportArea
}
