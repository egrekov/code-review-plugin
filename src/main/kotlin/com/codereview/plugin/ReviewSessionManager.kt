package com.codereview.plugin

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

class ReviewSessionManager private constructor(private val project: Project) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val sessionsFile: File
        get() = File(File(project.basePath ?: "", ".idea"), SESSIONS_FILE_NAME)

    private var sessions: MutableList<ReviewSession> = load()
    private var activeSessionId: String? = null

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
        sessions.add(session)
        activeSessionId = session.id
        save()
        return session
    }

    fun switchSession(id: String) {
        sessions.find { it.id == id }?.let {
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
            activeSessionId = sessions.firstOrNull()?.id
        }
        save()
    }

    fun renameSession(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        sessions.find { it.id == id }?.let { it.name = trimmed }
        save()
    }

    fun markActiveFinished() {
        activeSession?.let {
            it.isActive = false
            save()
        }
    }

    fun clearAllSessions() {
        sessions.clear()
        activeSessionId = null
        save()
    }

    fun saveActiveSession() {
        activeSession?.let { save() }
    }

    fun defaultSessionName(): String {
        val repos = GitRepositoryManager.getInstance(project).repositories
        val repo = repos.firstOrNull { it.root.path == project.basePath }
            ?: repos.firstOrNull()
        val repoName = repo?.root?.name ?: project.name
        val branch = repo?.currentBranchName
        val time = SimpleDateFormat("dd.MM.yyyy HH:mm").format(Date())
        return if (branch != null) "$repoName [$branch] $time" else "$repoName $time"
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
