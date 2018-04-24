# KotlinPhoenixChannel
KotlinPhoenixChannel is inspired by [JavaPhoenixChannels](https://github.com/eoinsha/JavaPhoenixChannels). `JavaPhoenixChannels` has shortcomings from just re-writing Javascript library to Java and it makes issues such as thread-safe.

## Setup
TBD

## Example
```kotlin
import org.phoenixframework.channel.*
import org.phoenixframework.socket.*
  
class MainActivity : Activity(), PhoenixSocketEventListener {
  
    val url = "http://sample.com/socket/websocket"
    val topic = "sample:topic"
    val socket: Socket = Socket(url)
    val channel: Channel
     
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ...
        
        socket.registerEventListener(this)
        socket.connect()
   }
   
   override fun onDestroy() {
        socket.unregisterEventListener(this) 
        super.onDestroy()
   }
   
   /**
    *  Implements [PhoenixSocketEventListener]. 
    */
   override fun onOpen() {
      channel = socket.channel(topic)
      channel.join(payload = "{ 'sample': 'payload' }") { response ->
        ...
      }
   }
   
   override fun onClosed(code: Int?, reason: String?) {}
   override fun onClosing(code: Int?, reason: String?) {}
   override fun onFailure(t: Throwable?) {}
   override fun onMessage(text: String?) {
   
   }
}

```
