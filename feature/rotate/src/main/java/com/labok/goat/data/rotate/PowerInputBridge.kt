package com.labok.goat.data.rotate

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64

/**
 * A private, authenticated loopback stream. Shizuku only launches the detached reader;
 * its Binder and stream-transfer threads are not involved after the connection starts.
 * Closing the socket (including app death) makes the helper terminate its getevent child.
 */
internal class PowerInputBridge(private val token: String = newToken()) : Closeable {
    private val lock = Any()
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    @Volatile private var closed = false
    private var client: Socket? = null
    val port: Int get() = server.localPort

    /** No user text is interpolated; the random token is restricted to shell-safe characters. */
    fun launchCommand(): String {
        require(token.matches(Regex("[A-Za-z0-9_-]{16,128}")))
        val script = """
            cleanup() {
                trap - EXIT HUP INT TERM
                kill -TERM "-${'$'}${'$'}" 2>/dev/null
            }
            trap cleanup EXIT
            trap 'exit 0' HUP INT TERM
            (printf '%s\n' '$token'; exec getevent -lt 2>&1) |
                toybox nc -4 -n -w 5 -q 1 127.0.0.1 $port > /dev/null &
            transport_pid=${'$'}!
            while kill -0 "${'$'}transport_pid" 2>/dev/null; do sleep 1; done
        """.trimIndent()
        return "setsid sh -c " + shellQuote(script) + " </dev/null >/dev/null 2>&1 &"
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    /** Accepts only the helper carrying this session's random token, with bounded input. */
    fun read(onConnected: () -> Unit, onLine: (String) -> Unit) {
        val deadline = System.nanoTime() + START_TIMEOUT_MS * 1_000_000
        while (!closed) {
            val remaining = ((deadline - System.nanoTime()) / 1_000_000).toInt()
            if (remaining <= 0) throw SocketTimeoutException("Power input helper did not connect")
            server.soTimeout = remaining
            val candidate = server.accept()
            synchronized(lock) {
                if (closed) {
                    candidate.close()
                    throw IOException("Power input bridge closed")
                }
                client = candidate
            }
            candidate.use { socket ->
                socket.soTimeout = minOf(remaining, AUTH_TIMEOUT_MS)
                val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val authenticated = try { line(input, 128) == token } catch (_: IOException) { false }
                if (!authenticated) return@use
                // One authenticated session only; no local application can attach afterwards.
                server.close()
                socket.soTimeout = 0
                onConnected()
                while (!closed) {
                    val next = line(input, MAX_LINE) ?: return
                    onLine(next)
                }
                return
            }
            synchronized(lock) { if (client === candidate) client = null }
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            runCatching { client?.close() }
            client = null
            runCatching { server.close() }
        }
    }

    private fun line(input: BufferedReader, limit: Int): String? {
        val value = StringBuilder()
        while (true) {
            val char = input.read()
            if (char == -1) return if (value.isEmpty()) null else value.toString()
            if (char == '\n'.code) return value.toString().removeSuffix("\r")
            if (value.length == limit) throw IOException("Power input line exceeds limit")
            value.append(char.toChar())
        }
    }

    companion object {
        private const val START_TIMEOUT_MS = 10_000L
        private const val AUTH_TIMEOUT_MS = 2000
        private const val MAX_LINE = 1024
        private fun newToken(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    }
}
