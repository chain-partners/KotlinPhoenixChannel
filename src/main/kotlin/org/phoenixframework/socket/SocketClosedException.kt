package org.phoenixframework.socket

data class SocketClosedException(override val message: String): Exception()