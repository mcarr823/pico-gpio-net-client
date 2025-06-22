package dev.mcarr.pgnc.socket

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Client class for managing the connection to the PGN daemon.
 *
 * This is the class responsible for managing the underlying
 * socket connections, and sending raw byte data back and
 * forth over the socket.
 *
 * It is a generic socket class, and does not contain any logic
 * which is specific to PGN.
 *
 * If you are implementing this class, chances are that you want
 * the PicoGpioNetClient class instead.
 *
 * Example usage:
 * ```
 * val response = KtorSocketClient().use{ client ->
 *     client.connect(ip, port)
 *     client.write(someBytes, flush=true)
 *     client.read(expectedResponseLength)
 * }
 * ```
 *
 * @see dev.mcarr.pgnc.PicoGpioNetClient
 * */
class KtorSocketClient : Closeable {

    /**
     * TCP socket connection to the PGN daemon.
     * */
    private lateinit var socket: Socket

    /**
     * Read channel. Receives bytes from the PGN daemon.
     * */
    private lateinit var receiveChannel: ByteReadChannel

    /**
     * Write channel. Sends bytes to the PGN daemon.
     * */
    private lateinit var sendChannel: ByteWriteChannel

    /**
     * Attempts to establish a connection to the PGN daemon.
     *
     * This function must be called prior to attempting any
     * reads or writes.
     *
     * @param ipAddress IP address of the Pico device running
     * PGN which we want to connect to.
     * @param port Port on which PGN is running. This is
     * usually port 8080.
     * */
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

    /**
     * Sends data to the PGN daemon.
     *
     * Throws an exception if the operation takes too long.
     *
     * @param bytes Raw byte data to send to the daemon.
     * @param flush If true, automatically flush the write channel
     * after queueing the data to be sent.
     * @param timeout Time in milliseconds after which the write
     * operation should be aborted if it still hasn't completed.
     *
     * @throws TimeoutCancellationException if timeout is reached
     * */
    suspend fun write(bytes: ByteArray, flush: Boolean = false, timeout: Long = 5_000L) =
        withTimeout(timeout) {
            sendChannel.writeFully(bytes)
            if (flush) sendChannel.flush()
        }

    /**
     * Flushes the write channel.
     *
     * This can be used to manually flush the channel if automatic
     * flushing has been disabled.
     *
     * ie. if `write(ByteArray, Boolean, Long)` has been called with
     * the middle `flush` parameter set to false.
     *
     * @param timeout Time in milliseconds after which the flush
     * operation should be aborted if it still hasn't completed.
     *
     * @throws TimeoutCancellationException if timeout is reached
     * */
    suspend fun flush(timeout: Long = 5_000L){
        withTimeout(timeout) {
            sendChannel.flush()
        }
    }

    /**
     * Receives the specified number of bytes from the read channel.
     *
     * @param length How many bytes we expect to receive from the channel.
     * @param timeout Time in milliseconds after which the read
     * operation should be aborted if it still hasn't completed.
     *
     * @return Bytes received from the socket. Should be exactly
     * `length` bytes in length.
     *
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