package dev.mcarr.pgnc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test

class PicoGpioNetClientTest {

    private val ip = "127.0.0.1"
    private val port = 8080

    private fun runSocketTest(
        callback: suspend (PicoGpioNetClient) -> Unit
    ){
        runTest {
            val socket = PicoGpioNetClient(ip, port)
            socket.connect()
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                socket.use{
                    callback(it)
                }
            }
        }
    }

    @Test
    fun getNameTest() = runSocketTest { socket ->
        val name = socket.getName()
        println(name)
        assert(name.isNotEmpty())
    }

    @Test
    fun getApiVersionTest() = runSocketTest { socket ->
        val apiVersion = socket.getApiVersion()
        println("API version: $apiVersion")
        assert(apiVersion > 0)
    }

    @Test
    fun waitForPinTest() = runSocketTest { socket ->
        socket.waitForPin(1, 0, 100)
        val result = socket.flush()
        assert(result.size == 1)
        assert(result[0])
    }

    @Test
    fun delayTest() = runSocketTest { socket ->
        socket.delay(100)
        val result = socket.flush()
        assert(result.size == 1)
        assert(result[0])
    }

    @Test
    fun spiWriteTest() = runSocketTest { socket ->
        val data = byteArrayOf(0, 1, 2, 3, 4)
        socket.spiWrite(data)
        val result = socket.flush()
        assert(result.size == 1)
        assert(result[0])
    }

    @Test
    fun getPinsTest() = runSocketTest { socket ->
        val data = byteArrayOf(0, 1, 2, 3, 4)
        val result = socket.getPins(data)
        assert(result.size == data.size)
    }

    @Test
    fun getPinTest() = runSocketTest { socket ->
        val result = socket.getPin(1)
        assert(result >= 0)
    }

    @Test
    fun setPinsTest() = runSocketTest { socket ->
        val pins = byteArrayOf(0, 1, 2, 3, 4)
        val values = byteArrayOf(1, 1, 1, 1, 1)
        val pinsAndValues = pins.mapIndexed { index, pin ->
            pin to values[index]
        }
        socket.setPins(pinsAndValues)
        val result = socket.flush()
        assert(result.size == 1)
        assert(result[0])
    }

    @Test
    fun setPinTest() = runSocketTest { socket ->
        socket.setPin(1, 0)
        val result = socket.flush()
        assert(result.size == 1)
        assert(result[0])
    }

    @Test
    fun setAndGetPinsTest() = runSocketTest { socket ->
        val pins = byteArrayOf(0, 1, 2, 3, 4)
        var values = byteArrayOf(1, 1, 1, 1, 1)
        var pinsAndValues = pins.mapIndexed { index, pin ->
            pin to values[index]
        }
        socket.setPins(pinsAndValues)
        socket.flush()
        var result = socket.getPins(pins)
        assert(result.size == pins.size)
        result.forEach {
            assert(it.toInt() == 1)
        }

        values = byteArrayOf(0, 0, 0, 0, 0)
        pinsAndValues = pins.mapIndexed { index, pin ->
            pin to values[index]
        }
        socket.setPins(pinsAndValues)
        socket.flush()
        result = socket.getPins(pins)
        assert(result.size == pins.size)
        result.forEach {
            assert(it.toInt() == 0)
        }
    }

    @Test
    fun setAndGetPinTest() = runSocketTest { socket ->
        socket.setPin(1, 0)
        socket.flush()
        var result = socket.getPin(1)
        assert(result.toInt() == 0)

        socket.setPin(1, 1)
        socket.flush()
        result = socket.getPin(1)
        assert(result.toInt() == 1)
    }



}