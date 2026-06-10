package com.studyapp

import org.json.JSONArray
import org.json.JSONObject

data class StudySession(
    val id: Long,
    val subject: String,
    val durationSeconds: Int,
    val date: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("subject", subject)
        put("durationSeconds", durationSeconds)
        put("date", date)
    }
    companion object {
        fun fromJson(obj: JSONObject): StudySession = StudySession(
            id = obj.getLong("id"),
            subject = obj.getString("subject"),
            durationSeconds = obj.getInt("durationSeconds"),
            date = obj.getString("date")
        )
    }
}

data class StudyTask(
    val id: Long,
    val text: String,
    val priority: String,
    val done: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("priority", priority)
        put("done", done)
    }
    companion object {
        fun fromJson(obj: JSONObject): StudyTask = StudyTask(
            id = obj.getLong("id"),
            text = obj.getString("text"),
            priority = obj.getString("priority"),
            done = obj.getBoolean("done")
        )
    }
}

data class QuizResult(
    val score: Int,
    val total: Int,
    val date: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("score", score)
        put("total", total)
        put("date", date)
    }
    companion object {
        fun fromJson(obj: JSONObject): QuizResult = QuizResult(
            score = obj.getInt("score"),
            total = obj.getInt("total"),
            date = obj.getLong("date")
        )
    }
}
