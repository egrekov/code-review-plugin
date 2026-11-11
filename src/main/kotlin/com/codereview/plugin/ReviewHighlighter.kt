package com.codereview.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent

// Помечает комментарии активной сессии в редакторе так же, как IDE помечает
// изменённые строки: тонкая полоска слева (зона free painters), тонкий маркер
// справа у полосы прокрутки (thin error stripe mark, как у VCS).
// При наведении на gutter в строках комментария полоска утолщается и показывается
// тултип со всеми пунктами ревью, покрывающими эту строку (их может быть несколько —
// у полоски рисуется их количество). Обновляется при изменениях сессии и открытии файлов.
class ReviewHighlighter(private val project: Project) : Disposable {

    companion object {
        // Key.create возвращает один и тот же объект по имени, поэтому все работы
        // с трекингом подсветок консистентны даже между пересозданиями сервиса
        private val HIGHLIGHTERS_KEY = Key.create<MutableList<RangeHighlighter>>("codereview.highlighters")
        private val RANGES_KEY = Key.create<MutableList<BarRange>>("codereview.barRanges")
        private val HOVER_TRACKED_KEY = Key.create<Boolean>("codereview.barHoverTracked")

        fun getInstance(project: Project): ReviewHighlighter =
            project.getService(ReviewHighlighter::class.java)
    }

    // Диапазон строк комментария + все комментарии, его покрывающие (для тултипа)
    private data class BarRange(val range: IntRange, val covering: List<ReviewComment>)

