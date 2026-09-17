package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SupabaseRealtimeManager(
    private val client: SupabaseClient,
    private val coroutineScope: CoroutineScope,
    private val authTokenProvider: (() -> String?)? = null,
    private val onTableChanged: (table: String, action: String, record: JSONObject?, oldRecord: JSONObject?) -> Unit
) {
    private val tag = "SupabaseRealtime"
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val shouldConnect = AtomicBoolean(false)
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val refCounter = AtomicInteger(1)

    private val tables = listOf("vehicles", "drivers", "bookings", "expenses", "driver_payments", "profiles")

    fun start() {
        if (!client.isConfigured()) return
        shouldConnect.set(true)
        connect()
    }

    fun stop() {
        shouldConnect.set(false)
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected.set(false)
    }

    private fun connect() {
        if (!shouldConnect.get()) return
        if (isConnected.get()) return

        val base = client.baseUrl
        if (base.isBlank()) return

        val token = authTokenProvider?.invoke()
        val tokenParam = if (!token.isNullOrBlank()) "&token=$token" else ""

        val wsUrl = base.replace("http://", "ws://").replace("https://", "wss://") +
                "/realtime/v1/websocket?apikey=${client.apiKey}&vsn=1.0.0$tokenParam"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "Realtime WebSocket connected!")
                isConnected.set(true)
                startHeartbeat()
                joinChannels()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "Realtime WebSocket closing: $code / $reason")
                isConnected.set(false)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "Realtime WebSocket closed: $code / $reason")
                isConnected.set(false)
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "Realtime WebSocket failure: ${t.message}")
                isConnected.set(false)
                scheduleReconnect()
            }
        })
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive && isConnected.get()) {
                delay(25_000)
                try {
                    val ref = refCounter.incrementAndGet().toString()
                    val ping = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", ref)
                    }
                    webSocket?.send(ping.toString())
                } catch (e: Exception) {
                    Log.w(tag, "Error sending heartbeat: ${e.message}")
                }
            }
        }
    }

    private fun joinChannels() {
        val token = authTokenProvider?.invoke()
        tables.forEach { table ->
            val ref = refCounter.incrementAndGet().toString()
            val joinPayload = JSONObject().apply {
                put("topic", "realtime:public:$table")
                put("event", "phx_join")
                put("payload", JSONObject().apply {
                    if (!token.isNullOrBlank()) {
                        put("user_token", token)
                    }
                    put("config", JSONObject().apply {
                        val array = org.json.JSONArray()
                        val rule = JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", table)
                        }
                        array.put(rule)
                        put("postgres_changes", array)
                    })
                })
                put("ref", ref)
            }
            webSocket?.send(joinPayload.toString())

            if (!token.isNullOrBlank()) {
                val tokenMsg = JSONObject().apply {
                    put("topic", "realtime:public:$table")
                    put("event", "access_token")
                    put("payload", JSONObject().put("access_token", token))
                    put("ref", refCounter.incrementAndGet().toString())
                }
                webSocket?.send(tokenMsg.toString())
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")
            val topic = json.optString("topic")

            if (event == "postgres_changes") {
                val payload = json.optJSONObject("payload") ?: return
                val data = payload.optJSONObject("data") ?: return
                val type = data.optString("type") // "INSERT", "UPDATE", "DELETE"
                val table = data.optString("table")
                val record = data.optJSONObject("record")
                val oldRecord = data.optJSONObject("old_record")

                onTableChanged(table, type, record, oldRecord)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing realtime message: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (!shouldConnect.get()) return
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch(Dispatchers.IO) {
            delay(5000)
            if (shouldConnect.get() && !isConnected.get()) {
                connect()
            }
        }
    }
}
