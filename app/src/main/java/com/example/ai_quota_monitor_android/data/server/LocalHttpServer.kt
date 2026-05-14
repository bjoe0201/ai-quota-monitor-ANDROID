package com.example.ai_quota_monitor_android.data.server

import com.example.ai_quota_monitor_android.data.repository.DataStoreRepository
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * Local HTTP server that receives data from Tampermonkey JS on PC browser.
 * Mirrors Python local_server.py endpoints: /update, /status, /poll, /health.
 */
class LocalHttpServer(port: Int = 7890) : NanoHTTPD("0.0.0.0", port) {

    override fun serve(session: IHTTPSession): Response {
        // CORS headers for all responses
        val cors = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, X-AI-Monitor-Client",
        )

        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
                .also { r -> cors.forEach { (k, v) -> r.addHeader(k, v) } }
        }

        val resp = when {
            session.method == Method.GET && (session.uri == "/status" || session.uri == "/") -> {
                val data = DataStoreRepository.getAllData()
                val json = JSONObject(data.mapValues { JSONObject(it.value) }).toString()
                newFixedLengthResponse(Response.Status.OK, "application/json", json)
            }

            session.method == Method.GET && session.uri == "/health" -> {
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            }

            session.method == Method.GET && session.uri.startsWith("/poll") -> {
                val clientSeq = session.parms["seq"]?.toIntOrNull() ?: 0
                val serverSeq = DataStoreRepository.getRefreshSeq()
                val json = """{"seq":$serverSeq,"refresh":${serverSeq > clientSeq}}"""
                newFixedLengthResponse(Response.Status.OK, "application/json", json)
            }

            session.method == Method.POST && session.uri == "/update" -> {
                handleUpdate(session)
            }

            else -> {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "application/json", """{"error":"not found"}"""
                )
            }
        }
        cors.forEach { (k, v) -> resp.addHeader(k, v) }
        return resp
    }

    private fun handleUpdate(session: IHTTPSession): Response {
        val bodyMap = mutableMapOf<String, String>()
        session.parseBody(bodyMap)
        val raw = bodyMap["postData"] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "application/json", """{"error":"empty body"}"""
        )

        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "application/json",
                """{"error":"${e.message}"}"""
            )
        }

        val source = json.optString("source", "")
        if (source.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "application/json",
                """{"error":"missing source field"}"""
            )
        }

        // Skip empty payloads
        val skipKeys = setOf("source", "timestamp", "page_url", "received_at")
        val realKeys = json.keys().asSequence().filter { it !in skipKeys }.toList()
        if (realKeys.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.OK, "application/json",
                """{"ok":false,"reason":"empty payload, ignored"}"""
            )
        }

        val data = mutableMapOf<String, Any?>()
        for (key in json.keys()) {
            data[key] = json.opt(key)
        }
        DataStoreRepository.putData(source, data)

        return newFixedLengthResponse(
            Response.Status.OK, "application/json",
            """{"ok":true,"source":"$source"}"""
        )
    }
}
