package dev.mcarr.pgnc

import dev.mcarr.pgnc.classes.Packet
import dev.mcarr.pgnc.enums.Command
import dev.mcarr.pgnc.socket.KtorSocketClient
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*

/**
 * Client for interacting with the PGN daemon.
 *
 * This is the class responsible for translating human-readable
 * function names and variables to raw byte data, and transmitting
 * that data back and forth over the socket.
 *
 * To use this class, you must first call `connect()`, and you should
 * remember to close the client after you've finished using it.
 *
 * While connected, you can issue commands to the PGN daemon by calling
 * the functions within this class.
 *
 * Example usage:
 * ```
 * val serverName = PicoGpioNetClient(ip, port).use{ client ->
 *     client.connect()
 *     val apiVersion = client.getApiVersion()
 *     if (apiVersion >= Command.CMD_GET_NAME.apiVersion)
 *         client.getName()
 *     else
 *         "Unknown device name"
 * }
 * ```
 *
 * @param ip IP address of the Pico device running
 * PGN which we want to connect to.
 * @param port Port on which PGN is running. This is
 * usually port 8080.
 * @param autoFlush If true, flush all writes to the
 * PGN daemon automatically.
 * */
class PicoGpioNetClient(
    private val ip: String,
    private val port: Int,
    private val autoFlush: Boolean = false
) : Closeable {

    /**
     * Underlying TCP socket connection which handles the
     * actual transmission of data.
     * */
    private val sock = KtorSocketClient()

    /**
     * Pending write requests.
     * */
    private val queue = ArrayList<Packet>()

    /**
     * Attempts to establish a connection to the PGN daemon.
     *
     * This function must be called prior to attempting any
     * reads or writes.
     * */
    suspend fun connect(){
        sock.connect(ip, port)
    }

    override fun close(){
        sock.close()
    }

    /**
     * Manually flushes the queue.
     *
     * Writes all pending data to the queue and waits until
     * responses have been received for all data packets.
     *
     * This function must be called manually if `autoFlush` is
     * set to false.
     *
     * @return List of success states for each data packet's command.
     * ie. True if the operation ran successfully on the PGN daemon.
     * */
    suspend fun flush(): List<Boolean> {

        if (queue.isEmpty()) return listOf()

        println("Flushing: ${queue.size}")

        // Queue all of the data
        queue.forEach {
            sock.write(it.data)
        }

        // Then send it all at once
        sock.flush()

        // Then wait for the results of all of those operations
        val results = read(queue.size)
        val boolResults = List<Boolean>(queue.size){ i ->
            results[i].toInt() == 1
        }

        queue.clear()

        return boolResults
    }

    /**
     *  Queues up an arbitrary write request.
     *
     *  A write request is one which doesn't expect any meaningful
     *  data in response.
     *  For example, a request which either succeeds or fails, and
     *  doesn't provide any further insight into the operation
     *  beyond that.
     *
     *  @param packet Data packet which contains an array of bytes to
     *  send to the PGN daemon.
     * */
    suspend fun write(packet: Packet){
        queue.add(packet)
        println("Queue count: ${queue.size}")
        if (autoFlush) flush()
    }

    /**
     * Performs a read request after flushing the queue, if needed.
     *
     * A read request is one which expects a meaningful response.
     * For example, reading data from SPI, or reading a pin's state.
     *
     *  @param packet Data packet which contains an array of bytes to
     *  send to the PGN daemon.
     * @param length Expected length of the response from the server
     *
     * @return Server response to the read request
     * */
    suspend fun read(packet: Packet, length: Int): ByteArray {
        flush()
        sock.write(packet.data, flush = true)
        return read(length)
    }

    /**
     * Reads the specified number of bytes from the socket.
     *
     * @param length Number of bytes to read from the socket
     *
     * @return Bytes read from the socket
     * */
    suspend fun read(length: Int): ByteArray =
        sock.read(length)

    /**
     * Sets the state of a single pin.
     *
     * @param pin The pin to change the state of
     * @param value New value to set for the pin
     * */
    suspend fun setPin(pin: Byte, value: Byte){
        Packet.Builder(Command.CMD_SET_PIN_SINGLE)
            .addData(pin)
            .addData(value)
            .write()
    }

    /**
     * Sets the states of multiple pins.
     *
     * @param pinsAndValues List of pairs, where the first value
     * of each pair is the pin, and the second value of each pair
     * is the value which that pin should be set to.
     * */
    suspend fun setPins(pinsAndValues: List<Pair<Byte, Byte>>){
        val numberOfPins = pinsAndValues.size.toByte()
        val pinData = ByteArray(numberOfPins * 2)
        pinsAndValues.forEachIndexed { index, (pin, value) ->
            val i = index * 2
            pinData[i] = pin
            pinData[i+1] = value
        }
        Packet.Builder(Command.CMD_SET_PIN_MULTI)
            .addData(numberOfPins)
            .addData(pinData)
            .write()
    }

    /**
     * Retrieves the value of a single pin.
     *
     * @param pin Pin to read the value of
     *
     * @return The value of that pin.
     * */
    suspend fun getPin(pin: Byte): Byte =
        Packet.Builder(Command.CMD_GET_PIN_SINGLE)
            .addData(pin)
            .readSingle()

    /**
     * Retrieves the value of multiple pins.
     *
     * @param pins Array of pins to read the values of
     *
     * @return An array of pin values, in order.
     * eg. If you send pins [16,18] and got a response of [0,1]
     * then that means pin 16 has value 0, and pin 18 has value 1.
     * */
    suspend fun getPins(pins: ByteArray): ByteArray {
        val numberOfPins = pins.size
        println("Getting $numberOfPins pins")
        return Packet.Builder(Command.CMD_GET_PIN_MULTI)
            .addData(numberOfPins.toByte())
            .addData(pins)
            .read(numberOfPins)
    }

    /**
     * Sends raw byte data to write over SPI.
     *
     * @param data Array of bytes to write to the SPI device
     * */
    suspend fun spiWrite(data: ByteArray){
        println("SPI write. Length: ${data.size}")
        val lengthBytes = data.size.toByteArray()
        Packet.Builder(Command.CMD_WRITE_BYTES)
            .addData(lengthBytes)
            .addData(data)
            .write()
    }

    /**
     * Tells the Pico server to wait for a defined amount of time
     * before moving onto the next request.
     *
     * This is useful when sending through multiple commands in a
     * single packet.
     *
     * For example, let's say your GPIO device requires you to wait
     * for 10ms after setting a pin before writing SPI data.
     *
     * One way of doing this would be for your client application to
     * send a SET_PIN command, wait 10ms, then send a WRITE_BYTES command.
     *
     * Another way of doing this would be to send a SET_PIN command, a
     * DELAY command, and a WRITE_BYTES command all in one packet.
     *
     * The second approach sends 3 commands instead of 2, but it does so
     * in 1 packet instead of 2, making it faster overall due to network
     * latency and packet size constraints.
     *
     * @param millis Time to wait in milliseconds
     * */
    suspend fun delay(millis: Short){
        println("Sending delay of $millis")
        val data = millis.toByteArray()
        Packet.Builder(Command.CMD_DELAY)
            .addData(data)
            .write()
    }

    /**
     * Waits for a given pin to reach a particular value before
     * continuing execution.
     *
     * This is useful for waiting until a GPIO device is in a particular
     * state before trying to send it more commands.
     *
     * eg. Waiting until the BUSY pin is set to 0, indicating that the
     * GPIO device has finished whatever it was doing, before trying to
     * make it do something else.
     *
     * @param pin Pin to wait on
     * @param value Value to wait for the pin to reach
     * @param millis Milliseconds to wait between pin reads
     * */
    suspend fun waitForPin(pin: Byte, value: Byte, millis: Short){
        println("Sending delay")
        val delayBytes = millis.toByteArray()
        Packet.Builder(Command.CMD_WAIT_FOR_PIN)
            .addData(pin)
            .addData(value)
            .addData(delayBytes)
            .write()
    }

    /**
     * Asks the Pico device to identify itself.
     *
     * The first byte returned by the Pico is the length of the name.
     * The remaining bytes are the encoded name.
     *
     * Command introduced in API version 2.
     *
     * @return Name of the device running the PGN daemon
     * */
    suspend fun getName(): String {
        println("Getting device name")
        val length = Packet.Builder(Command.CMD_GET_NAME)
            .readSingle()
            .toInt()
        val nameBytes = read(length)
        return nameBytes.decodeToString()
    }

    /**
     * Asks the Pico device which version of pico-gpio-net it is running.
     *
     * The API version is used to identify which commands the Pico is
     * able to understand and respond to.
     *
     * Command introduced in API version 2.
     *
     * Note that although it was introduced in version 2, this command
     * "accidentally" works in version 1 as well, since the default
     * response for an unknown command is [1].
     *
     * @return API version of the PGN daemon running on the target device
     * */
    suspend fun getApiVersion(): Byte =
        Packet.Builder(Command.CMD_GET_API_VERSION)
            .readSingle()



    /**
     * Convenience function for compiling a Packet.Builder object
     * to a Packet object and writing it to the socket in one go.
     * */
    private suspend fun Packet.Builder.write(){
        val packet = this.build()
        write(packet)
    }

    /**
     * Convenience function for compiling a Packet.Builder object
     * to a Packet object, writing it to the socket, and reading
     * the response from the server in one go.
     *
     * @param length Number of bytes expected to be received from the
     * server in response to the packet being sent.
     *
     * @return Response from the server. Should be exactly `length`
     * bytes in length.
     * */
    private suspend fun Packet.Builder.read(length: Int): ByteArray {
        val packet = this.build()
        return read(packet, length)
    }

    /**
     * Convenience function for compiling a Packet.Builder object
     * to a Packet object, writing it to the socket, and reading
     * the response from the server in one go.
     *
     * The response is assumed to be a single byte in length.
     *
     * @return Response from the server
     * */
    private suspend fun Packet.Builder.readSingle(): Byte {
        return read(1)[0]
    }

    companion object{

        /**
         * Converts an Int to an array of bytes so that it can be
         * sent over the TCP socket.
         *
         * @return Byte array representation of the given Int
         * */
        fun Int.toByteArray(): ByteArray =
            byteArrayOf(
                (this shr 24).toByte(),
                (this shr 16).toByte(),
                (this shr 8).toByte(),
                (this shr 0).toByte()
            )

        /**
         * Converts a Short to an array of bytes so that it can be
         * sent over the TCP socket.
         *
         * @return Byte array representation of the given Short
         * */
        fun Short.toByteArray(): ByteArray =
            byteArrayOf(
                this.highByte,
                this.lowByte
            )

        /**
         * Converts an array of bytes which was received from the TCP
         * socket to an Int.
         *
         * Expects the byte array to be 4 bytes in length.
         *
         * @return Int value represented by the byte array
         * */
        fun ByteArray.toInt(): Int =
            (this[0].toInt() shl 24) or
                    (this[1].toInt() and 0xff shl 16) or
                    (this[2].toInt() and 0xff shl 8) or
                    (this[3].toInt() and 0xff)

        /**
         * Converts an array of bytes which was received from the TCP
         * socket to a Short.
         *
         * Note that the return type is actually Int, but the value
         * of the Int will be in the range of a Short.
         *
         * Expects the byte array to be 2 bytes in length.
         *
         * @return Int value represented by the byte array
         * */
        fun ByteArray.toShort(): Int =
            (this[0].toInt() and 0xff shl 8) or
                    (this[1].toInt() and 0xff)

        /**
         * Converts an array of bytes which was received from the TCP
         * socket to an Int.
         *
         * Extracts the first 1, 2, or 4 bytes from the ByteArray and converts
         * them into a number.
         *
         * If 1 byte in length, it is parsed as a byte.
         *
         * If 2 bytes in length, it is parsed as a short.
         *
         * If 4 bytes in length, it is parsed as an int.
         *
         * @param Number of bytes to be taken from the byte array and
         * converted into a number
         *
         * @return Int value represented by the byte array
         * */
        fun ByteArray.toInt(numberOfBytes: Int): Int =
            when(numberOfBytes){
                4 -> toInt()
                2 -> toShort()
                1 -> this[0].toInt()
                else -> throw NumberFormatException("Byte array must be 1, 2, or 4 bytes in length")
            }

    }

}