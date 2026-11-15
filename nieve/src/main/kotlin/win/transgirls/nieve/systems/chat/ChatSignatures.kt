package win.transgirls.nieve.systems.chat

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import win.transgirls.nieve.tools.Log
import win.transgirls.nieve.tools.peekVarInt
import win.transgirls.nieve.tools.readString
import win.transgirls.nieve.tools.readVarInt
import win.transgirls.nieve.tools.writeVarInt
import java.nio.ByteBuffer

class ChatSignatures {
    private class Packed(val id: Int, val full: ByteArray?)

    private class Parsed(val head: Int, val seenAt: Int, val seen: List<Packed>, val tail: Int, val own: ByteArray?)

    private val entries = arrayOfNulls<ByteArray>(CAPACITY)
    private var reported = false

    fun reset() {
        entries.fill(null)
    }

    fun record(buf: ByteBuf) {
        try {
            val parsed = parse(buf)
            push(parsed.seen.map(::resolve), parsed.own)
        } catch (e: Exception) {
            if (!reported) Log.exception("chat", "couldn't read player_chat", e)
            reported = true
        }
    }

    fun rewrite(buf: ByteBuf, index: Int): ByteBuf {
        val parsed = parse(buf)
        val out = Unpooled.buffer()
        out.writeVarInt(buf.peekVarInt())
        out.writeVarInt(index)
        out.writeBytes(buf, parsed.head, parsed.seenAt - parsed.head)
        out.writeVarInt(parsed.seen.size)
        parsed.seen.forEach {
            out.writeVarInt(0)
            out.writeBytes(resolve(it))
        }
        out.writeBytes(buf, parsed.tail, buf.writerIndex() - parsed.tail)
        return out
    }

    fun expandDeleted(buf: ByteBuf): ByteBuf {
        val view = buf.duplicate()
        view.readVarInt()
        val signature = resolve(packed(view))
        val out = Unpooled.buffer()
        out.writeVarInt(buf.peekVarInt())
        out.writeVarInt(0)
        out.writeBytes(signature)
        return out
    }

    private fun parse(buf: ByteBuf): Parsed {
        val view = buf.duplicate()
        view.readVarInt()
        view.readVarInt()
        val head = view.readerIndex()
        view.skipBytes(16)
        view.readVarInt()
        val own = if (view.readBoolean()) ByteArray(SIGNATURE).also { view.readBytes(it) } else null
        view.readString()
        view.skipBytes(16)
        val seenAt = view.readerIndex()
        val seen = List(view.readVarInt()) { packed(view) }
        return Parsed(head, seenAt, seen, view.readerIndex(), own)
    }

    private fun packed(view: ByteBuf): Packed {
        val id = view.readVarInt() - 1
        return Packed(id, if (id == -1) ByteArray(SIGNATURE).also { view.readBytes(it) } else null)
    }

    private fun resolve(packed: Packed): ByteArray = packed.full ?: entries[packed.id] ?: error("Unknown chat signature ${packed.id}")

    private fun push(seen: List<ByteArray>, own: ByteArray?) {
        val queue = ArrayDeque(seen)
        own?.let { queue.addLast(it) }
        val added = queue.map { ByteBuffer.wrap(it) }.toSet()
        for (i in entries.indices) {
            if (queue.isEmpty()) break
            val previous = entries[i]
            entries[i] = queue.removeLast()
            if (previous != null && ByteBuffer.wrap(previous) !in added) queue.addFirst(previous)
        }
    }

    companion object {
        private const val CAPACITY = 128
        private const val SIGNATURE = 256
    }
}
