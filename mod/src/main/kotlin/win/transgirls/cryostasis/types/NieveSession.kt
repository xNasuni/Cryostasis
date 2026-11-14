package win.transgirls.cryostasis.types

data class NieveSession(
    val id: Int,
    val name: String,
    val host: String,
    val port: Int,
    val origin: String,
    val detached: Boolean,
    val uptime: Long,
    val at: Long = System.currentTimeMillis()
)
