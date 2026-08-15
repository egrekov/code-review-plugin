package com.codereview.plugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import javax.swing.*
import javax.swing.table.AbstractTableModel

class SessionManagerDialog(private val project: Project) : DialogWrapper(project) {

    private val manager = ReviewSessionManager.getInstance(project)
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm")

    private val tableModel = object : AbstractTableModel() {
        private val columns = arrayOf("Name", "Status", "Comments", "Created")

        override fun getRowCount(): Int = manager.allSessions.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]
        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

        override fun getValueAt(row: Int, column: Int): Any {
            val session = manager.allSessions[row]
            return when (column) {
                0 -> session.name
                1 -> if (session.isActive) "In progress" else "Finished"
                2 -> session.comments.size.toString()
                else -> dateFormat.format(java.util.Date(session.createdAt))
            }
        }
    }

    private fun toolbarButton(icon: Icon, tooltip: String, action: () -> Unit): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            margin = Insets(0, 0, 0, 0)
            preferredSize = Dimension(24, 24)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
        }

    private val table = JBTable(tableModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        rowSelectionAllowed = true
        showVerticalLines = false
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && selectedRow >= 0) {
                    doOpenAction()
                }
            }
        })
    }

    private val renameButton = toolbarButton(AllIcons.Actions.Edit, "Rename session") {
        renameSessionAt(table.selectedRow)
    }

    private val deleteButton = toolbarButton(AllIcons.Actions.GC, "Delete session") {
        deleteSessionAt(table.selectedRow)
    }

    private val clearAllButton = toolbarButton(
        IconLoader.getIcon("/icons/broom.svg", SessionManagerDialog::class.java),
        "Clear all sessions"
    ) {
        clearAllSessions()
    }

    init {
        title = "Review Sessions"
        setCancelButtonText("Close")
        init()

        table.removeColumn(table.columnModel.getColumn(1))

        table.selectionModel.addListSelectionListener { updateButtons() }

        if (tableModel.rowCount > 0) {
            table.setRowSelectionInterval(0, 0)
        }
        updateButtons()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        panel.preferredSize = Dimension(560, 320)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        toolbar.add(renameButton)
        toolbar.add(deleteButton)
        toolbar.add(clearAllButton)

        val hint = JLabel("Double-click a session to open it. Sessions are stored in .idea/").apply {
            font = Font("Arial", Font.ITALIC, 11)
            foreground = com.intellij.util.ui.UIUtil.getLabelDisabledForeground()
        }

        val northPanel = JPanel(BorderLayout(0, 4)).apply { isOpaque = false }
        northPanel.add(toolbar, BorderLayout.NORTH)
        northPanel.add(hint, BorderLayout.CENTER)

        panel.add(northPanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }

    private fun updateButtons() {
        val hasSelection = table.selectedRow >= 0
        val hasSessions = tableModel.rowCount > 0
        renameButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection
        clearAllButton.isEnabled = hasSessions
    }

    private fun renameSessionAt(row: Int) {
        val session = manager.allSessions.getOrNull(row) ?: return
        val newName = Messages.showInputDialog(
            project,
            "Enter a new name for the session:",
            "Rename Session",
            Messages.getQuestionIcon(),
            session.name,
            null
        )
        if (newName != null) {
            manager.renameSession(session.id, newName)
            tableModel.fireTableDataChanged()
            ReviewToolWindowFactory.refresh(project)
            updateButtons()
        }
    }

    private fun deleteSessionAt(row: Int) {
        val session = manager.allSessions.getOrNull(row) ?: return
        val confirm = Messages.showYesNoDialog(
            project,
            "Delete session \"${session.name}\" (${session.comments.size} comments)?",
            "Delete Session?",
            Messages.getWarningIcon()
        )
        if (confirm == Messages.YES) {
            manager.deleteSession(session.id)
            tableModel.fireTableDataChanged()
            if (tableModel.rowCount > 0) {
                table.setRowSelectionInterval(0, 0)
            }
            ReviewToolWindowFactory.refresh(project)
            updateButtons()
        }
    }

    private fun clearAllSessions() {
        if (tableModel.rowCount == 0) return
        val confirm = Messages.showYesNoDialog(
            project,
            "Delete all review sessions (${tableModel.rowCount})?",
            "Clear All Sessions?",
            Messages.getWarningIcon()
        )
        if (confirm == Messages.YES) {
            manager.clearAllSessions()
            tableModel.fireTableDataChanged()
            ReviewToolWindowFactory.refresh(project)
            updateButtons()
        }
    }

    private fun doOpenAction() {
        val row = table.selectedRow
        if (row < 0) return
        manager.switchSession(manager.allSessions[row].id)
        ReviewToolWindowFactory.refresh(project)
        close(OK_EXIT_CODE)
    }

    override fun getPreferredFocusedComponent(): JComponent? = table
}
