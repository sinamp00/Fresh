package org.fresh.instagram.network

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * SocketFactory that wraps created TCP sockets with a fragmenting output stream.
 * Specifically splits the initial TLS ClientHello handshake packet to bypass SNI-based Deep Packet Inspection (DPI).
 */
class FragmentingSocketFactory(
    private val defaultFactory: SocketFactory = SocketFactory.getDefault(),
    private val splitChunkSize: Int = 24,
    private val delayMs: Long = 6L
) : SocketFactory() {

    override fun createSocket(): Socket {
        return FragmentingSocket(defaultFactory.createSocket(), splitChunkSize, delayMs)
    }

    override fun createSocket(host: String?, port: Int): Socket {
        val socket = defaultFactory.createSocket(host, port)
        return FragmentingSocket(socket, splitChunkSize, delayMs)
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        val socket = defaultFactory.createSocket(host, port, localHost, localPort)
        return FragmentingSocket(socket, splitChunkSize, delayMs)
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        val socket = defaultFactory.createSocket(host, port)
        return FragmentingSocket(socket, splitChunkSize, delayMs)
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        val socket = defaultFactory.createSocket(address, port, localAddress, localPort)
        return FragmentingSocket(socket, splitChunkSize, delayMs)
    }
}

class FragmentingSocket(
    private val delegate: Socket,
    private val splitChunkSize: Int = 24,
    private val delayMs: Long = 6L
) : Socket() {
    private var wrappedOutputStream: OutputStream? = null

    override fun connect(endpoint: SocketAddress?) {
        delegate.connect(endpoint)
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        delegate.connect(endpoint, timeout)
    }

    override fun bind(bindpoint: SocketAddress?) = delegate.bind(bindpoint)
    override fun getInetAddress(): InetAddress? = delegate.inetAddress
    override fun getLocalAddress(): InetAddress = delegate.localAddress
    override fun getPort(): Int = delegate.port
    override fun getLocalPort(): Int = delegate.localPort
    override fun getRemoteSocketAddress(): SocketAddress? = delegate.remoteSocketAddress
    override fun getLocalSocketAddress(): SocketAddress? = delegate.localSocketAddress
    override fun getChannel() = delegate.channel
    override fun getInputStream(): InputStream = delegate.inputStream

    override fun getOutputStream(): OutputStream {
        if (wrappedOutputStream == null) {
            wrappedOutputStream = FragmentingOutputStream(delegate.outputStream, splitChunkSize, delayMs)
        }
        return wrappedOutputStream!!
    }

    override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
    override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
    override fun setSoLinger(on: Boolean, linger: Int) { delegate.setSoLinger(on, linger) }
    override fun getSoLinger(): Int = delegate.soLinger
    override fun sendUrgentData(data: Int) = delegate.sendUrgentData(data)
    override fun setOOBInline(on: Boolean) { delegate.oobInline = on }
    override fun getOOBInline(): Boolean = delegate.oobInline
    override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
    override fun getSoTimeout(): Int = delegate.soTimeout
    override fun setSendBufferSize(size: Int) { delegate.sendBufferSize = size }
    override fun getSendBufferSize(): Int = delegate.sendBufferSize
    override fun setReceiveBufferSize(size: Int) { delegate.receiveBufferSize = size }
    override fun getReceiveBufferSize(): Int = delegate.receiveBufferSize
    override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
    override fun getKeepAlive(): Boolean = delegate.keepAlive
    override fun setTrafficClass(tc: Int) { delegate.trafficClass = tc }
    override fun getTrafficClass(): Int = delegate.trafficClass
    override fun setReuseAddress(on: Boolean) { delegate.reuseAddress = on }
    override fun getReuseAddress(): Boolean = delegate.reuseAddress
    override fun close() = delegate.close()
    override fun shutdownInput() = delegate.shutdownInput()
    override fun shutdownOutput() = delegate.shutdownOutput()
    override fun isConnected(): Boolean = delegate.isConnected
    override fun isBound(): Boolean = delegate.isBound
    override fun isClosed(): Boolean = delegate.isClosed
    override fun isInputShutdown(): Boolean = delegate.isInputShutdown
    override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown
}

class FragmentingOutputStream(
    private val delegate: OutputStream,
    private val splitChunkSize: Int = 24,
    private val delayMs: Long = 6L
) : OutputStream() {
    private var hasFragmented = false

    override fun write(b: Int) {
        delegate.write(b)
    }

    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        // Detect TLS Handshake: Record Type 0x16 (Handshake), Version 0x03 0x01/02/03
        if (!hasFragmented && len > splitChunkSize && b.size >= off + 3 && b[off] == 0x16.toByte() && b[off + 1] == 0x03.toByte()) {
            hasFragmented = true
            // Write first small fragment (e.g. 24 bytes) to split the SNI extension across packets
            delegate.write(b, off, splitChunkSize)
            delegate.flush()
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs)
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            // Write remainder of the handshake
            delegate.write(b, off + splitChunkSize, len - splitChunkSize)
            delegate.flush()
        } else {
            delegate.write(b, off, len)
        }
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}
