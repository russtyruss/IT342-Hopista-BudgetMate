package edu.cit.hopista.budgetmate.shared.data.network

import android.util.Log
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent

class AdminUsersRealtimeClient(
    private val websocketUrl: String,
    private val onUsersChanged: () -> Unit,
    private val onConnectionLost: () -> Unit
) {

    companion object {
        private const val TAG = "AdminUsersRealtimeClient"
    }

    private var stompClient: StompClient? = null
    private var lifecycleDisposable: Disposable? = null
    private var topicDisposable: Disposable? = null
    private var reconnectingWithFallback = false

    fun connect() {
        disconnect()
        reconnectingWithFallback = false

        val fallbackUrl = computeFallbackUrl(websocketUrl)
        connectInternal(websocketUrl, fallbackUrl)
    }

    private fun connectInternal(currentUrl: String, fallbackUrl: String?) {
        Log.d(TAG, "Connecting websocket: $currentUrl")

        val client = try {
            Stomp.over(Stomp.ConnectionProvider.OKHTTP, currentUrl)
        } catch (_: Exception) {
            if (!tryFallback(fallbackUrl)) {
                onConnectionLost()
            }
            return
        }
        client.withClientHeartbeat(10_000).withServerHeartbeat(10_000)

        lifecycleDisposable = client.lifecycle()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { event ->
                    when (event.type) {
                        LifecycleEvent.Type.OPENED -> {
                            reconnectingWithFallback = false
                            subscribeToUsers(client)
                        }

                        LifecycleEvent.Type.ERROR,
                        LifecycleEvent.Type.CLOSED,
                        LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> {
                            Log.w(TAG, "WebSocket lifecycle event ${event.type} on $currentUrl")
                            if (!tryFallback(fallbackUrl)) {
                                onConnectionLost()
                            }
                        }
                    }
                },
                { error ->
                    Log.w(TAG, "WebSocket lifecycle error on $currentUrl", error)
                    if (!tryFallback(fallbackUrl)) {
                        onConnectionLost()
                    }
                }
            )

        stompClient = client
        try {
            client.connect()
        } catch (_: Exception) {
            if (!tryFallback(fallbackUrl)) {
                onConnectionLost()
            }
        }
    }

    private fun tryFallback(fallbackUrl: String?): Boolean {
        if (reconnectingWithFallback || fallbackUrl.isNullOrBlank()) {
            return false
        }

        reconnectingWithFallback = true
        disconnect()
        Log.w(TAG, "Retrying websocket with fallback endpoint: $fallbackUrl")
        connectInternal(fallbackUrl, null)
        return true
    }

    private fun computeFallbackUrl(primaryUrl: String): String? {
        return when {
            primaryUrl.endsWith("/ws-native") -> primaryUrl.removeSuffix("/ws-native") + "/ws"
            primaryUrl.endsWith("/ws") -> primaryUrl.removeSuffix("/ws") + "/ws-native"
            else -> null
        }
    }

    private fun subscribeToUsers(client: StompClient) {
        topicDisposable?.dispose()
        topicDisposable = client.topic("/topic/admin/users")
            .subscribeOn(Schedulers.io())
            .subscribe(
                { onUsersChanged() },
                { onConnectionLost() }
            )
    }

    fun disconnect() {
        topicDisposable?.dispose()
        topicDisposable = null

        lifecycleDisposable?.dispose()
        lifecycleDisposable = null

        stompClient?.disconnect()
        stompClient = null
    }
}