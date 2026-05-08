package budgetmate.shared.data.network

import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import android.util.Log
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent

class DashboardRealtimeClient(
    private val websocketUrl: String,
    private val onDashboardChanged: () -> Unit,
    private val onConnectionLost: () -> Unit
) {

    companion object {
        private const val TAG = "DashboardRealtimeClient"
    }

    private var stompClient: StompClient? = null
    private var lifecycleDisposable: Disposable? = null
    private var topicDisposable: Disposable? = null
    private var reconnectingWithFallback = false

    fun connect(userId: Long) {
        disconnect()
        reconnectingWithFallback = false

        val fallbackUrl = computeFallbackUrl(websocketUrl)
        connectInternal(userId, websocketUrl, fallbackUrl)
    }

    private fun connectInternal(userId: Long, currentUrl: String, fallbackUrl: String?) {
        Log.d(TAG, "Connecting websocket: $currentUrl")

        val client = try {
            Stomp.over(Stomp.ConnectionProvider.OKHTTP, currentUrl)
        } catch (_: Exception) {
            if (!tryFallback(userId, fallbackUrl)) {
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
                            subscribeToDashboard(client, userId)
                        }
                        LifecycleEvent.Type.ERROR,
                        LifecycleEvent.Type.CLOSED,
                        LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> {
                            Log.w(TAG, "WebSocket lifecycle event ${event.type} on $currentUrl")
                            if (!tryFallback(userId, fallbackUrl)) {
                                onConnectionLost()
                            }
                        }
                    }
                },
                { error ->
                    // Keep failures non-fatal so auth/dashboard can continue without realtime.
                    Log.w(TAG, "WebSocket lifecycle error on $currentUrl", error)
                    if (!tryFallback(userId, fallbackUrl)) {
                        onConnectionLost()
                    }
                }
            )

        stompClient = client
        try {
            client.connect()
        } catch (_: Exception) {
            if (!tryFallback(userId, fallbackUrl)) {
                onConnectionLost()
            }
        }
    }

    private fun tryFallback(userId: Long, fallbackUrl: String?): Boolean {
        if (reconnectingWithFallback || fallbackUrl.isNullOrBlank()) {
            return false
        }

        reconnectingWithFallback = true
        disconnect()
        Log.w(TAG, "Retrying websocket with fallback endpoint: $fallbackUrl")
        connectInternal(userId, fallbackUrl, null)
        return true
    }

    private fun computeFallbackUrl(primaryUrl: String): String? {
        return when {
            primaryUrl.endsWith("/ws-native") -> primaryUrl.removeSuffix("/ws-native") + "/ws"
            primaryUrl.endsWith("/ws") -> primaryUrl.removeSuffix("/ws") + "/ws-native"
            else -> null
        }
    }

    private fun subscribeToDashboard(client: StompClient, userId: Long) {
        topicDisposable?.dispose()
        topicDisposable = client.topic("/topic/dashboard/$userId")
            .subscribeOn(Schedulers.io())
            .subscribe(
                { onDashboardChanged() },
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
