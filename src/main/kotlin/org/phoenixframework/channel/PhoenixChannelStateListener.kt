package org.phoenixframework.channel

/**
 * Each callbacks corresponds to [ChannelState].
 */
interface PhoenixChannelStateListener {

  fun onJoined(channel: Channel)

  fun onJoining(channel: Channel)

  fun onError(channel: Channel, throwable: Throwable?)

  fun onClosed(channel: Channel)

  fun onClosing(channel: Channel)
}