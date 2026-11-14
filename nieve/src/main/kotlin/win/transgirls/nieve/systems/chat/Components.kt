package win.transgirls.nieve.systems.chat

import io.netty.buffer.ByteBuf

object Components {
    private const val ESC = "\u001b["
    private const val RESET = "${ESC}0m"
    private const val SECTION = '§'

    private val names = listOf(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    )

    // TODO: use ;38;5 or something else always instead of regular code for the colors cause I'm pretty sure its forcing boldness when its a bright range??
    private val ansi = listOf(30, 34, 32, 36, 31, 35, 33, 37, 90, 94, 92, 96, 91, 95, 93, 97)
    private val colors = names.zip(ansi).toMap()

    // probably use actual translation later
    private val translations = mapOf(
        "chat.type.text" to "<%s> %s",
        "chat.type.announcement" to "[%s] %s",
        "chat.type.emote" to "* %s %s",
        "multiplayer.player.joined" to "%s joined the game",
        "multiplayer.player.left" to "%s left the game"
    )

    private data class Style(
        val color: String? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underlined: Boolean = false,
        val strikethrough: Boolean = false,
        val obfuscated: Boolean = false
    ) {
        fun merge(tag: Map<*, *>) = Style(
            tag["color"] as? String ?: color,
            flag(tag["bold"], bold),
            flag(tag["italic"], italic),
            flag(tag["underlined"], underlined),
            flag(tag["strikethrough"], strikethrough),
            flag(tag["obfuscated"], obfuscated)
        )

        fun codes() = buildString {
            append(RESET)
            color?.let { append(colorCode(it)) }
            if (bold) append("${ESC}1m")
            if (italic) append("${ESC}3m")
            if (underlined) append("${ESC}4m")
            if (strikethrough) append("${ESC}9m")
        }

        private fun flag(value: Any?, fallback: Boolean) = if (value is Byte) value.toInt() != 0 else fallback
    }

    fun read(buf: ByteBuf): String {
        val out = StringBuilder()
        render(tag(buf, buf.readByte().toInt()), Style(), out)
        return out.append(RESET).toString()
    }

    fun legacy(text: String): String {
        val out = StringBuilder()
        emit(text, Style(), out)
        return out.append(RESET).toString()
    }

    private fun colorCode(color: String): String {
        if (color.startsWith("#") && color.length == 7) {
            val rgb = color.drop(1).toInt(16)
            return "${ESC}38;2;${rgb shr 16 and 0xFF};${rgb shr 8 and 0xFF};${rgb and 0xFF}m"
        }
        return "$ESC${colors[color] ?: 37}m"
    }

    private fun render(tag: Any?, parent: Style, out: StringBuilder) {
        when (tag) {
            null -> {}
            is String -> emit(tag, parent, out)
            is List<*> -> tag.forEach { render(it, parent, out) }
            is Map<*, *> -> compound(tag, parent.merge(tag), out)
            else -> emit(tag.toString(), parent, out)
        }
    }

    private fun compound(tag: Map<*, *>, style: Style, out: StringBuilder) {
        (tag[""] as? String)?.let { emit(it, style, out) }
        tag["text"]?.let { emit(it.toString(), style, out) }
        tag["translate"]?.let { translate(it.toString(), tag, style, out) }
        (tag["keybind"] ?: tag["selector"])?.let { emit(it.toString(), style, out) }
        tag["extra"]?.let { render(it, style, out) }
    }

    private fun translate(key: String, tag: Map<*, *>, style: Style, out: StringBuilder) {
        val arguments = tag["with"] as? List<*> ?: emptyList<Any?>()
        val pattern = translations[key] ?: tag["fallback"] as? String
        if (pattern == null) {
            emit(key, style, out)
            if (arguments.isNotEmpty()) {
                emit("[", style, out)
                arguments.forEachIndexed { index, argument ->
                    if (index > 0) emit(", ", style, out)
                    render(argument, style, out)
                }
                emit("]", style, out)
            }
            return
        }
        var next = 0
        var last = 0
        for (match in Regex("%(?:(\\d+)\\$)?s").findAll(pattern)) {
            emit(pattern.substring(last, match.range.first), style, out)
            val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: next++
            render(arguments.getOrNull(index), style, out)
            last = match.range.last + 1
        }
        emit(pattern.substring(last), style, out)
    }

    private fun emit(text: String, base: Style, out: StringBuilder) {
        var style = base
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isEmpty()) return
            out.append(style.codes()).append(buffer)
            buffer.clear()
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != SECTION || i + 1 >= text.length) {
                buffer.append(if (style.obfuscated && !c.isWhitespace()) '?' else c)
                i++
                continue
            }
            flush()
            val code = text[i + 1].lowercaseChar()
            i += 2
            style = when {
                code == 'x' && i + 12 <= text.length -> {
                    val hex = (0 until 6).map { text[i + it * 2 + 1] }.joinToString("")
                    i += 12
                    Style(color = "#$hex")
                }

                code in '0'..'9' || code in 'a'..'f' -> Style(color = names[code.digitToInt(16)])
                code == 'k' -> style.copy(obfuscated = true)
                code == 'l' -> style.copy(bold = true)
                code == 'm' -> style.copy(strikethrough = true)
                code == 'n' -> style.copy(underlined = true)
                code == 'o' -> style.copy(italic = true)
                code == 'r' -> Style()
                else -> style
            }
        }
        flush()
    }

    private fun tag(buf: ByteBuf, type: Int): Any? = when (type) {
        BYTE -> buf.readByte()
        SHORT -> buf.readShort()
        INT -> buf.readInt()
        LONG -> buf.readLong()
        FLOAT -> buf.readFloat()
        DOUBLE -> buf.readDouble()
        BYTE_ARRAY -> buf.skipBytes(buf.readInt()).let { null }
        STRING -> string(buf)
        LIST -> {
            val elements = buf.readByte().toInt()
            List(buf.readInt()) { tag(buf, elements) }
        }

        COMPOUND -> {
            val map = HashMap<String, Any?>()
            while (true) {
                val next = buf.readByte().toInt()
                if (next == END) break
                map[string(buf)] = tag(buf, next)
            }
            map
        }

        INT_ARRAY -> buf.skipBytes(buf.readInt() * 4).let { null }
        LONG_ARRAY -> buf.skipBytes(buf.readInt() * 8).let { null }
        else -> error("Unknown NBT tag $type")
    }

    private fun string(buf: ByteBuf): String {
        val length = buf.readUnsignedShort()
        return buf.readCharSequence(length, Charsets.UTF_8).toString()
    }

    private const val END = 0
    private const val BYTE = 1
    private const val SHORT = 2
    private const val INT = 3
    private const val LONG = 4
    private const val FLOAT = 5
    private const val DOUBLE = 6
    private const val BYTE_ARRAY = 7
    private const val STRING = 8
    private const val LIST = 9
    private const val COMPOUND = 10
    private const val INT_ARRAY = 11
    private const val LONG_ARRAY = 12
}
