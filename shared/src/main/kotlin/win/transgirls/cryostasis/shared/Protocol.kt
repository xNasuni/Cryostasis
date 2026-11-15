package win.transgirls.cryostasis.shared

object Protocol {
    const val VERSION = 1
    const val NIEVE_PORT = 25585

    const val AUTH_CHANNEL = "nieve:auth"
    const val LEAVE_CHANNEL = "nieve:leave"

    private const val MARKER = "nieve"
    private const val SEPARATOR = '\u0000'

    const val NEW_SESSION = 0

    class Route(val host: String, val version: Int, val origin: String, val session: Int)

    fun encodeRoute(host: String, origin: String, session: Int = NEW_SESSION): String = "$host$SEPARATOR$MARKER$VERSION$SEPARATOR$origin$SEPARATOR$session"

    fun decodeRoute(address: String): Route? {
        val parts = address.split(SEPARATOR)
        if (parts.size != 4 || !parts[1].startsWith(MARKER)) return null
        return Route(
            parts[0],
            parts[1].removePrefix(MARKER).toIntOrNull() ?: 0,
            parts[2],
            parts[3].toIntOrNull() ?: return null
        )
    }
}
