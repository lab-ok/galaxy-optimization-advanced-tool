package com.labok.goat.data.rotate

/** Finds only devices advertising KEY_POWER in a `getevent -pl` capability listing. */
internal object PowerInputDevice {
    private val device = Regex("add device \\d+: (/dev/input/event\\d+)")
    private val eventType = Regex("^([A-Z_]+)\\s+\\(([0-9a-fA-F]{4})\\):")
    private val powerKey = Regex("(?:^|\\s)KEY_POWER(?:\\s|$)")

    fun findAll(capabilities: String): List<String> {
        val devices = linkedSetOf<String>()
        var path: String? = null
        var keys = false
        for (raw in capabilities.lineSequence()) {
            val line = raw.trim()
            val type = eventType.find(line)
            when {
                line.startsWith("add device ") -> {
                    path = device.matchEntire(line)?.groupValues?.get(1)
                    keys = false
                }
                type != null -> keys = type.groupValues[1] == "KEY" && type.groupValues[2] == "0001"
                line == "input props:" -> keys = false
            }
            if (keys && path != null && powerKey.containsMatchIn(line.substringAfter(':'))) devices.add(path)
        }
        return devices.toList()
    }
}
