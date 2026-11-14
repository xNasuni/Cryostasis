package win.transgirls.nieve.systems.session

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import win.transgirls.nieve.systems.network.PacketTable
import win.transgirls.nieve.tools.packet
import win.transgirls.nieve.tools.readVarInt
import win.transgirls.nieve.tools.writeVarInt

class Mirror(private val ids: PacketTable) {
    private class Chunk(val base: ByteArray) {
        var light: ByteArray? = null
        val deltas = ArrayList<Delta>()
    }

    private class Delta(val name: String, val pos: Long, val bytes: ByteArray)

    private class Entity {
        var add: ByteArray? = null
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var yRot = 0f
        var xRot = 0f
        var head: Byte? = null
        var moved = false
        var rotated = false
        var positionAt = -1
        var rotationAt = -1
        var passengers: ByteArray? = null
        val metadata = ArrayList<ByteArray>()
        val equipment = ArrayList<ByteArray>()
        val attributes = ArrayList<ByteArray>()
        val effects = ArrayList<ByteArray>()
    }

    private val latest = LinkedHashMap<String, ByteArray>()
    private val events = LinkedHashMap<Int, ByteArray>()
    private val history = ArrayDeque<ByteArray>()
    private val chunks = LinkedHashMap<Long, Chunk>()
    private val entities = HashMap<Int, Entity>()
    private var inventory: ByteArray? = null
    private val slots = LinkedHashMap<Int, ByteArray>()
    private val playerSlots = LinkedHashMap<Int, ByteArray>()
    private var respawn: ByteArray? = null
    private var playerId = -1
    private var hasPosition = false
    private var px = 0.0
    private var py = 0.0
    private var pz = 0.0
    private var yaw = 0f
    private var pitch = 0f

    fun reset() {
        latest.clear()
        events.clear()
        history.clear()
        chunks.clear()
        entities.clear()
        inventory = null
        slots.clear()
        playerSlots.clear()
        respawn = null
        hasPosition = false
    }

    fun clientbound(buf: ByteBuf) {
        val view = buf.duplicate()
        val name = ids.playClientbound[view.readVarInt()] ?: return
        try {
            record(name, view, buf)
        } catch (_: IndexOutOfBoundsException) {
        }
    }

    fun serverbound(buf: ByteBuf) {
        val view = buf.duplicate()
        val name = ids.playServerbound[view.readVarInt()] ?: return
        try {
            when (name) {
                "move_player_pos" -> position(view.readDouble(), view.readDouble(), view.readDouble())
                "move_player_pos_rot" -> {
                    position(view.readDouble(), view.readDouble(), view.readDouble())
                    yaw = view.readFloat()
                    pitch = view.readFloat()
                }

                "move_player_rot" -> {
                    yaw = view.readFloat()
                    pitch = view.readFloat()
                }
            }
        } catch (_: IndexOutOfBoundsException) {
        }
    }

    fun confirmTeleport(buf: ByteBuf, id: Int) {
        buf.writeVarInt(id)
        buf.writeDouble(px)
        buf.writeDouble(py)
        buf.writeDouble(pz)
        buf.writeFloat(yaw)
        buf.writeFloat(pitch)
    }

    fun replay(): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        respawn?.let { out += it }
        latest.values.forEach { out += it }
        if (hasPosition) {
            out += build(ids.clientbound("player_position")) {
                writeVarInt(TELEPORT_ID)
                writeDouble(px)
                writeDouble(py)
                writeDouble(pz)
                repeat(3) { writeDouble(0.0) }
                writeFloat(yaw)
                writeFloat(pitch)
                writeInt(0)
            }
        }
        events.values.forEach { out += it }
        out += history
        inventory?.let { out += it }
        out += slots.values
        out += playerSlots.values

        out += build(ids.clientbound("chunk_batch_start")) {}
        for (chunk in chunks.values) {
            out += chunk.base
            chunk.light?.let { out += it }
            chunk.deltas.forEach { out += it.bytes }
        }
        out += build(ids.clientbound("chunk_batch_finished")) { writeVarInt(chunks.size) }

