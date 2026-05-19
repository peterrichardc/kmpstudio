package studio.kmp.frontend.ws

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import studio.kmp.frontend.interop.*
import studio.kmp.shared.model.WsMessage

enum class WsState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class WsClient {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state    = MutableStateFlow(WsState.DISCONNECTED)
    val state: StateFlow<WsState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 256)
    val messages: SharedFlow<WsMessage> = _messages.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }

    private var pollJob: Job? = null
    private var activePort: Int = -1

    fun connect(port: Int) {
        activePort = port
        pollJob?.cancel()
        wsConnect("ws://127.0.0.1:$port/ws")
        _state.value = WsState.CONNECTING

        pollJob = scope.launch {
            var reconnectMs = 2_000L
            while (isActive) {
                val current = when (wsGetState()) {
                    "connected"  -> WsState.CONNECTED
                    "connecting" -> WsState.CONNECTING
                    "error"      -> WsState.ERROR
                    else         -> WsState.DISCONNECTED
                }
                _state.value = current

                when (current) {
                    WsState.DISCONNECTED, WsState.ERROR -> {
                        delay(reconnectMs)
                        reconnectMs = minOf(reconnectMs * 2, 30_000L)
                        wsConnect("ws://127.0.0.1:$activePort/ws")
                        _state.value = WsState.CONNECTING
                    }
                    WsState.CONNECTED -> {
                        reconnectMs = 2_000L
                        while (true) {
                            val raw = wsPopMessage() ?: break
                            runCatching { _messages.emit(json.decodeFromString<WsMessage>(raw)) }
                        }
                        delay(16)
                    }
                    WsState.CONNECTING -> delay(16)
                }
            }
        }
    }

    fun send(msg: WsMessage) {
        wsSend(json.encodeToString(WsMessage.serializer(), msg))
    }

    fun disconnect() {
        pollJob?.cancel()
        wsClose()
        _state.value = WsState.DISCONNECTED
    }

    fun dispose() {
        disconnect()
        scope.cancel()
    }
}
