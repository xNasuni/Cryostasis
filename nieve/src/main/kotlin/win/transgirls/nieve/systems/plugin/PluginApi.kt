package win.transgirls.nieve.systems.plugin

import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.systems.network.PacketTable
import java.util.UUID

//TODO: extract this to its own nieve-api module for it to be on the maven. brainstorm should people need the nieve internals? maybe export a interface for interacting with it no i dont see the point if the api is enough
interface NievePlugin {
    val name: String

    fun enable(api: PluginApi)

    fun disable() {}
}

interface PluginApi {
    val name: String

    fun log(message: String)

    fun payloads(channel: String, handler: PayloadHandler)

    fun onDetach(handler: SessionHandler)

    fun onAttach(handler: SessionHandler)

    fun command(command: Command)
}

fun interface SessionHandler {
    fun handle(session: SessionView)
}

fun interface PayloadHandler {
    fun handle(event: PayloadEvent)
}

enum class Direction { CLIENTBOUND, SERVERBOUND }

interface SessionView {
    val name: String
    val uuid: UUID
    val host: String
    val port: Int
    val protocol: Int
    val label: String
    val detached: Boolean
    val ids: PacketTable
    val attributes: MutableMap<String, Any>

    fun say(message: String)

    fun sendToClient(channel: String, data: ByteArray)

    fun sendToServer(channel: String, data: ByteArray)
}

class PayloadEvent internal constructor(
    val session: SessionView,
    val direction: Direction,
    val channel: String,
    var data: ByteArray
) {
    internal var cancelled = false
    internal val toClient = ArrayList<Pair<String, ByteArray>>()
    internal val toServer = ArrayList<Pair<String, ByteArray>>()

    fun cancel() {
        cancelled = true
    }

    fun sendToClient(channel: String, data: ByteArray) {
        toClient += channel to data
    }

    fun sendToServer(channel: String, data: ByteArray) {
        toServer += channel to data
    }
}
