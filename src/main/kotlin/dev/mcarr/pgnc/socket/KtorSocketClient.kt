package dev.mcarr.pgnc.socket

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class KtorSocketClient : Closeable {

    private lateinit var socket: Socket
    private lateinit var receiveChannel: ByteReadChannel
    private lateinit var sendChannel: ByteWriteChannel

    suspend fun connect(
        ipAddress: String,
        port: Int
    ){
        val selectorManager = SelectorManager(Dispatchers.IO)

        socket = aSocket(selectorManager)
            .tcp()
            .connect(ipAddress, port)

        receiveChannel = socket.openReadChannel()
        sendChannel = socket.openWriteChannel()
    }

    suspend fun write(bytes: ByteArray, flush: Boolean = false, timeout: Long = 5_000L) =
        withTimeout(timeout) {
            sendChannel.writeFully(bytes)
            if (flush) sendChannel.flush()
        }

    suspend fun flush(timeout: Long = 5_000L){
        withTimeout(timeout) {
            sendChannel.flush()
        }
    }

    /**
     * @return returns result of read operation
     * @throws TimeoutCancellationException if timeout is reached
     * */
    suspend fun read(length: Int, timeout: Long = 5_000L): ByteArray =
        withTimeout(timeout) {
            val body = ByteArray(length)
            receiveChannel.readFully(body)
            body
        }

    override fun close() {

        try {
            receiveChannel.cancel()
            sendChannel.cancel(Exception("Close function called manually"))
            socket.close()
        }catch (e: UninitializedPropertyAccessException){

            // This occurs if the connect() function wasn't called,
            // or the connection to the server failed.
            e.printStackTrace()

        }

    }



}