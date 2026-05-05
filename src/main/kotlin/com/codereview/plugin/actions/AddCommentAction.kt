package com.codereview.plugin.actions

import com.codereview.plugin.ReviewCommentDialog
import com.codereview.plugin.ReviewState
import com.codereview.plugin.ReviewToolWindowFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

class AddCommentAction : AnAction("Add Comment (Ctrl+Alt+R)", "Add a review comment for selected code", AllIcons.General.Add) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val state = ReviewState.getInstance(project)

        if (!state.isReviewActive) {
            Messages.showWarningDialog(
                project,
                "No review is active. Click '▶ Start Review' first.",
                "No Active Review"
            )
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
            ?: FileEditorManager.getInstance(project).selectedTextEditor
            ?: run {
                Messages.showWarningDialog(project, "No file is open in the editor.", "No Editor")
                return
            }

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val filePath = virtualFile?.path ?: "unknown"

        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: ""
        val document = editor.document
        val lineStart = document.getLineNumber(selectionModel.selectionStart) + 1
        val lineEnd = document.getLineNumber(selectionModel.selectionEnd) + 1

        // Get PSI reference (method -> class -> file)
        val reference = buildReference(e, project, editor, virtualFile?.path ?: "")

        val dialog = ReviewCommentDialog(project, filePath, lineStart, lineEnd, selectedText, reference)
        if (dialog.showAndGet()) {
            val finalReference = dialog.getReference()
            val finalSelectedText = dialog.getSelectedText()
            val reviewComment = state.addComment(filePath, lineStart, lineEnd, finalSelectedText, finalReference)
            reviewComment.comment = dialog.getComment()
            ReviewToolWindowFactory.refresh(project)
        }
    }

    private fun buildReference(
        e: AnActionEvent,
        project: com.intellij.openapi.project.Project,
        editor: com.intellij.openapi.editor.Editor,
        filePath: String
    ): String {
        try {
            val document = editor.document
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return fileReference(filePath)
            val offset = editor.caretModel.offset

            // Walk up PSI tree to find method -> class
            var element: PsiElement? = psiFile.findElementAt(offset)
            var methodName: String? = null
            var className: String? = null

            while (element != null) {
                val name = (element as? PsiNamedElement)?.name
                if (name != null) {
                    val typeName = element.javaClass.simpleName
                    when {
                        // Method-like elements
                        typeName.contains("Method", ignoreCase = true) ||
                        typeName.contains("Function", ignoreCase = true) -> {
                            if (methodName == null) methodName = name
                        }
                        // Class-like elements
                        typeName.contains("Class", ignoreCase = true) ||
                        typeName.contains("Interface", ignoreCase = true) -> {
                            if (className == null) className = name
                        }
                    }
                }
                element = element.parent
            }

            // Build reference string
            val namespace = extractNamespace(psiFile, filePath)
            return when {
                className != null && methodName != null -> "$namespace$className::$methodName"
                className != null -> "$namespace$className"
                else -> fileReference(filePath)
            }
        } catch (ex: Exception) {
            return fileReference(filePath)
        }
    }

    private fun extractNamespace(psiFile: PsiFile, filePath: String): String {
        // Try to extract PHP namespace from file text
        val text = psiFile.text
        val nsMatch = Regex("""namespace\s+([\w\\]+)\s*;""").find(text)
        return if (nsMatch != null) {
            "\\" + nsMatch.groupValues[1].replace("\\", "\\") + "\\"
        } else {
            // Fallback: derive from file path (src/... -> \...)
            val parts = filePath.replace("\\", "/")
            val srcIdx = parts.indexOf("/src/")
            if (srcIdx >= 0) {
                "\\" + parts.substring(srcIdx + 5)
                    .substringBeforeLast("/")
                    .replace("/", "\\") + "\\"
            } else ""
        }
    }

    private fun fileReference(filePath: String): String {
        return filePath.substringAfterLast("/")
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: run { e.presentation.isEnabled = false; return }
        val state = ReviewState.getInstance(project)
        e.presentation.isEnabled = state.isReviewActive
        e.presentation.icon = AllIcons.General.Add
    }
}
