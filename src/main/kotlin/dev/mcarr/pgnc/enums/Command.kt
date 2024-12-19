package dev.mcarr.pgnc.enums

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