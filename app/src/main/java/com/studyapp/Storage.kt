package com.studyapp

import android.content.Context
import org.json.JSONArray

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("study_data", Context.MODE_PRIVATE)

    fun saveSessions(sessions: List<StudySession>) {
        val arr = JSONArray()
        sessions.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("sessions", arr.toString()).apply()
    }

    fun loadSessions(): List<StudySession> {
        val raw = prefs.getString("sessions", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { StudySession.fromJson(arr.getJSONObject(it)) }
    }

    fun saveTasks(tasks: List<StudyTask>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

    fun loadTasks(): List<StudyTask> {
        val raw = prefs.getString("tasks", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { StudyTask.fromJson(arr.getJSONObject(it)) }
    }

    fun saveQuizResults(results: List<QuizResult>) {
        val arr = JSONArray()
        results.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("quiz_results", arr.toString()).apply()
    }

    fun loadQuizResults(): List<QuizResult> {
        val raw = prefs.getString("quiz_results", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { QuizResult.fromJson(arr.getJSONObject(it)) }
    }
}
