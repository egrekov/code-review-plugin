package com.codereview.plugin.actions

import com.codereview.plugin.ReviewSessionManager
import com.codereview.plugin.ReviewState
import com.codereview.plugin.ReviewToolWindowFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class StartReviewAction : AnAction("Start Review", "Start a new code review session", AllIcons.Actions.Execute) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = ReviewState.getInstance(project)
        val manager = ReviewSessionManager.getInstance(project)

        state.startReview(manager.defaultSessionName())

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Code Review")
        toolWindow?.show()

        ReviewToolWindowFactory.refresh(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: run { e.presentation.isEnabled = false; return }
        e.presentation.isEnabled = true
        e.presentation.icon = AllIcons.Actions.Execute
    }
}
