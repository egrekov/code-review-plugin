package com.codereview.plugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import javax.swing.table.TableRowSorter

class SessionManagerDialog(private val project: Project) : DialogWrapper(project) {

    private val manager = ReviewSessionManager.getInstance(project)
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")

    companion object {
        private const val ACTIVE_COLUMN = 0
        private const val NAME_COLUMN = 1
        private const val STATUS_COLUMN = 2
        private const val CREATED_COLUMN = 3
    }

    private val tableModel = object : AbstractTableModel() {
        private val columns = arrayOf("", "Name", "Status", "Created")

        override fun getRowCount(): Int = manager.allSessions.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            ACTIVE_COLUMN -> Integer::class.java
            NAME_COLUMN -> String::class.java
            STATUS_COLUMN -> String::class.java
            else -> Long::class.java
        }

        override fun getValueAt(row: Int, column: Int): Any {
            val session = manager.allSessions[row]
            return when (column) {
                ACTIVE_COLUMN -> if (session.id == manager.activeSessionId()) 1 else 0
                NAME_COLUMN -> session.name
                STATUS_COLUMN -> if (session.isActive) "In progress" else "Finished"
                else -> session.createdAt
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
        autoCreateRowSorter = true
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

    private val activeCellRenderer = object : TableCellRenderer {
        private val activeLabel = JLabel(AllIcons.Actions.Checked).apply {
            horizontalAlignment = SwingConstants.CENTER
            isOpaque = false
        }
        private val emptyLabel = JLabel().apply {
            horizontalAlignment = SwingConstants.CENTER
            isOpaque = false
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component = if (value == 1) activeLabel else emptyLabel
    }

    private val createdCellRenderer = object : TableCellRenderer {
        private val renderer = DefaultTableCellRenderer()

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val text = (value as? Long)?.let { dateFormat.format(Date(it)) } ?: ""
            return renderer.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
        }
    }

    private val badgeColor = JBColor(0x8A93A5, 0x555B66)

    private val nameCellRenderer = object : TableCellRenderer {
        private val nameLabel = JLabel().apply {
            horizontalAlignment = SwingConstants.LEFT
            isOpaque = false
        }

        private val badgeLabel = object : JLabel() {
            init {
                horizontalAlignment = SwingConstants.CENTER
                foreground = Color.WHITE
                border = EmptyBorder(0, 5, 0, 5)
                isOpaque = false
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = badgeColor
                    val h = height - 1
                    g2.fillRoundRect(0, 0, width - 1, h, h, h)
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }

        private val panel = JPanel(BorderLayout(4, 0)).apply {
            add(nameLabel, BorderLayout.CENTER)
            add(badgeLabel, BorderLayout.EAST)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val modelRow = table.convertRowIndexToModel(row)
            val session = manager.allSessions.getOrNull(modelRow)
            val name = session?.name ?: value?.toString() ?: ""
            val count = session?.comments?.size ?: 0

            nameLabel.font = table.font
            val fontMetrics = nameLabel.getFontMetrics(nameLabel.font)

            badgeLabel.font = table.font.deriveFont(Font.BOLD, table.font.size2D - 1f)
            badgeLabel.text = count.toString()
            val badgeWidth = badgeLabel.preferredSize.width

            val cellWidth = table.columnModel.getColumn(column).width
            val availableTextWidth = (cellWidth - badgeWidth - 8).coerceAtLeast(20)

            var displayName = name
            if (fontMetrics.stringWidth(displayName) > availableTextWidth) {
                while (displayName.isNotEmpty() && fontMetrics.stringWidth(displayName + "…") > availableTextWidth) {
                    displayName = displayName.dropLast(1)
                }
                displayName += "…"
            }

            nameLabel.text = displayName
            nameLabel.toolTipText = if (displayName != name) name else null
            nameLabel.foreground = if (isSelected) table.selectionForeground else table.foreground

            panel.background = if (isSelected) table.selectionBackground else table.background
            panel.isOpaque = isSelected
            return panel
        }
    }

    private val newButton = toolbarButton(AllIcons.General.Add, "New session") {
        doNewSession()
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

        table.columnModel.getColumn(ACTIVE_COLUMN).apply {
            minWidth = 28
            maxWidth = 28
            preferredWidth = 28
            cellRenderer = activeCellRenderer
        }
        table.columnModel.getColumn(NAME_COLUMN).apply {
            minWidth = 150
            preferredWidth = 300
            cellRenderer = nameCellRenderer
        }
        table.columnModel.getColumn(CREATED_COLUMN).apply {
            preferredWidth = 140
            cellRenderer = createdCellRenderer
        }
        table.rowHeight = 22
        table.removeColumn(viewColumn(STATUS_COLUMN))

        (table.rowSorter as TableRowSorter<*>).setSortKeys(
            listOf(RowSorter.SortKey(CREATED_COLUMN, SortOrder.DESCENDING))
        )

        table.selectionModel.addListSelectionListener { updateButtons() }

        if (tableModel.rowCount > 0) {
            table.setRowSelectionInterval(0, 0)
        }
        updateButtons()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        panel.preferredSize = Dimension(620, 320)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        toolbar.add(newButton)
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

    private fun viewColumn(modelIndex: Int): TableColumn =
        table.columnModel.getColumn(table.convertColumnIndexToView(modelIndex))

    private fun sessionAt(viewRow: Int): ReviewSession? {
        if (viewRow < 0) return null
        val modelRow = table.convertRowIndexToModel(viewRow)
        return manager.allSessions.getOrNull(modelRow)
    }

    private fun doNewSession() {
        val name = manager.promptSessionName() ?: return
        val newSession = manager.newSession(name)
        tableModel.fireTableDataChanged()
        val modelRow = manager.allSessions.indexOfFirst { it.id == newSession.id }
        if (modelRow >= 0) {
            val viewRow = table.convertRowIndexToView(modelRow)
            if (viewRow >= 0) {
                table.setRowSelectionInterval(viewRow, viewRow)
            }
        }
        ReviewToolWindowFactory.refresh(project)
        updateButtons()
    }

    private fun renameSessionAt(viewRow: Int) {
        val session = sessionAt(viewRow) ?: return
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

    private fun deleteSessionAt(viewRow: Int) {
        val session = sessionAt(viewRow) ?: return
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
        val session = sessionAt(table.selectedRow) ?: return
        manager.switchSession(session.id)
        ReviewToolWindowFactory.refresh(project)
        close(OK_EXIT_CODE)
    }

    override fun getPreferredFocusedComponent(): JComponent? = table
}
