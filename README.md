# pico-gpio-net-client

## What is it?

PGNC (pico-gpio-net-client) is a Kotlin library for interacting with a [pico-gpio-net (PGN) daemon](https://github.com/mcarr823/pico-gpio-net) running on a Raspberry Pi Pico.

It abstracts away the nitty-gritty of communicating with PGN, so you can utilize human-readable client commands instead of manipulating raw byte data.


## Supported platforms

PGNC supports all KMM platforms except for web.

| Platform | Supported |
|----------|-----------|
| JVM      | &check;   |
| Android  | &check;   |
| Native   | &check;   |
| iOS      | &check;   |
| Web      | &cross;   |
| tvOS     | &check;   |
| watchOS  | &check;   |

* Note that although all platforms (except web) are supported and _should_ work, only JVM and Native Linux have actually been tested.

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

The above example connects to the PGN daemon running on a Raspberry Pi Pico device on IP address 192.168.1.150, port 8080.

It then asks the device to name itself, stores the value in the `deviceName` variable, then closes the socket connection.

Under-the-hood, the client library is doing the following:
- opens a socket connection
- sends a command (raw byte data) asking for the device's name
- waits to read the length header of the response from the socket
- waits to read that number of bytes from the socket
- converts the response into a String

## Setup

The setup instructions below assume that you're building a gradle project, with a TOML file for dependency management and KTS files for gradle scripts.

The instructions should still work for other setups with minor changes.

1. Add the library definition and version to your TOML file (if you use one):

```toml
# libs.versions.toml

[versions]
pgnc = "1.0.0"

[libraries]
pgnc-library-core = { module = "dev.mcarr.pgnc:library", version.ref = "pgnc" }
```

2. Add the dependency to your app's build.gradle.kts file:

```Kotlin
// app (not root) build.gradle.kts

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.d253.library.core)
            }
        }
    }
}
```

## API Documentation

Javadoc can be found [here](https://mcarr823.github.io/pico-gpio-net-client/).
