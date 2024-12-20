package dev.mcarr.pgnc.enums

/**
 * Represents a command which the PGN daemon knows how to interpret
 * and respond to.
 *
 * Every packet of data sent to the PGN daemon must start with one
 * of these commands.
 *
 * @param value The raw byte value which gets sent via the socket
 * to the PGN daemon.
 * @param apiVersion The API version in which the given command
 * was introduced. ie. The PGN daemon must be that version or higher
 * in order to understand the given command.
 *
 * @see dev.mcarr.pgnc.classes.Packet
 * */
enum class Command(
    val value: Byte,
    val apiVersion: Int
) {
    CMD_SET_PIN_SINGLE(value = 0, apiVersion = 1),
    CMD_SET_PIN_MULTI(value = 1, apiVersion = 1),
    CMD_WRITE_BYTES(value = 2, apiVersion = 1),
    CMD_GET_PIN_SINGLE(value = 3, apiVersion = 1),
    CMD_GET_PIN_MULTI(value = 4, apiVersion = 1),
    CMD_DELAY(value = 5, apiVersion = 1),
    CMD_WAIT_FOR_PIN(value = 6, apiVersion = 1),
    CMD_GET_NAME(value = 7, apiVersion = 2),
    CMD_GET_API_VERSION(value = 8, apiVersion = 1)
}