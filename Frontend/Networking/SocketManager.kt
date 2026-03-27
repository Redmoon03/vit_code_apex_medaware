package deo.raghav.medaware.networking

import deo.raghav.medaware.utility.Constants
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class SocketManager {
    private var socket: Socket? = null

    fun initialize() {
        val options: IO.Options = IO.Options.builder()
            .setTransports(arrayOf("websocket"))
            .setReconnection(true)
            .build()

        socket = IO.socket("http://${Constants.HOST}:${Constants.PORT}", options)
    }

    fun connect() {
        // If socket is already connected or connecting, don't start a new one
        if (socket != null && socket!!.connected()) return

        socket?.on(Socket.EVENT_CONNECT) {
            println("Socket connected successfully")
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val err = args[0]
            if (err is Exception) {
                err.printStackTrace() // Prints the detailed exception
            } else {
                println("Connect error: $err") // Prints the error message
            }
        }

        socket?.on(Socket.EVENT_DISCONNECT) { args ->
            val reason = args[0] as String
            println("Disconnected. Reason: $reason")
        }

        socket?.connect()
    }

    fun getSocketObject(): Socket? {
        return socket
    }

    fun setupListeners(eventName: String, callback: (Any?) -> Unit) {
        socket?.off(eventName)
        socket?.on(eventName) { args ->
            println("DEBUG: SocketManager received event: $eventName with ${args.size} args")

            if (args != null && args.isNotEmpty()) {
                val data = args[0]
                println("DEBUG: Arg[0] type: ${data?.javaClass?.simpleName}")
                callback(data)
            } else {
                callback(null)
                println("DEBUG: Event $eventName received but args were empty!")
            }
        }
    }

    fun sendEvent(event: String, data: JSONObject) {
        if (socket?.connected() == true) {
            socket?.emit(event, data)
        }
    }

    fun send(jpegData: ByteArray) {
        if (socket?.connected() == true) {
            val data = JSONObject()
            data.put("uid", 1)
            data.put("rid", 1)
            data.put("frame", jpegData)
            socket?.emit("raw_frame", data)
            println("Image sent")
        } else {
            println("Socket not connected")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off() // Removes all listeners
    }
}