package win.transgirls.nieve.systems.chat

import io.netty.buffer.ByteBuf
import win.transgirls.nieve.systems.plugin.SessionView
import win.transgirls.nieve.tools.Colors
import win.transgirls.nieve.tools.Log
import win.transgirls.nieve.tools.readString
import win.transgirls.nieve.tools.readVarInt

object Viewer {
    @Volatile
    var watching: SessionView? = null

    fun feed(session: SessionView, buf: ByteBuf) {
        if (watching !== session) return
        val view = buf.duplicate()
        val name = session.ids.playClientbound[view.readVarInt()] ?: return
        if (name !in MESSAGES) return
        val text = try {
            when (name) {
                "system_chat", "set_action_bar_text" -> Components.read(view)
                "disguised_chat" -> disguised(view)
                else -> player(view)
            }
        } catch (_: Exception) {
            "${Colors.GRAY}unreadable $name packet${Colors.RESET}"
        }
        if (text.isNotBlank()) Log.out("${Colors.BLUE}${session.label}${Colors.RESET} $text")
    }

    private fun disguised(buf: ByteBuf): String {
        val message = Components.read(buf)
        buf.readVarInt()
        return "<${Components.read(buf)}> $message"
    }

    private fun player(buf: ByteBuf): String {
        buf.readVarInt()
        buf.skipBytes(16)
        buf.readVarInt()
        if (buf.readBoolean()) buf.skipBytes(SIGNATURE)
        val content = buf.readString()
        buf.skipBytes(16)
        repeat(buf.readVarInt()) { if (buf.readVarInt() == 0) buf.skipBytes(SIGNATURE) }
        val unsigned = if (buf.readBoolean()) Components.read(buf) else null
        if (buf.readVarInt() == PARTIALLY_FILTERED) repeat(buf.readVarInt()) { buf.readLong() }
        buf.readVarInt()
        return "<${Components.read(buf)}> ${unsigned ?: Components.legacy(content)}"
    }

    private const val SIGNATURE = 256
    private const val PARTIALLY_FILTERED = 2
    private val MESSAGES = setOf("system_chat", "player_chat", "disguised_chat", "set_action_bar_text")
}
