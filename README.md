# pico-gpio-net-client

## What is it?

PGNC (pico-gpio-net-client) is a Kotlin library for interacting with a [pico-gpio-net daemon](https://github.com/mcarr823/pico-gpio-net) running on a Raspberry Pi Pico.

It abstracts away the nitty-gritty of communicating with the daemon, so you can utilize human-readable client commands instead of manipulating raw byte data.


## Example usage

```kotlin
val rpiPicoIpAddress = "192.168.1.150"
val client = PicoGpioNetClient(
    ip = rpiPicoIpAddress,
    port = 8080,
    autoFlush = false
)
client.connect()
val deviceName = client.getName()
client.close()
```

The above example connects to a Raspberry Pi Pico device on IP address 192.168.1.150, port 8080, running the pico-gpio-net daemon.

It then asks the device to name itself, stores the value in the `deviceName` variable, then closes the socket connection.

Under-the-hood, the client library is doing the following:
- opens a socket connection
- sends a command (raw byte data) asking for the device's name
- waits to read the length header of the response from the socket
- waits to read that number of bytes from the socket
- converts the response into a String

## How do I use it?

This library has not yet been published anywhere publicly-accessible.

As such, it currently requires you to open this project on your own PC and publish the library to your own maven server.

eg. To publish the repository onto your own PC, you would run:

`gradlew publishToMavenLocal`

After that, you can import the library into a different project by adding mavenLocal to your repositories list, and pgnc to your dependencies.

eg. In build.gradle.kts:

```Kotlin
repositories {
    mavenLocal()
}
dependencies {
    implementation("dev.mcarr:pgnc:0.0.1")
}
```