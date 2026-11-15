package win.transgirls.nieve.systems.network

import com.google.gson.JsonParser

object PacketIds {
    const val HANDSHAKE = 0x00
    const val STATUS_REQUEST = 0x00
    const val STATUS_PING = 0x01

    const val LOGIN_HELLO = 0x00
    const val ENCRYPTION_RESPONSE = 0x01
    const val LOGIN_QUERY_RESPONSE = 0x02
    const val LOGIN_ACKNOWLEDGED = 0x03

    const val LOGIN_DISCONNECT = 0x00
    const val ENCRYPTION_REQUEST = 0x01
    const val LOGIN_SUCCESS = 0x02
    const val SET_COMPRESSION = 0x03
    const val LOGIN_QUERY = 0x04
}

class PacketTable private constructor(private val ids: Map<String, Int>) {
    val playKeepAlive = id("play/s2c/keep_alive")
    val playPing = id("play/s2c/ping")
    val playPosition = id("play/s2c/player_position")
    val playChunkBatchFinished = id("play/s2c/chunk_batch_finished")
    val playDisconnect = id("play/s2c/disconnect")
    val playLogin = id("play/s2c/login")
    val playKeepAliveResponse = id("play/c2s/keep_alive")
    val playPong = id("play/c2s/pong")
    val playAcceptTeleport = id("play/c2s/accept_teleportation")
    val playMovePlayerPosRot = id("play/c2s/move_player_pos_rot")
    val playChunkBatchReceived = id("play/c2s/chunk_batch_received")
    val playCustomPayload = id("play/c2s/custom_payload")
    val playCustomPayloadClientbound = id("play/s2c/custom_payload")
    val playPlayerChat = id("play/s2c/player_chat")
    val playDeleteChat = id("play/s2c/delete_chat")
    val playClientTickEnd = id("play/c2s/client_tick_end")
    val configDisconnect = id("configuration/s2c/disconnect")
    val configFinish = id("configuration/c2s/finish_configuration")
    val configReplayed = listOf(
        "custom_payload",
        "select_known_packs",
        "resource_pack_push",
        "resource_pack_pop",
        "reset_chat",
        "registry_data",
        "update_enabled_features",
        "update_tags",
        "custom_report_details",
        "server_links"
    )
        .map { id("configuration/s2c/$it") }
        .toSet()

    val playClientbound = names("play/s2c/")
    val playServerbound = names("play/c2s/")
    val playStartConfiguration = id("play/s2c/start_configuration")
    val playTransfer = id("play/s2c/transfer")
    val playConfigurationAcknowledged = id("play/c2s/configuration_acknowledged")
    val playPlayerLoaded = id("play/c2s/player_loaded")
    val configFinishClientbound = id("configuration/s2c/finish_configuration")
    val configKeepAlive = id("configuration/s2c/keep_alive")
    val configKeepAliveResponse = id("configuration/c2s/keep_alive")
    val configPing = id("configuration/s2c/ping")
    val configPong = id("configuration/c2s/pong")
    val configKnownPacks = id("configuration/s2c/select_known_packs")
    val configKnownPacksResponse = id("configuration/c2s/select_known_packs")
    val configCodeOfConduct = id("configuration/s2c/code_of_conduct")
    val configCodeOfConductAccepted = id("configuration/c2s/accept_code_of_conduct")

    fun clientbound(name: String) = id("play/s2c/$name")

    fun serverbound(name: String) = id("play/c2s/$name")

    private fun id(key: String) = ids.getValue(key)

    private fun names(prefix: String) = ids.entries
        .filter { it.key.startsWith(prefix) }
        .associate { it.value to it.key.removePrefix(prefix) }

    companion object {
        fun forProtocol(protocol: Int): PacketTable? {
            val report = PacketTable::class.java.getResourceAsStream("/packets/$protocol.json") ?: return null
            val ids = HashMap<String, Int>()
            for ((state, directions) in JsonParser.parseReader(report.reader()).asJsonObject.entrySet()) {
                for ((direction, packets) in directions.asJsonObject.entrySet()) {
                    val side = if (direction == "clientbound") "s2c" else "c2s"
                    for ((name, packet) in packets.asJsonObject.entrySet()) {
                        ids["$state/$side/${name.removePrefix("minecraft:")}"] =
                            packet.asJsonObject.get("protocol_id").asInt
                    }
                }
            }
            return PacketTable(ids)
        }
    }
}
