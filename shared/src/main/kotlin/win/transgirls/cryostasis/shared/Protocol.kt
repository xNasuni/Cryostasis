package win.transgirls.cryostasis.shared

object Protocol {
    const val VERSION = 1
    const val NIEVE_PORT = 25585

    const val AUTH_CHANNEL = "nieve:auth"
    const val LEAVE_CHANNEL = "nieve:leave"

    private const val MARKER = "nieve"
    private const val SEPARATOR = '\u0000'

    class Hostname(val host: String, val version: Int, val origin: String)

    fun encodeHostname(host: String, origin: String): String = "$host$SEPARATOR$MARKER$VERSION$SEPARATOR$origin"

    fun decodeHostname(address: String): Hostname? {
        val parts = address.split(SEPARATOR)
        if (parts.size != 3 || !parts[1].startsWith(MARKER)) return null
        return Hostname(parts[0], parts[1].removePrefix(MARKER).toIntOrNull() ?: 0, parts[2])
    }
}
