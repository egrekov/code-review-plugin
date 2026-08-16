package com.codereview.plugin

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.io.IOException
import java.util.UUID

class ReviewSessionManager private constructor(private val project: Project) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val sessionsFile: File
        get() = File(File(project.basePath ?: "", ".idea"), SESSIONS_FILE_NAME)

    private var sessions: MutableList<ReviewSession> = load()
    private var activeSessionId: String? = sessions.filter { it.isActive }.maxByOrNull { it.createdAt }?.id

    init {
        if (sessions.any { it.isActive != (it.id == activeSessionId) }) {
            sessions.forEach { it.isActive = it.id == activeSessionId }
            save()
        }
    }

    val allSessions: List<ReviewSession> get() = sessions

    val activeSession: ReviewSession?
        get() = sessions.find { it.id == activeSessionId }

    fun activeSessionId(): String? = activeSessionId

    fun newSession(name: String): ReviewSession {
        val session = ReviewSession(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Session ${sessions.size + 1}" },
            isActive = true
        )
        sessions.forEach { it.isActive = false }
        sessions.add(session)
        activeSessionId = session.id
        save()
        return session
    }

    fun switchSession(id: String) {
        sessions.find { it.id == id }?.let {
            sessions.forEach { s -> s.isActive = false }
            activeSessionId = it.id
            it.isActive = true
            save()
        }
    }

    fun deleteSession(id: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        sessions.removeAt(index)
        if (activeSessionId == id) {
            activeSessionId = null
            sessions.forEach { it.isActive = false }
        }
        save()
    }

    fun renameSession(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        sessions.find { it.id == id }?.let { it.name = trimmed }
        save()
    }

    fun clearAllSessions() {
        sessions.clear()
        activeSessionId = null
        save()
    }

    fun saveActiveSession() {
        activeSession?.let { save() }
    }

    fun promptSessionName(): String? {
        val repos = GitRepositoryManager.getInstance(project).repositories
        val repo = repos.firstOrNull { it.root.path == project.basePath }
            ?: repos.firstOrNull()
        val branch = repo?.currentBranchName
        if (branch != null) return branch
        val name = Messages.showInputDialog(
            project,
            "Enter a name for the review session:",
            "New Review Session",
            Messages.getQuestionIcon(),
            null,
            object : InputValidator {
                override fun checkInput(inputString: String): Boolean = inputString.isNotBlank()
                override fun canClose(inputString: String): Boolean = inputString.isNotBlank()
            }
        )
        return name?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun load(): MutableList<ReviewSession> {
        return try {
            val file = sessionsFile
            if (!file.exists()) {
                return mutableListOf()
            }
            val type = object : TypeToken<MutableList<ReviewSession>>() {}.type
            gson.fromJson<MutableList<ReviewSession>>(file.readText(), type) ?: mutableListOf()
        } catch (e: IOException) {
            mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    private fun save() {
        try {
            val file = sessionsFile
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(sessions))
        } catch (e: IOException) {
            // Ignore write failures — sessions stay in memory
        }
    }

    companion object {
        private const val SESSIONS_FILE_NAME = "code-review-sessions.json"
        private val instances = mutableMapOf<Project, ReviewSessionManager>()

        fun getInstance(project: Project): ReviewSessionManager =
            instances.getOrPut(project) { ReviewSessionManager(project) }
    }
}
