package com.cloudx.client

/**
 * Simple singleton that holds shared state across Activities.
 * In a production app this would be a ViewModel + Repository,
 * but for v1 this keeps things simple and easy to follow.
 */
object ClientState {
    var password: String = ""
    var sessionId: String = ""
    var games: List<GameItem> = emptyList()
    var signalingClient: SignalingClient? = null
}
