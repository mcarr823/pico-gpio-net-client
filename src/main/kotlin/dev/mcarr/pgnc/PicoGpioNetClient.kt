package dev.mcarr.pgnc

import dev.mcarr.pgnc.classes.Packet
import dev.mcarr.pgnc.enums.Command
import dev.mcarr.pgnc.socket.KtorSocketClient
import java.io.Closeable
import java.nio.ByteBuffer

class PicoGpioNetClient(
    private val ip: String,
    private val port: Int,
    private val autoFlush: Boolean = false
) : Closeable {

    private val sock = KtorSocketClient()
    private val queue = ArrayList<Packet>()

    suspend fun connect(){
        sock.connect(ip, port)
    }

    override fun close(){
        sock.close()
    }

    suspend fun flush(): List<Boolean> {

        if (queue.isEmpty()) return listOf()

        println("Flushing: ${queue.size}")

        // Send all of the data at once
        queue.forEach {
            sock.write(it.data)
        }

        sock.flush()

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
     *  @param cmd Array of bytes to send
     * */
    suspend fun write(cmd: Packet){
        queue.add(cmd)
        println("Queue count: ${queue.size}")
        if (autoFlush) flush()
    }

    /**
     * Performs a read request after flushing the queue, if needed.
     *
     * A read request is one which expects a meaningful response.
     * For example, reading data from SPI, or reading a pin's state.
     *
     * @param cmd Array of bytes to send
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
     * `pin` The number of the pin to change the state of
     * `value` New value to set for the pin
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
     * `pinsAndValues` Array of pin:value pairs.
     * eg. [ [16,1], [18,0] ]
     * would set pin 16 to value 1, and pin 18 to value 0.
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
     * `bytedata` Array of bytes to write to the SPI device
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
     * */
    suspend fun getName(): String {
        println("Getting device name")
        val length = Packet.Builder(Command.CMD_GET_NAME)
            .readSingle()
            .toInt()
        val nameBytes = read(length)
        return nameBytes.toString(Charsets.UTF_8)
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
     * */
    suspend fun getApiVersion(): Byte =
        Packet.Builder(Command.CMD_GET_API_VERSION)
            .readSingle()



    private fun Int.toByteArray(): ByteArray =
        ByteBuffer.allocate(4)
            .putInt(this)
            .array()

    private fun Short.toByteArray(): ByteArray =
        ByteBuffer.allocate(2)
            .putShort(this)
            .array()

    private fun ByteArray.toInt(): Int =
        ByteBuffer.wrap(this).int

    private suspend fun Packet.Builder.write(){
        val packet = this.build()
        write(packet)
    }

    private suspend fun Packet.Builder.read(length: Int): ByteArray {
        val packet = this.build()
        return read(packet, length)
    }

    private suspend fun Packet.Builder.readSingle(): Byte {
        return read(1)[0]
    }

}