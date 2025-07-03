package dev.mcarr.pgnc

import dev.mcarr.pgnc.PicoGpioNetClient.Companion.toByteArray
import dev.mcarr.pgnc.PicoGpioNetClient.Companion.toInt
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the PicoGpioNetClient class.
 *
 * These tests can be run with either a real Pico
 * device running the PGN daemon, or a mock
 * implementation using the MockServer class.
 *
 * You should change the variables ip, port,
 * and useRealDevice to suit your use case.
 *
 * @see PicoGpioNetClient
 * */
class PicoGpioNetClientTest {

    /**
     * IP address of the Pico device which is running the PGN
     * daemon.
     *
     * TODO replace hard-coded string with a gradle argument
     * */
    private val ip = "127.0.0.1"

    /**
     * Port on which the PGN daemon is running.
     *
     * TODO replace hard-coded int with a gradle argument
     * */
    private val port = 8080

    /**
     * Specifies whether the tests should be run on a real
     * Pico device or not.
     *
     * If true, the tests will assume that a real device is
     * available on the given ip and port.
     *
     * If false, the MockServer class is used to provide
     * a virtual endpoint instead.
     *
     * @see MockServer
     * */
    private val useRealDevice = false

    /**
     * Establishes a connection to the PGN daemon and handles
     * the connection/disconnection of the socket.
     *
     * Also handles the setup of coroutines and the necessary
     * context switching.
     *
     * This function abstracts away the logic which is common
     * to each of this class's tests, so that the individual
     * tests can focus on the thing that they're testing rather
     * than the socket connection and setup.
     *
     * @param callback Callback which is invoked after a
     * connection to the PGN daemon has been established.
     * */
    private fun runSocketTest(
        callback: suspend (PicoGpioNetClient) -> Unit
    )=
        runTest {

            // Setup a mock server on another thread, if requires
            val serverThread = launch(Dispatchers.Default.limitedParallelism(1)){
                if (!useRealDevice) {
                    val server = MockServer(ip, port, name = "Test server", maxSizeKb = 32)
                    server.runDaemon()
                }
            }

            // Create the client socket class
            val socket = PicoGpioNetClient(ip, port)

            // Keep trying to connect to the server in a loop.
            // The port might not have been freed and reclaimed yet by the time
            // this code runs after another test has completed, so we need to
            // wait for it.
            // There's probably a cleaner way to do this, but I can't find any
            // which are cross-platform, so a loop will have to do for now.
            var i = 0
            while(i++ < 1000) {
                try {
                    socket.connect()
                    break
                } catch (e: Exception) {
                    delay(100)
                    // Likely a connection exception.
                    // The exact connection type varies depending on the platform though,
                    // so we can't be more specific inside of a common test.
                }
            }

            withContext(Dispatchers.Default.limitedParallelism(1)) {

                // Open the socket connection, run the test via the callback, then close
                // automatically when the use block ends.
                socket.use{
                    callback(it)
                }

                // Close the mock server, if required
                serverThread.cancel()

            }
        }

    /**
     * Tests the `getName` function by querying the PGN daemon
     * for its name, then checking if the name returned was empty.
     * */
    @Test
    fun getNameTest() = runSocketTest { socket ->
        val name = socket.getName()
        println(name)
        assertTrue(name.isNotEmpty())
    }

    /**
     * Tests the `getApiVersion` function by querying the PGN daemon
     * for its version number, then checking if it's a valid version number.
     * */
    @Test
    fun getApiVersionTest() = runSocketTest { socket ->
        val apiVersion = socket.getApiVersion()
        println("API version: $apiVersion")
        assertTrue(apiVersion > 0)
    }

    /**
     * Tests the `waitForPin` function by issuing the command to the
     * PGN daemon and waiting for a response, then confirming that the
     * length and value of the response match our expectations.
     * */
    @Test
    fun waitForPinTest() = runSocketTest { socket ->
        socket.waitForPin(1, 0, 100)
        val result = socket.flush()
        assertEquals(1, result.size)
        assertTrue(result[0])
    }

    /**
     * Tests the `delay` function by issuing the command to the
     * PGN daemon and waiting for a response, then confirming that the
     * length and value of the response match our expectations.
     * */
    @Test
    fun delayTest() = runSocketTest { socket ->
        socket.delay(100)
        val result = socket.flush()
        assertEquals(1, result.size)
        assertTrue(result[0])
    }

