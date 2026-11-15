package win.transgirls.cryostasis

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import win.transgirls.cryostasis.shared.Protocol
import win.transgirls.cryostasis.systems.network.NieveLink
import java.nio.file.Files

object Config {
    const val LOCAL_HOST = "127.0.0.1"

    class Endpoint(val host: String, val port: Int)

    private val file = FabricLoader.getInstance().configDir.resolve("cryostasis.json")

    var remote = false
        private set

    var address = "$LOCAL_HOST:${Protocol.NIEVE_PORT}"
        private set

    fun load() {
        if (Files.exists(file)) {
            val json = JsonParser.parseString(Files.readString(file)).asJsonObject
            remote = json.get("remote")?.asBoolean ?: false
            address = json.get("address")?.asString ?: address
        }
        activate()
    }

    fun update(remote: Boolean, address: String): Boolean {
        if (remote && parse(address) == null) return false
        val changed = remote != this.remote || (remote && address != this.address)
        this.remote = remote
        if (remote) this.address = address
        Files.writeString(
            file,
            JsonObject().apply {
                addProperty("remote", this@Config.remote)
                addProperty("address", this@Config.address)
            }.toString()
        )
        if (changed) {
            activate()
            NieveLink.reconnect()
        }
        return true
    }

    fun parse(text: String): Endpoint? {
        val value = text.trim()
        val split = if (value.startsWith("[")) value.indexOf("]:") + 1 else value.lastIndexOf(':')
        val host = (if (split > 0) value.substring(0, split) else value).removeSurrounding("[", "]")
        val port = if (split > 0) value.substring(split + 1).toIntOrNull() else Protocol.NIEVE_PORT
        return if (host.isEmpty() || port == null || port !in 1..65535) null else Endpoint(host, port)
    }

    private fun activate() {
        val endpoint = if (remote) parse(address) else null
        Cryostasis.nieveHost = endpoint?.host ?: LOCAL_HOST
        Cryostasis.nievePort = endpoint?.port ?: Protocol.NIEVE_PORT
    }
}
