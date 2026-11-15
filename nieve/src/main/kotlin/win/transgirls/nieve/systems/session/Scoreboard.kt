package win.transgirls.nieve.systems.session

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import win.transgirls.nieve.systems.chat.Components
import win.transgirls.nieve.systems.network.PacketTable
import win.transgirls.nieve.tools.encode
import win.transgirls.nieve.tools.readString
import win.transgirls.nieve.tools.readVarInt
import win.transgirls.nieve.tools.writeString
import win.transgirls.nieve.tools.writeVarInt

class Scoreboard(private val ids: PacketTable) {
    private class Team(var parameters: ByteArray)

    private data class Score(val objective: String, val owner: String)

    private val objectives = LinkedHashMap<String, ByteArray>()
    private val displays = LinkedHashMap<Int, String>()
    private val scores = LinkedHashMap<Score, ByteArray>()
    private val teams = LinkedHashMap<String, Team>()
    private val membership = LinkedHashMap<String, String>()

    fun reset() {
        objectives.clear()
        displays.clear()
        scores.clear()
        teams.clear()
        membership.clear()
    }

    fun objective(view: ByteBuf) {
        val name = view.readString()
        val method = view.readByte().toInt()
        val payload = ByteBufUtil.getBytes(view, view.readerIndex(), view.readableBytes())
        when (method) {
            OBJECTIVE_ADD -> objectives.putIfAbsent(name, payload)
            OBJECTIVE_REMOVE -> if (objectives.remove(name) != null) {
                scores.keys.removeAll { it.objective == name }
                displays.values.removeAll { it == name }
            }

            OBJECTIVE_CHANGE -> if (name in objectives) objectives[name] = payload
        }
    }

    fun display(view: ByteBuf) {
        val slot = view.readVarInt()
        val name = view.readString()
        if (name in objectives) displays[slot] = name else displays.remove(slot)
    }

    fun score(view: ByteBuf, packet: ByteBuf) {
        val owner = view.readString()
        val objective = view.readString()
        if (objective in objectives) scores[Score(objective, owner)] = ByteBufUtil.getBytes(packet)
    }

    fun resetScore(view: ByteBuf) {
        val owner = view.readString()
        val objective = if (view.readBoolean()) view.readString() else null
        if (objective == null) scores.keys.removeAll { it.owner == owner } else scores.remove(Score(objective, owner))
    }

    fun team(view: ByteBuf) {
        val name = view.readString()
        when (view.readByte().toInt()) {
            TEAM_ADD -> {
                val start = view.readerIndex()
                skipParameters(view)
                val parameters = ByteBufUtil.getBytes(view, start, view.readerIndex() - start)
                val team = teams.getOrPut(name) { Team(parameters) }
                team.parameters = parameters
                members(view).forEach { join(it, name) }
            }

            TEAM_REMOVE -> if (teams.remove(name) != null) membership.values.removeAll { it == name }
            TEAM_CHANGE -> teams[name]?.parameters =
                ByteBufUtil.getBytes(view, view.readerIndex(), view.readableBytes())

            TEAM_JOIN -> if (name in teams) members(view).forEach { join(it, name) }
            TEAM_LEAVE -> if (name in teams) members(view).forEach { if (membership[it] == name) membership.remove(it) }
        }
    }

    fun replay(): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        for ((name, payload) in objectives) {
            out += encode(ids.clientbound("set_objective")) {
                writeString(name)
                writeByte(OBJECTIVE_ADD)
                writeBytes(payload)
            }
        }
        for ((slot, name) in displays) {
            out += encode(ids.clientbound("set_display_objective")) {
                writeVarInt(slot)
                writeString(name)
            }
        }
        out += scores.values
        for ((name, team) in teams) {
            val members = membership.filterValues { it == name }.keys
            out += encode(ids.clientbound("set_player_team")) {
                writeString(name)
                writeByte(TEAM_ADD)
                writeBytes(team.parameters)
                writeVarInt(members.size)
                members.forEach { writeString(it) }
            }
        }
        return out
    }

    private fun join(player: String, team: String) {
        membership.remove(player)
        membership[player] = team
    }

    private fun members(view: ByteBuf) = List(view.readVarInt()) { view.readString() }

    private fun skipParameters(view: ByteBuf) {
        repeat(COMPONENTS) { Components.skip(view) }
        view.readVarInt()
        view.readVarInt()
        if (view.readBoolean()) view.readVarInt()
        view.skipBytes(1)
    }

    private companion object {
        const val OBJECTIVE_ADD = 0
        const val OBJECTIVE_REMOVE = 1
        const val OBJECTIVE_CHANGE = 2
        const val TEAM_ADD = 0
        const val TEAM_REMOVE = 1
        const val TEAM_CHANGE = 2
        const val TEAM_JOIN = 3
        const val TEAM_LEAVE = 4
        const val COMPONENTS = 3
    }
}
