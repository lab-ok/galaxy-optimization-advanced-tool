package com.labok.goat.data.rotate

import java.io.IOException
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PowerInputBridgeTest {
    private val token = "test_session_0123456789abcdef"

    @Test fun refusesToLaunchWithAnInvalidOrMissingDevicePath() {
        PowerInputBridge(token).use { bridge ->
            try {
                bridge.launchCommand(emptyList())
                fail("Missing devices must not launch an unrestricted reader")
            } catch (_: IllegalArgumentException) { }
            for (path in listOf("", "/dev/input", "/dev/input/event0 /dev/input/event1", "/dev/input/event0;reboot")) {
                try {
                    bridge.launchCommand(listOf(path))
                    fail("Invalid device path must not launch a reader: $path")
                } catch (_: IllegalArgumentException) {
                    // Never allow an unrestricted reader or shell metacharacters.
                }
            }
        }
    }

    @Test fun rejectsAnotherLocalClientAndOnlyDeliversAuthenticatedEvents() {
        val bridge = PowerInputBridge(token)
        val executor = Executors.newSingleThreadExecutor()
        val connected = CountDownLatch(1)
        val events = mutableListOf<String>()
        try {
            val reader = executor.submit { bridge.read({ connected.countDown() }, events::add) }
            Socket("127.0.0.1", bridge.port).use {
                it.getOutputStream().write("wrong-token\nforged event\n".toByteArray())
            }
            Socket("127.0.0.1", bridge.port).use {
                it.getOutputStream().write("$token\nreal power event\n".toByteArray())
                it.shutdownOutput()
                reader.get(3, TimeUnit.SECONDS)
            }
            assertEquals(0L, connected.count)
            assertEquals(listOf("real power event"), events)
        } finally {
            bridge.close()
            executor.shutdownNow()
        }
    }

    @Test fun rejectsOverlongInputInsteadOfAllocatingAnUnboundedLine() {
        val bridge = PowerInputBridge(token)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val reader = executor.submit { bridge.read({}, {}) }
            Socket("127.0.0.1", bridge.port).use {
                it.getOutputStream().write((token + "\n" + "x".repeat(1025) + "\n").toByteArray())
                try {
                    reader.get(3, TimeUnit.SECONDS)
                    fail("Overlong input must terminate the stream")
                } catch (expected: ExecutionException) {
                    assertTrue(expected.cause is IOException)
                }
            }
        } finally {
            bridge.close()
            executor.shutdownNow()
        }
    }

    @Test fun closingAnEstablishedSessionUnblocksTheReader() {
        val bridge = PowerInputBridge(token)
        val executor = Executors.newSingleThreadExecutor()
        val connected = CountDownLatch(1)
        try {
            val reader = executor.submit { runCatching { bridge.read({ connected.countDown() }, {}) } }
            Socket("127.0.0.1", bridge.port).use {
                it.getOutputStream().write("$token\n".toByteArray())
                assertTrue(connected.await(3, TimeUnit.SECONDS))
                bridge.close()
                reader.get(3, TimeUnit.SECONDS)
                assertEquals(-1, it.getInputStream().read())
            }
        } finally {
            bridge.close()
            executor.shutdownNow()
        }
    }

    @Test fun closingBeforeAuthenticationUnblocksAccept() {
        val bridge = PowerInputBridge(token)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val reader = executor.submit { runCatching { bridge.read({}, {}) } }
            bridge.close()
            reader.get(3, TimeUnit.SECONDS)
        } finally {
            bridge.close()
            executor.shutdownNow()
        }
    }
}