    init {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) = refresh()
                override fun fileClosed(source: FileEditorManager, file: VirtualFile) = refresh()
                override fun selectionChanged(event: FileEditorManagerEvent) = refresh()
            }
        )
    }

    fun refresh() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            hideHoverTooltip()
            applyToOpenEditors()
        }
    }

    private fun applyToOpenEditors() {
        val comments = ReviewSessionManager.getInstance(project).activeSession?.comments ?: emptyList()
        // getService вместо FileEditorManager.getInstance: компилятор против 2026.3 кладёт
        // вызов через companion-объект, которого нет в 2025.1 (verifier: unresolved class)
        val editorManager = project.getService(FileEditorManager::class.java) ?: return
        editorManager.allEditors
            .mapNotNull { it as? TextEditor }
            .map { it.editor }
            .forEach { editor ->
                ensureHoverTracking(editor)
                apply(editor, comments)
            }
    }

    private fun apply(editor: Editor, comments: List<ReviewComment>) {
        val markup = editor.markupModel
        val document = editor.document

        ApplicationManager.getApplication().runWriteAction {
            // refresh() — единственная точка входа: всё, что лежит под ключом, наше,
            // поэтому перед раскладкой заново просто снимаем старое
            editor.getUserData(HIGHLIGHTERS_KEY)?.forEach { markup.removeHighlighter(it) }
            val created = mutableListOf<RangeHighlighter>()
            val ranges = mutableListOf<BarRange>()
            editor.putUserData(HIGHLIGHTERS_KEY, created)
            editor.putUserData(RANGES_KEY, ranges)

            val file = FileDocumentManager.getInstance().getFile(document) ?: return@runWriteAction
            val relevant = comments.filter { it.filePath == file.path }
            if (relevant.isEmpty()) return@runWriteAction

            val lastLine = document.lineCount - 1
            fun lineOf(c: ReviewComment): Int = (c.lineStart - 1).coerceIn(0, lastLine)
            fun endOf(c: ReviewComment): Int {
                val s = lineOf(c)
                return (c.lineEnd - 1).coerceIn(s, lastLine)
            }

            // Группируем по строке начала. В тултип попадают все комментарии, чьи
            // диапазоны покрывают строку индикатора, — на одни и те же строки может
            // указывать несколько пунктов ревью
            val byStartLine = relevant.groupBy { lineOf(it) }
            byStartLine.forEach { (startLine, group) ->
                val endLine = group.maxOf { endOf(it) }
                val covering = relevant.filter { lineOf(it) <= startLine && startLine <= endOf(it) }
                val attrs = TextAttributes().apply {
                    errorStripeColor = STRIPE_COLOR
                }
                val highlighter = markup.addRangeHighlighter(
                    document.getLineStartOffset(startLine),
                    document.getLineEndOffset(endLine),
                    HighlighterLayer.SELECTION - 1,
                    attrs,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                // Тонкий маркер у полосы прокрутки — как у VCS-изменений
                // (по умолчанию полоска рисуется широкой, как у ошибок инспекций)
                (highlighter as RangeHighlighterEx).isThinErrorStripeMark = true
                highlighter.lineMarkerRenderer = CommentBarRenderer(startLine, endLine, covering)
                created.add(highlighter)
                ranges.add(BarRange(startLine..endLine, covering))
            }
        }
    }

    private fun tooltipFor(comments: List<ReviewComment>): String {
        val sb = StringBuilder("<html>")
        if (comments.size > 1) {
            sb.append("<b>Комментарии: ").append(comments.size).append("</b><br>")
        }
        comments.forEachIndexed { i, comment ->
            if (i > 0) sb.append("<br><br>")
            sb.append("<b>#").append(comment.id).append("</b>")
            if (comment.reference.isNotBlank()) {
                sb.append(" <code>").append(escape(comment.reference)).append("</code>")
            }
            if (comment.lineEnd != comment.lineStart) {
                sb.append(" (строки ").append(comment.lineStart).append("–").append(comment.lineEnd).append(")")
            }
            if (comment.comment.isNotBlank()) {
                sb.append("<br>").append(escape(comment.comment).replace("\n", "<br>"))
            }
            if (comment.selectedText.isNotBlank()) {
                val preview = comment.selectedText.take(120).replace("\n", " ")
                sb.append("<br><i>").append(escape(preview)).append("</i>")
            }
        }
        sb.append("</html>")
        return sb.toString()
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // --- Наведение: утолщение полоски + тултип у полоски ---

    private val hoverLines = mutableMapOf<Editor, Int>()
    private var hoverEditor: Editor? = null
    private var hoverLine = -1
    private var hoverBalloon: Balloon? = null

    // Показ переносим на паузу курсора: баллон создаётся только когда курсор
    // замер на строке. При движении всё мгновенно прячется — иначе на каждую
    // строку создаётся новый баллон и остаётся «шлейф» из затухающих тултипов
    private val hoverAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val SHOW_DELAY_MS = 200

    // Платформа не сообщает рендерерам free painters о наведении и не показывает
    // им тултипы, поэтому слушаем gutter сами. Слушатель вешается один раз на
    // редактор и читает актуальные диапазоны из RANGES_KEY, переживая refresh
    private fun ensureHoverTracking(editor: Editor) {
        if (editor.getUserData(HOVER_TRACKED_KEY) == true) return
        val gutter = editor.gutter as? JComponent ?: return
        editor.putUserData(HOVER_TRACKED_KEY, true)
        gutter.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val line = editor.xyToLogicalPosition(Point(0, e.y)).line
                val hit = editor.getUserData(RANGES_KEY)?.firstOrNull { line in it.range }
                updateHover(editor, if (hit != null) line else -1)
                if (hit == null) {
                    hoverAlarm.cancelAllRequests()
                    hideHoverTooltip()
                    return
                }
                if (hoverEditor === editor && hoverLine == line && hoverBalloon != null) return
                // Перешли на другую строку диапазона — прячем сразу, без затухания
                hideHoverTooltip()
                hoverAlarm.cancelAllRequests()
                hoverAlarm.addRequest({
                    if (project.isDisposed) return@addRequest
                    showHoverTooltip(gutter, editor, line, hit.covering, e.point)
                }, SHOW_DELAY_MS)
            }
        })
        gutter.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                hoverAlarm.cancelAllRequests()
                updateHover(editor, -1)
                hideHoverTooltip()
            }
        })
    }

    private fun updateHover(editor: Editor, line: Int) {
        if (hoverLines[editor] == line) return
        hoverLines[editor] = line
        (editor.gutter as? JComponent)?.repaint()
    }

    private fun showHoverTooltip(gutter: JComponent, editor: Editor, line: Int, covering: List<ReviewComment>, point: Point) {
        hideHoverTooltip()

        hoverEditor = editor
        hoverLine = line
        hoverBalloon = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(tooltipFor(covering), null, null, null)
            .setFadeoutTime(4000)
            .createBalloon()
            .also { it.show(RelativePoint(gutter, point), Balloon.Position.atRight) }
    }

    private fun hideHoverTooltip() {
        hoverBalloon?.hide()
        hoverBalloon = null
        hoverEditor = null
        hoverLine = -1
    }

    // Полоска слева в зоне free painters — на весь диапазон строк комментария,
    // как у VCS-маркера. При нескольких комментариях на строку справа от полоски
    // рисуется их количество
    private inner class CommentBarRenderer(
        private val startLine: Int,
        private val endLine: Int,
        private val covering: List<ReviewComment>,
    ) : LineMarkerRendererEx {

        private val ids: List<Int> = covering.map { it.id }.sorted()

        override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.LEFT

        override fun paint(editor: Editor, g: Graphics, bounds: Rectangle) {
            val hovered = hoverLines[editor] in startLine..endLine
            g.color = GUTTER_ACCENT
            val width = if (hovered) 4 else 2
            g.fillRect(bounds.x, bounds.y + 1, width, bounds.height - 2)
            if (covering.size > 1) {
                g.color = JBColor.foreground()
                g.font = JBUI.Fonts.smallFont()
                val fm = g.fontMetrics
                val ty = bounds.y + (bounds.height - fm.height) / 2 + fm.ascent
                g.drawString(covering.size.toString(), bounds.x + width + 2, ty)
            }
        }

        override fun equals(other: Any?): Boolean =
            other is CommentBarRenderer && other.startLine == startLine && other.ids == ids

        override fun hashCode(): Int = 31 * startLine + ids.hashCode()
    }

    override fun dispose() {
        hoverLines.clear()
        hideHoverTooltip()
    }

    // Приглушённый «песочный» тон полоски — отличим от зелёного VCS-маркера;
    // тёмный вариант — тем же тоном, но бледнее
    private val GUTTER_ACCENT = JBColor(Color(0xE6, 0xC7, 0x8A, 0xB4), Color(0xA8, 0x8C, 0x54, 0xB4))
    private val STRIPE_COLOR = JBColor(Color(0xE9, 0xA2, 0x3B), Color(0xB1, 0x7C, 0x1E))
}

// Раскладывает подсветки по уже открытым файлам сразу после старта проекта —
// иначе они появятся только после первого изменения сессии или открытия файла
class ReviewHighlighterStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ReviewHighlighter.getInstance(project).refresh()
    }
}
