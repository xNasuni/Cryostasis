package win.transgirls.nieve.systems.session

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import win.transgirls.nieve.systems.chat.Components
import win.transgirls.nieve.systems.network.PacketTable
import win.transgirls.nieve.tools.encode
import win.transgirls.nieve.tools.readUuid
import win.transgirls.nieve.tools.readVarInt
import win.transgirls.nieve.tools.skipPrefixed
import win.transgirls.nieve.tools.writeUuid
import win.transgirls.nieve.tools.writeVarInt
import java.util.UUID

class Tablist(private val ids: PacketTable) {
    private class Entry {
        val parts = arrayOfNulls<ByteArray>(ACTIONS)

        fun mask() = parts.indices.fold(0) { mask, action -> if (parts[action] != null) mask or (1 shl action) else mask }
    }

    private val entries = LinkedHashMap<UUID, Entry>()

    fun reset() = entries.clear()

    fun update(view: ByteBuf) {
        val actions = view.readUnsignedByte().toInt()
        repeat(view.readVarInt()) {
            val uuid = view.readUuid()
            val entry = if (actions and (1 shl ADD_PLAYER) != 0) entries.getOrPut(uuid) { Entry() } else entries[uuid]
            for (action in 0 until ACTIONS) {
                if (actions and (1 shl action) == 0) continue
                val start = view.readerIndex()
                skip(action, view)
                if (entry == null || (action == ADD_PLAYER && entry.parts[ADD_PLAYER] != null)) continue
                entry.parts[action] = ByteBufUtil.getBytes(view, start, view.readerIndex() - start)
            }
        }
    }

    fun remove(view: ByteBuf) = repeat(view.readVarInt()) { entries.remove(view.readUuid()) }

    fun replay(): List<ByteArray> = entries.entries.groupBy({ it.value.mask() }, { it.key to it.value }).map { (mask, group) ->
        encode(ids.clientbound("player_info_update")) {
            writeByte(mask)
            writeVarInt(group.size)
            for ((uuid, entry) in group) {
                writeUuid(uuid)
                for (action in 0 until ACTIONS) if (mask and (1 shl action) != 0) writeBytes(entry.parts[action]!!)
            }
        }
    }

    private fun skip(action: Int, buf: ByteBuf) {
        when (action) {
            ADD_PLAYER -> {
                buf.skipPrefixed()
                repeat(buf.readVarInt()) {
                    buf.skipPrefixed()
                    buf.skipPrefixed()
                    if (buf.readBoolean()) buf.skipPrefixed()
                }
            }

            INITIALIZE_CHAT -> if (buf.readBoolean()) {
                buf.skipBytes(UUID_BYTES + Long.SIZE_BYTES)
                buf.skipPrefixed()
                buf.skipPrefixed()
            }

            UPDATE_GAME_MODE, UPDATE_LATENCY, UPDATE_LIST_ORDER -> buf.readVarInt()
            UPDATE_LISTED, UPDATE_HAT -> buf.skipBytes(1)
            UPDATE_DISPLAY_NAME -> if (buf.readBoolean()) Components.skip(buf)
        }
    }

    private companion object {
        const val ADD_PLAYER = 0
        const val INITIALIZE_CHAT = 1
        const val UPDATE_GAME_MODE = 2
        const val UPDATE_LISTED = 3
        const val UPDATE_LATENCY = 4
        const val UPDATE_DISPLAY_NAME = 5
        const val UPDATE_LIST_ORDER = 6
        const val UPDATE_HAT = 7
        const val ACTIONS = 8
        const val UUID_BYTES = 16
    }
}
