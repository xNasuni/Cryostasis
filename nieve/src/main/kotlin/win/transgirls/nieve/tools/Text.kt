package win.transgirls.nieve.tools

import io.netty.buffer.ByteBuf

object Text {
    const val ACCENT = "#AFFFFF"
    const val TITLE = "Nieve Instance"

    fun motd(info: String): String = """{"text":"","extra":[{"text":"A Nieve Server","color":"$ACCENT"},{"text":"\n"},{"text":"${escape(info)}","color":"gray"}]}"""

    class Part(val text: String, val color: String? = null)

    fun json(description: String, color: String?) = json(listOf(Part(description, color)))

    fun json(parts: List<Part>): String {
        val body = parts.joinToString(",") { part ->
            val colorField = if (part.color != null) ",\"color\":\"${part.color}\"" else ""
            """{"text":"${escape(part.text)}"$colorField}"""
        }
        return """{"text":"","extra":[{"text":"$TITLE","color":"$ACCENT"},{"text":"\n"},$body]}"""
    }

    fun writeNbt(buf: ByteBuf, description: String, color: String?) = writeNbt(buf, listOf(Part(description, color)))

    fun writeNbt(buf: ByteBuf, parts: List<Part>) {
        buf.writeByte(TAG_COMPOUND)
        buf.string("text", "")
        buf.writeByte(TAG_LIST)
        buf.name("extra")
        buf.writeByte(TAG_COMPOUND)
        buf.writeInt(2 + parts.size)
        buf.string("text", TITLE)
        buf.string("color", ACCENT)
        buf.writeByte(TAG_END)
        buf.string("text", "\n")
        buf.writeByte(TAG_END)
        for (part in parts) {
            buf.string("text", part.text)
            if (part.color != null) buf.string("color", part.color)
            buf.writeByte(TAG_END)
        }
        buf.writeByte(TAG_END)
    }

    private fun escape(s: String) = buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    private const val TAG_END = 0
    private const val TAG_STRING = 8
    private const val TAG_LIST = 9
    private const val TAG_COMPOUND = 10

    private fun ByteBuf.name(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeShort(bytes.size)
        writeBytes(bytes)
    }

    private fun ByteBuf.string(key: String, value: String) {
        writeByte(TAG_STRING)
        name(key)
        name(value)
    }
}
