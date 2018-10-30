# KotlinPhoenixChannel
KotlinPhoenixChannel is inspired by [JavaPhoenixChannels](https://github.com/eoinsha/JavaPhoenixChannels). `JavaPhoenixChannels` has shortcomings from just re-writing Javascript library to Java and it causes issues such as thread-safe. As `JavaPhonexChannels` do, its primary purpose is to ease development of real time messaging apps for Kotlin using an Elixir/Phoenix backend.

# Setup
- Add `jitpack` in your root build.gradle at the end of repositories:
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```
- Add the dependency
```groovy
dependencies {
    implementation 'com.github.chain-partners:KotlinPhoenixChannel:0.1.15'
}
```

# Example
## Example Android App
For a full sample Android chat app, check out the repository at https://github.com/chain-partners/PhoenixChatAndroid

The quick examples below are used with the [Phoenix Chat Example](https://github.com/chrismccord/phoenix_chat_example)

## Socket Connection
```kotlin
import org.phoenixframework.channel.*
import org.phoenixframework.socket.*

val socket = Socket(endpointUri = "http://base.url")
val socketEventListener = object : PhoenixSocketEventListener {

  fun onOpen(socket: Socket) {
    createAndJoinChannel(socket)
  }

  fun onClosing(socket: Socket, code: Int?, reason: String?) {
    System.out.println("Socket Closing : ${code?.toString()} ${reason?.toString()}")
  }

  fun onClosed(socket: Socket, code: Int?, reason: String?) {
    System.out.println("Socket Closed : ${code?.toString()} ${reason?.toString()}")
  }

  fun onFailure(socket: Socket, t: Throwable?) {
    System.out.println("Socket Error : ${t?.message}")
  }

  fun onMessage(socket: Socket, text: String?) {
    System.out.println("Message : ${text}")
  }
}
socket.registerEventListener(socketEventListener)
socket.connect()
```

## Join Channel
Implements `createAndJoinChannel(socket: Socket)` on [Socket Connection](#socket-connection)
```kotlin
fun createAndJoinChannel(socket: Socket) {
  with(socket.createChannel(topic = "rooms:lobby")) {
    join(payload = null,
        success = { message: Message? ->
          pushMessage(channel)
          getEvents(channel)
        },
        failure = { message: Message?, throwable: Throwable? ->
          System.out.println("Join Error: ${message?.toString()} ${throwable?.message}")
        })
  }
}
```
You can register `PhoenixChannelStateListener` to channel to detect `ChannelState` changes.

### Push Messages
Implements `pushMessage(channel: Channel)` on [Join Channel](#join-channel)
```kotlin
fun pushMessage(channel: Channel) {
  val payload = """
    {
      'user': 'my_username',
      'body': 'Hello, world!'
    }
  """.trimIndent()
  channel.pushRequest(
      event = "new:msg",
      payload = payload,
      success = { message: Message? ->
        System.out.println("NEW MESSAGE: " + message.toString())
      },
      failure = { message: Message?, throwable: Throwable? ->
        System.out.println("ERROR: " + message.toString())
      })
}
```

### Get Event Notifications
Implements `getEvents(channel: Channel)` on [Join Channel](#join-channel)
```kotlin
fun getEvents(channel: Channel) {
  channel.on(
      event = "new:msg",
      success = {
        System.out.println("NEW MESSAGE: " + message.toString())
      })
}
```

# Who's using KotlinPhoenixChannel
- [DAYBIT](https://daybit.com/)
> Are you using KotlinPhoenixChannel? Please [let me know](mailto:leechhe90+kotlinphoenixlib@gmail.com)!

# License
KotlinPhoenixChannel is under MIT license. See the [LICENSE](https://github.com/chain-partners/KotlinPhoenixChannel/blob/master/LICENSE) for more info.