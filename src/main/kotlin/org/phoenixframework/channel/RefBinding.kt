package org.phoenixframework.channel

import org.phoenixframework.MessageCallback

data class RefBinding(val ref: String, val callback: MessageCallback)