        for (entity in entities.values) {
            entity.add?.let { out += patched(entity, it) }
            out += entity.metadata
            out += entity.equipment
            out += entity.attributes
            out += entity.effects
            entity.passengers?.let { out += it }
        }
        return out
    }

    private fun patched(entity: Entity, add: ByteArray): ByteArray {
        if (entity.positionAt < 0 || entity.rotationAt < 0) return add
        val bytes = add.copyOf()
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        buffer.putDouble(entity.positionAt, entity.x)
        buffer.putDouble(entity.positionAt + 8, entity.y)
        buffer.putDouble(entity.positionAt + 16, entity.z)
        if (entity.rotated) {
            bytes[entity.rotationAt] = (entity.xRot * 256f / 360f).toInt().toByte()
            bytes[entity.rotationAt + 1] = (entity.yRot * 256f / 360f).toInt().toByte()
        }
        entity.head?.let { bytes[entity.rotationAt + 2] = it }
        return bytes
    }

    val chunkCount get() = chunks.size
    val entityCount get() = entities.size

    private fun record(name: String, view: ByteBuf, buf: ByteBuf) {
        val bytes by lazy { ByteBufUtil.getBytes(buf) }
        when (name) {
            "login" -> playerId = view.readInt()
            "respawn" -> {
                chunks.clear()
                entities.clear()
                inventory = null
                slots.clear()
                playerSlots.clear()
                hasPosition = false
                respawn = bytes
            }

            in LATEST -> {
                latest.remove(name)
                latest[name] = bytes
            }

            in HISTORY -> {
                if (name == "player_info_update" && view.getUnsignedByte(view.readerIndex())
                        .toInt() == LATENCY_ONLY
                ) {
                    return
                }
                history.addLast(bytes)
                while (history.size > HISTORY_CAP) history.removeFirst()
            }

            "game_event" -> {
                val event = view.readUnsignedByte().toInt()
                val key = if (event == RAIN_STOPPED) RAIN_STARTED else event
                if (key in EVENTS) events[key] = bytes
            }

            "player_position" -> {
                view.readVarInt()
                val x = view.readDouble()
                val y = view.readDouble()
                val z = view.readDouble()
                repeat(3) { view.readDouble() }
                val newYaw = view.readFloat()
                val newPitch = view.readFloat()
                val relative = view.readInt()
                position(
                    if (relative and X != 0) px + x else x,
                    if (relative and Y != 0) py + y else y,
                    if (relative and Z != 0) pz + z else z
                )
                yaw = if (relative and Y_ROT != 0) yaw + newYaw else newYaw
                pitch = if (relative and X_ROT != 0) pitch + newPitch else newPitch
            }

            "player_rotation" -> {
                val newYaw = view.readFloat()
                val relativeYaw = view.readBoolean()
                val newPitch = view.readFloat()
                val relativePitch = view.readBoolean()
                yaw = if (relativeYaw) yaw + newYaw else newYaw
                pitch = if (relativePitch) pitch + newPitch else newPitch
            }

            "container_set_content" -> if (view.readVarInt() == 0) {
                inventory = bytes
                slots.clear()
            }

            "container_set_slot" -> if (view.readVarInt() == 0) {
                view.readVarInt()
                slots[view.readShort().toInt()] = bytes
            }

            "set_player_inventory" -> playerSlots[view.readVarInt()] = bytes

            "level_chunk_with_light" -> chunks[key(view.readInt(), view.readInt())] = Chunk(bytes)
            "forget_level_chunk" -> chunks.remove(view.readLong())
            "light_update" -> chunks[key(view.readVarInt(), view.readVarInt())]?.light = bytes
            "block_update", "block_entity_data" -> {
                val pos = view.readLong()
                val chunk = chunks[key(blockX(pos) shr 4, blockZ(pos) shr 4)] ?: return
                chunk.deltas.removeAll { it.name == name && it.pos == pos }
                chunk.add(Delta(name, pos, bytes))
            }

            "section_blocks_update" -> {
                val pos = view.readLong()
                val chunk = chunks[key((pos shr 42).toInt(), (pos shl 22 shr 42).toInt())] ?: return
                chunk.add(Delta(name, Long.MIN_VALUE, bytes))
            }

            "add_entity" -> add(view, bytes, buf.readerIndex())
            "remove_entities" -> repeat(view.readVarInt()) { entities.remove(view.readVarInt()) }
            "set_entity_data" -> entity(view.readVarInt())?.metadata?.add(bytes)
            "set_equipment" -> entity(view.readVarInt())?.equipment?.add(bytes)
            "update_attributes" -> entity(view.readVarInt())?.attributes?.add(bytes)
            "update_mob_effect", "remove_mob_effect" -> entity(view.readVarInt())?.effects?.add(bytes)
            "set_passengers" -> entity(view.readVarInt())?.passengers = bytes
            "rotate_head" -> entity(view.readVarInt())?.head = view.readByte()
            "move_entity_pos" -> entity(view.readVarInt())?.let { shift(it, view) }
            "move_entity_pos_rot" -> entity(view.readVarInt())?.let {
                shift(it, view)
                it.yRot = angle(view.readByte())
                it.xRot = angle(view.readByte())
                it.rotated = true
            }

            "move_entity_rot" -> entity(view.readVarInt())?.let {
                view.readBoolean()
                it.yRot = angle(view.readByte())
                it.xRot = angle(view.readByte())
                it.rotated = true
            }

            "entity_position_sync" -> entity(view.readVarInt())?.let {
                if (view.readVarInt() == STEPPED) {
                    repeat(view.readVarInt()) { _ ->
                        move(it, view.readDouble(), view.readDouble(), view.readDouble())
                        view.readVarInt()
                    }
                } else {
                    move(it, view.readDouble(), view.readDouble(), view.readDouble())
                }
                it.yRot = view.readFloat()
                it.xRot = view.readFloat()
                it.rotated = true
            }

            "teleport_entity" -> entity(view.readVarInt())?.let {
                val x = view.readDouble()
                val y = view.readDouble()
                val z = view.readDouble()
                repeat(3) { view.readDouble() }
                val newYaw = view.readFloat()
                val newPitch = view.readFloat()
                val relative = view.readInt()
                move(
                    it,
                    if (relative and X != 0) it.x + x else x,
                    if (relative and Y != 0) it.y + y else y,
                    if (relative and Z != 0) it.z + z else z
                )
                it.yRot = if (relative and Y_ROT != 0) it.yRot + newYaw else newYaw
                it.xRot = if (relative and X_ROT != 0) it.xRot + newPitch else newPitch
                it.rotated = true
            }
        }
    }

    private fun add(view: ByteBuf, bytes: ByteArray, base: Int) {
        val id = view.readVarInt()
        val entity = Entity().also { it.add = bytes }
        entities[id] = entity
        try {
            view.skipBytes(16)
            view.readVarInt()
            val positionAt = view.readerIndex() - base
            entity.x = view.readDouble()
            entity.y = view.readDouble()
            entity.z = view.readDouble()
            val movement = view.readUnsignedByte().toInt()
            if (movement != 0) {
                view.skipBytes(5)
                if (movement and LP_EXTENDED != 0) view.readVarInt()
            }
            val rotationAt = view.readerIndex() - base
            entity.xRot = angle(view.readByte())
            entity.yRot = angle(view.readByte())
            entity.head = view.readByte()
            entity.positionAt = positionAt
            entity.rotationAt = rotationAt
        } catch (_: IndexOutOfBoundsException) {
        }
    }

    private fun entity(id: Int) = entities[id] ?: if (id == playerId) Entity().also { entities[id] = it } else null

    private fun shift(entity: Entity, view: ByteBuf) {
        val steps = view.readVarInt() ushr 1
        if (steps == 0) return delta(entity, view)
        repeat(steps) {
            view.readVarInt()
            delta(entity, view)
        }
    }

    private fun delta(entity: Entity, view: ByteBuf) {
        val xa = view.readShort().toLong()
        val ya = view.readShort().toLong()
        val za = view.readShort().toLong()
        move(entity, step(entity.x, xa), step(entity.y, ya), step(entity.z, za))
    }

    private fun step(value: Double, delta: Long) = if (delta == 0L) value else (Math.round(value * 4096.0) + delta) / 4096.0

    private fun move(entity: Entity, x: Double, y: Double, z: Double) {
        entity.x = x
        entity.y = y
        entity.z = z
        entity.moved = true
    }

    private fun position(x: Double, y: Double, z: Double) {
        px = x
        py = y
        pz = z
        hasPosition = true
    }

    private fun Chunk.add(delta: Delta) {
        deltas += delta
        while (deltas.size > DELTA_CAP) deltas.removeAt(0)
    }

    private fun angle(value: Byte) = value * 360f / 256f

    private fun key(x: Int, z: Int) = (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)

    private fun blockX(pos: Long) = (pos shr 38).toInt()

    private fun blockZ(pos: Long) = (pos shl 26 shr 38).toInt()

    private fun build(id: Int, body: ByteBuf.() -> Unit): ByteArray {
        val buf = packet(id, body)
        return ByteBufUtil.getBytes(buf).also { buf.release() }
    }

    companion object {
        // TODO: verify this wont ever fallthrough to the real server for detection security reasons
        const val TELEPORT_ID = 0x4E495645

        private const val HISTORY_CAP = 4000
        private const val DELTA_CAP = 1024
        private const val LATENCY_ONLY = 0x10
        private const val LP_EXTENDED = 4
        private const val STEPPED = 1
        private const val RAIN_STARTED = 1
        private const val RAIN_STOPPED = 2
        private const val X = 1
        private const val Y = 2
        private const val Z = 4
        private const val Y_ROT = 8
        private const val X_ROT = 16

        private val EVENTS = setOf(1, 3, 7, 8, 13)

        private val LATEST = setOf(
            "change_difficulty",
            "player_abilities",
            "commands",
            "update_recipes",
            "update_tags",
            "server_data",
            "game_rule_values",
            "initialize_border",
            "set_border_center",
            "set_border_lerp_size",
            "set_border_size",
            "set_border_warning_delay",
            "set_border_warning_distance",
            "set_time",
            "set_default_spawn_position",
            "ticking_state",
            "ticking_step",
            "set_simulation_distance",
            "set_chunk_cache_radius",
            "set_chunk_cache_center",
            "recipe_book_settings",
            "set_experience",
            "set_health",
            "set_held_slot",
            "tab_list"
        )

        private val HISTORY = setOf(
            "player_info_update",
            "player_info_remove",
            "set_player_team",
            "set_objective",
            "set_display_objective",
            "set_score",
            "reset_score",
            "boss_event",
            "update_advancements",
            "recipe_book_add",
            "recipe_book_remove",
            "waypoint"
        )
    }
}
