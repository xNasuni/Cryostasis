package win.transgirls.cryostasis.types

data class NieveSession(
    val id: Int,
    val name: String,
    val host: String,
    val port: Int,
    val origin: String,
    val detached: Boolean,
    val uptime: Long,
    val ping: Int,
    val players: List<String>,
    val max: Int,
    val at: Long = System.currentTimeMillis()
)
