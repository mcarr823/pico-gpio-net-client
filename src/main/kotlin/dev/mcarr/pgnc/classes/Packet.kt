package dev.mcarr.pgnc.classes

import dev.mcarr.pgnc.enums.Command

/**
 * Represents a packet of data which can be sent to/from the
 * daemon on the Pico device.
 *
 * Packets should be constructed by using the inner Builder
 * class.
 *
 * @param data Raw byte data encapsulated by the packet
 *
 * @see Builder
 * */
class Packet(
    val data: ByteArray
) {

    /**
     * Builder class for Packet.
     *
     * Each packet sent to the Pico device is expected to follow
     * a specific format, starting with a single byte for the command
     * and ending with an array of byte data.
     *
     * This builder class enforces that format and provides simple
     * functions for appending the necessary data to the request.
     *
     * For example:
     * ```
     * val packet = Packet.Builder(Command.CMD_SET_PIN_SINGLE)
     *             .addData(pin)
     *             .addData(value)
     *             .build()
     * ```
     *
     * The above request sets the command to SET_PIN_SINGLE, appends
     * the pin, appends the value to set for that pin, then compiles
     * the data into a packet.
     * */
    class Builder(
        private val command: Command
    ) {

        /**
         * List of ByteArray objects for temporarily storing byte data
         * prior to compilation.
         *
         * Note that an arraylist of byte arrays is used in order to avoid
         * modifying a single byte array over and over again, since a byte
         * array is a fixed length.
         * */
        private val data = ArrayList<ByteArray>()

        /**
         * Appends 
         * */
        fun addData(byte: Byte): Builder {
            addData(byteArrayOf(byte))
            return this
        }

        fun addData(bytes: ByteArray): Builder {
            data.add(bytes)
            return this
        }

        fun build(): Packet {

            val commandByte = command.value.toByte()

            // +1 because of commandByte
            var offset = 1
            val totalLength = 1 + data.sumOf { it.size }

            val returnData = ByteArray(totalLength)
            returnData.set(0, commandByte)

            data.forEach { bytes ->
                bytes.copyInto(returnData, offset)
                offset += bytes.size
            }

            return Packet(returnData)
        }

    }

}