package win.transgirls.nieve.tools

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.util.UUID

fun ByteBuf.readVarInt(): Int {
    var value = 0
    for (i in 0 until 5) {
        val b = readByte().toInt()
        value = value or ((b and 0x7F) shl (7 * i))
        if (b and 0x80 == 0) return value
    }
    error("VarInt too big")
}

fun ByteBuf.writeVarInt(value: Int) {
    var v = value
    while (v and 0x7F.inv() != 0) {
        writeByte((v and 0x7F) or 0x80)
        v = v ushr 7
    }
    writeByte(v)
}

fun ByteBuf.peekVarInt(): Int = duplicate().readVarInt()

fun ByteBuf.readString(): String {
    val length = readVarInt()
    val s = toString(readerIndex(), length, Charsets.UTF_8)
    skipBytes(length)
    return s
}

fun ByteBuf.writeString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeVarInt(bytes.size)
    writeBytes(bytes)
}

fun ByteBuf.readByteArray(): ByteArray {
    val bytes = ByteArray(readVarInt())
    readBytes(bytes)
    return bytes
}

fun ByteBuf.writeByteArray(value: ByteArray) {
    writeVarInt(value.size)
    writeBytes(value)
}

fun ByteBuf.readUuid(): UUID = UUID(readLong(), readLong())

fun ByteBuf.writeUuid(value: UUID) {
    writeLong(value.mostSignificantBits)
    writeLong(value.leastSignificantBits)
}

fun packet(id: Int, body: ByteBuf.() -> Unit): ByteBuf {
    val buf = Unpooled.buffer()
    buf.writeVarInt(id)
    buf.body()
    return buf
}
