package com.codereview.plugin.actions

import com.codereview.plugin.ReviewReportDialog
import com.codereview.plugin.ReviewState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class FinishReviewAction : AnAction("Get Report", "Generate Redmine report", AllIcons.Actions.Copy) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = ReviewState.getInstance(project)

        if (!state.isReviewActive) {
            Messages.showWarningDialog(project, "No review is active.", "No Active Review")
            return
        }

        if (state.comments.isEmpty()) {
            Messages.showWarningDialog(project, "No comments added yet.", "Nothing to Report")
            return
        }

        // Just show the report — review stays active
        val report = state.generateRedmineReport()
        ReviewReportDialog(project, report).show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: run { e.presentation.isEnabled = false; return }
        val state = ReviewState.getInstance(project)
        e.presentation.isEnabled = state.isReviewActive && state.comments.isNotEmpty()
        e.presentation.icon = AllIcons.Actions.Copy
    }
}
