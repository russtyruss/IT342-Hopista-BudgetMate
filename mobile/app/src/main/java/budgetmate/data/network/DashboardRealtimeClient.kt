package budgetmate.data.network

import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.plugins.RxJavaPlugins
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent

class DashboardRealtimeClient(
    private val websocketUrl: String,
    private val onDashboardChanged: () -> Unit,
    private val onConnectionLost: () -> Unit
) {

    private var stompClient: StompClient? = null
    private var lifecycleDisposable: Disposable? = null
    private var topicDisposable: Disposable? = null

    fun connect(userId: Long) {
        disconnect()

        val client = try {
            Stomp.over(Stomp.ConnectionProvider.OKHTTP, websocketUrl)
        } catch (_: Exception) {
            onConnectionLost()
            return
        }
        client.withClientHeartbeat(10_000).withServerHeartbeat(10_000)

        lifecycleDisposable = client.lifecycle()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { event ->
                    when (event.type) {
                        LifecycleEvent.Type.OPENED -> subscribeToDashboard(client, userId)
                        LifecycleEvent.Type.ERROR,
                        LifecycleEvent.Type.CLOSED,
                        LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> onConnectionLost()
                    }
                },
                {
                    // Prevent RxJava onError from crashing the app if websocket handshake fails.
                    RxJavaPlugins.onError(it)
                    onConnectionLost()
                }
            )

        stompClient = client
        try {
            client.connect()
        } catch (_: Exception) {
            onConnectionLost()
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