    /**
     * Tests the `spiWrite` function by issuing the command to the
     * PGN daemon and waiting for a response, then confirming that the
     * length and value of the response match our expectations.
     * */
    @Test
    fun spiWriteTest() = runSocketTest { socket ->
        val data = byteArrayOf(0, 1, 2, 3, 4)
        socket.spiWrite(data)
        val result = socket.flush()
        assertEquals(1, result.size)
        assertTrue(result[0])
    }

    /**
     * Tests the `getPins` function by querying the PGN daemon
     * and checking if the length of the response matches the
     * number of pins we queried.
     *
     * We can't test the actual values of the pins, since we
     * don't know what those values are necessarily supposed
     * to be.
     * */
    @Test
    fun getPinsTest() = runSocketTest { socket ->
        val data = byteArrayOf(0, 1, 2, 3, 4)
        val result = socket.getPins(data)
        assertEquals(result.size, data.size)
    }

    /**
     * Tests the `getPin` function by querying the PGN daemon
     * and checking if the returned pin value is valid.
     * */
    @Test
    fun getPinTest() = runSocketTest { socket ->
        val result = socket.getPin(1)
        assertTrue(result >= 0)
    }

    /**
     * Tests the `setPins` function by issuing the command to the
     * PGN daemon and waiting for a response, then confirming that the
     * length and value of the response match our expectations.
     * */
    @Test
    fun setPinsTest() = runSocketTest { socket ->
        val pins = byteArrayOf(0, 1, 2, 3, 4)
        val values = byteArrayOf(1, 1, 1, 1, 1)
        val pinsAndValues = pins.mapIndexed { index, pin ->
            pin to values[index]
        }
        socket.setPins(pinsAndValues)
        val result = socket.flush()
        assertEquals(1, result.size)
        assertTrue(result[0])
    }

    /**
     * Tests the `setPin` function by issuing the command to the
     * PGN daemon and waiting for a response, then confirming that the
     * length and value of the response match our expectations.
     * */
    @Test
    fun setPinTest() = runSocketTest { socket ->
        socket.setPin(1, 0)
        val result = socket.flush()
        assertEquals(1, result.size)
        assertTrue(result[0])
    }

    /**
     * A more advanced test which checks both the
     * `setPins` and `getPins` functions to make sure
     * that they're actually setting and getting the
     * right values.
     *
     * The set command is issued to the daemon, after which
     * the get command is issued.
     *
     * The results of the get command are then compared to the
     * values sent by the set command.
     *
     * This is done twice, with different values each time,
     * to ensure that the first test didn't "accidentally" pass
     * due to the pins coincidentally being already set to the
     * expected values.
     * */
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
        assertEquals(result.size, pins.size)
        result.forEach {
            assertEquals(1, it.toInt())
        }

        values = byteArrayOf(0, 0, 0, 0, 0)
        pinsAndValues = pins.mapIndexed { index, pin ->
            pin to values[index]
        }
        socket.setPins(pinsAndValues)
        socket.flush()
        result = socket.getPins(pins)
        assertEquals(result.size, pins.size)
        result.forEach {
            assertEquals(0, it.toInt())
        }
    }

    /**
     * A more advanced test which checks both the
     * `setPin` and `getPin` functions to make sure
     * that they're actually setting and getting the
     * right value.
     *
     * The set command is issued to the daemon, after which
     * the get command is issued.
     *
     * The result of the get command is then compared to the
     * value sent by the set command.
     *
     * This is done twice, with a different value each time,
     * to ensure that the first test didn't "accidentally" pass
     * due to the pin coincidentally being already set to the
     * expected value.
     * */
    @Test
    fun setAndGetPinTest() = runSocketTest { socket ->
        socket.setPin(1, 0)
        socket.flush()
        var result = socket.getPin(1)
        assertEquals(0, result.toInt())

        socket.setPin(1, 1)
        socket.flush()
        result = socket.getPin(1)
        assertEquals(1, result.toInt())
    }

    /**
     * Test if converting bytes back and forth from ints works correctly.
     *
     * This was implemented to make sure that bit-shifting ints worked
     * the same as using a ByteBuffer.
     * (ByteBuffers aren't platform-agnostic, so we can't use those
     * anymore)
     * */
    @Test
    fun byteShiftTest() = runSocketTest{
        val payload = 260
        println("Int: $payload")
        val bytes = payload.toByteArray()
        println("Bytes: ${bytes.joinToString(",")}")
        val intVal = bytes.toInt()
        println("Int: $intVal")
    }

}