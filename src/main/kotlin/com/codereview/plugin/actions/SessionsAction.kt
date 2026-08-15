package com.codereview.plugin.actions

import com.codereview.plugin.ReviewToolWindowFactory
import com.codereview.plugin.SessionManagerDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class SessionsAction : AnAction("Review Sessions", "Open saved review sessions", AllIcons.Actions.ListFiles) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SessionManagerDialog(project).show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: run { e.presentation.isEnabled = false; return }
        e.presentation.isEnabled = true
    }
}
