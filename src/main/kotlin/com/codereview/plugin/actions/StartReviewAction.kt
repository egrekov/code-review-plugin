package com.codereview.plugin.actions

import com.codereview.plugin.ReviewState
import com.codereview.plugin.ReviewToolWindowFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager

class StartReviewAction : AnAction("Start Review", "Start a new code review session", AllIcons.Actions.Execute) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = ReviewState.getInstance(project)

        if (state.isReviewActive) {
            val confirm = Messages.showYesNoDialog(
                project,
                "A review is already in progress (${state.comments.size} comments). Start a new one? Current comments will be lost.",
                "Restart Review?",
                Messages.getQuestionIcon()
            )
            if (confirm != Messages.YES) return
        }

        state.startReview()

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Code Review")
        toolWindow?.show()

        ReviewToolWindowFactory.refresh(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: run { e.presentation.isEnabled = false; return }
        val state = ReviewState.getInstance(project)
        e.presentation.isEnabled = true
        e.presentation.icon = AllIcons.Actions.Execute
    }
}
