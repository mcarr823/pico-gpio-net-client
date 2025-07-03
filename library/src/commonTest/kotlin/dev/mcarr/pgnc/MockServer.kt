package dev.mcarr.pgnc

import dev.mcarr.pgnc.PicoGpioNetClient.Companion.toInt
import dev.mcarr.pgnc.enums.Command
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.core.Closeable
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Mock implementation of the PGN daemon class which would be deployed to
 * a Pico device.
 *
 * This is an alternative to the mock_server.py file provided by the
 * PGN repo.
 *
 * It roughly implements the same features, api calls, and function names.
 * It is not identical - nor is it intended to be.
 * However, calls made to this class should be parsed and responded to
 * with the same data format, and in the same manner, making it appropriate
 * for testing purposes as an endpoint where the client doesn't really care
 * about anything but the input/output.
 *
 * This class exists for the sake of unit testing whereby a real Pico
 * device running the PGN daemon is not available.
 *
 * @param ip IP address to bind to. Usually 127.0.0.1
 * @param port Port to bind to. Usually 8080
 * @param name How the server should identify itself when asked
 * @param maxSizeKb Maximum packet size to transfer in one go, measured
 * in KB
 * */
class MockServer(
    val ip: String,
    val port: Int,
    val name: String,
    maxSizeKb: Int
): Closeable{

    /**
     * API version of the PGN daemon which this class currently implements
     * */
    private val apiVersion = 2.toByte()

    /**
     * Input byte buffer.
     *
     * Temporarily stores received byte data until it is needed.
     * */
    private var buffer = ByteArray(0)

    /**
     * Maximum size of byte data which can be read in one go.
     * */
    private val maxReadSize = maxSizeKb * 1024

    /**
     * Map of pins and values.
     *
     * This is a virtual device and doesn't have any hardware GPIO pins,
     * so we use this map to keep track of the fake pins and their values.
     * */
    private val pins = HashMap<Byte, Byte>()

    /**
     * Socket which this class creates and binds to the given ip and port.
     * */
    private lateinit var serverSocket: ServerSocket

    /**
     * Client read channel.
     *
     * Data channel through which this class receives data from a client.
     * */
    private lateinit var readChannel: ByteReadChannel

    /**
     * Client write channel.
     *
     * Data channel through which this class sends data to a client.
     * */
    private lateinit var writeChannel: ByteWriteChannel

    init {
        this.init_spi()
    }

    /**
     * Initializes the SPI device.
     *
     * Doesn't actually do anything, since there isn't a SPI device
     * in the first place.
     * */
    fun init_spi(){}

    /**
     * Closes the server socket connection and frees up the port it was
     * listening on.
     * */
    override fun close(){
        this.serverSocket.close()
    }

    /**
     * Creates a socket and binds it to the given ip address and port.
     * */
    suspend fun open(){
        this.serverSocket =
            aSocket(SelectorManager(Dispatchers.Default))
                .tcp()
                .bind(hostname = ip, port = port){
                    this.backlogSize = 1
                }
    }

    /**
     * Opens a socket connection and runs the callback,
     * then finishes by closing the socket afterwards.
     *
     * Follows the usual "use" function syntax and use case
     * of other closeable resources.
     *
     * @param callback Callback to invoke after the socket
     * connection has opened. Provides an instance of the
     * mock server class in the callback.
     * */
    suspend fun use(
        callback: suspend (server: MockServer) -> Unit
    ){
        this.open()
        try {
            callback(this)
        }finally {
            this.close()
        }
    }

    /**
     * Runs the mock server in "daemon" mode.
     *
     * The class runs in a loop, perpetually waiting for a connection
     * to be established, and for the client to send it commands.
     *
     * This function will exit once the client disconnects.
     *
     * Note that you should not run this function inside of a "use" block,
     * nor do you need to open the socket first.
     * This function does both of those things already.
     *
     * Also, this class waits for data indefinitely, so it should be run
     * on its own thread.
     *
     * See the PicoGpioNetClientTest for an example of how it should be
     * used when unit testing.
     *
     * @see PicoGpioNetClientTest
     * */
    suspend fun runDaemon() {

        println("run daemon on port $port")

        // Open and close the connection automatically
        this.use { server ->

            println("awaiting connection")
            val client = server.serverSocket.accept()

            println("got connection")
            readChannel = client.openReadChannel()
            writeChannel = client.openWriteChannel(autoFlush = true)

            println("opened channels")
            this.buffer = ByteArray(0)

            // Loop until client disconnects
            while (client.isActive) {

                try {
                    val result = this.runCommand()
                    println("Writing ${result.size} bytes to client")
                    writeChannel.writeByteArray(result)
                }catch (e: kotlinx.coroutines.CancellationException){
                    client.close()
                    break
                }catch (e: Exception) {
                    e.printStackTrace()
                    client.close()
                    break
                }

            }

            println("closed connection")
        }
    }

    /**
     * Equivalent of the python version's run_command function.
     * */
    suspend fun runCommand(): ByteArray {

        println("Awaiting command")

        val command = this.takeFromBufferSingle(1)[0]
        val cmd = Command.entries.find { it.value == command }

        println("Command ${command}")

        when (cmd) {
            Command.CMD_SET_PIN_SINGLE -> this.cmdSetPinSingle()
            Command.CMD_SET_PIN_MULTI -> this.cmdSetPinMulti()
            Command.CMD_WRITE_BYTES -> this.cmdWriteBytes()
            Command.CMD_GET_PIN_SINGLE -> return byteArrayOf(this.cmdGetPinSingle())
            Command.CMD_GET_PIN_MULTI -> return this.cmdGetPinMulti()
            Command.CMD_DELAY -> this.cmdDelay()
            Command.CMD_WAIT_FOR_PIN -> this.cmdWaitForPin()
            Command.CMD_GET_NAME -> return this.cmdGetName()
            Command.CMD_GET_API_VERSION -> return byteArrayOf(this.apiVersion)
            else -> println("Unknown")
        }

        return byteArrayOf(1)
    }

    /**
     * Equivalent of the python version's cmd_delay function.
     * */
    suspend fun cmdDelay(){
        println("Delay")

        val delay_ms = this.readLengthHeader(2).toLong()
        val delay_seconds = delay_ms / 1000.0

        println("Seconds: ${delay_seconds}")
        delay(delay_ms)
    }

    /**
     * Equivalent of the python version's cmd_wait_for_pin function.
     * */
    suspend fun cmdWaitForPin() {

        println("Wait for pin")

        val data = this.takeFromBufferSingle(2)
        val pin = data[0]
        val value = data[1]
        val delay_ms = this.readLengthHeader(2)
        val delay_seconds = delay_ms / 1000.0
        println("Wait for ${delay_seconds} seconds")

        // TODO actually implement a delay for the test?
    }

    /**
     * Equivalent of the python version's cmd_write_bytes function.
     * */
    suspend fun cmdWriteBytes() {
        println("Write bytes")
        val numberOfBytes = this.readLengthHeader(4)
        this.takeFromBuffer(numberOfBytes){ bytes ->
            // Pretending to write...
        }
    }

    /**
     * Equivalent of the python version's read_spi function.
     * */
    fun readSpi(numberOfBytes: Int): ByteArray {
        return ByteArray(numberOfBytes){ 0.toByte() }
    }

    /**
     * Equivalent of the python version's cmd_set_pin_single function.
     * */
    suspend fun cmdSetPinSingle() {

        println("Set pin")

        val pair = this.takeFromBufferSingle(2)

        this.setPin(pair)
    }

    /**
     * Equivalent of the python version's cmd_set_pin_multi function.
     * */
    suspend fun cmdSetPinMulti() {

        println("Set pins")
        val numberOfPairs = this.readLengthHeader(1)
        val numberOfBytes = numberOfPairs * 2

        this.takeFromBuffer(numberOfBytes) { pairs ->
            pairs.toList().chunked(2).forEach { pair ->
                this.setPin(pair.toByteArray())
            }
        }
    }

    /**
     * Equivalent of the python version's set_pin function.
     * */
    fun setPin(pair: ByteArray) {
        val pin = pair[0]
        val value = pair[1]
        println("Setting pin ${pin} to ${value}")
        this.cachePin(pin)
        this.pins[pin] = value
    }

    /**
     * Equivalent of the python version's cache_pin function.
     * */
    fun cachePin(pin: Byte){
        if (!this.pins.containsKey(pin)) {
            this.pins[pin] = 0
        }
    }

    /**
     * Equivalent of the python version's cmd_get_pin_multi function.
     * */
    suspend fun cmdGetPinMulti(): ByteArray {

        println("Get pins")
        val numberOfBytes = this.readLengthHeader(1)
        val returnData = ArrayList<Byte>()
        this.takeFromBuffer(numberOfBytes) { byteArray ->
            byteArray.mapTo(returnData) { pin -> this.getPin(pin) }
        }
        return returnData.toByteArray()
    }

    /**
     * Equivalent of the python version's cmd_get_pin_single function.
     * */
    suspend fun cmdGetPinSingle(): Byte {
        println("Get pin single")
        val pin = this.takeFromBufferSingle(1)
        return this.getPin(pin[0])
    }

    /**
     * Equivalent of the python version's get_pin function.
     * */
    fun getPin(pin: Byte): Byte {
        println("Getting pin ${pin}")
        this.cachePin(pin)
        return this.pins[pin] ?: 0
    }

    /**
     * Equivalent of the python version's cmd_get_name function.
     * */
    fun cmdGetName(): ByteArray {
        println("Get name (${this.name})")
        val nameBytes = this.name.encodeToByteArray()
        val nameLength = nameBytes.size
        val nameLengthBytes = byteArrayOf(nameLength.toByte())
        println("Returning ${nameLengthBytes.size} + ${nameBytes.size}")
        return nameLengthBytes + nameBytes
    }

    /**
     * Equivalent of the python version's read_length_header function.
     * */
    suspend fun readLengthHeader(numberOfBytes: Int): Int {
        val request = this.takeFromBufferSingle(numberOfBytes)
        return request.toInt(numberOfBytes)
    }

    /**
     * Equivalent of the python version's read_into_buffer function.
     * */
    suspend fun readIntoBuffer() {
        do {
            if (readChannel.isClosedForRead || writeChannel.isClosedForWrite)
                throw Exception("Socket connection closed")
            val available = minOf(readChannel.availableForRead, this.maxReadSize)
            val bytesRead: ByteArray = readChannel.readByteArray(available)
            val length = bytesRead.size
            this.buffer += bytesRead
            //println("Read ${length} bytes")
            delay(10)
        }while (length == 0)
    }

    /**
     * Equivalent of the python version's take_from_buffer_single function.
     * */
    suspend fun takeFromBufferSingle(numberOfBytes: Int): ByteArray {
        val returnData = ArrayList<Byte>()
        this.takeFromBuffer(numberOfBytes) { loopBytes ->
            returnData.addAll(loopBytes.toList())
        }
        return returnData.toByteArray()
    }

    /**
     * Equivalent of the python version's take_from_buffer function.
     * */
    suspend fun takeFromBuffer(numberOfBytes: Int, callback: (bytes: ByteArray) -> Unit): Int {
        var returnedBytes = 0
        println("reading from buffer")
        while (returnedBytes < numberOfBytes) {

            val remaining = numberOfBytes - returnedBytes
            var bufferLength = this.buffer.size

            if (bufferLength == 0) {
                this.readIntoBuffer()
                bufferLength = this.buffer.size
            }

            val bytesToRead = minOf(remaining, bufferLength)
            returnedBytes += bytesToRead
            val bytes = this.buffer.take(bytesToRead).toByteArray()
            callback(bytes)
            this.buffer = this.buffer.drop(bytesToRead).toByteArray()
        }
        return returnedBytes
    }

}