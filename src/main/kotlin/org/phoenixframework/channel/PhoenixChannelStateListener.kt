package org.phoenixframework.channel

/**
 * Each callbacks corresponds to [ChannelState].
 */
interface PhoenixChannelStateListener {

  fun onJoined()

  fun onJoining()

  fun onError(throwable: Throwable?)

  fun onClosed()
}