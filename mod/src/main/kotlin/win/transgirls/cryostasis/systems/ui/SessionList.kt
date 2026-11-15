package win.transgirls.cryostasis.systems.ui

import net.minecraft.SharedConstants
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget
import net.minecraft.client.gui.screen.world.WorldIcon
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo
import net.minecraft.client.render.RenderPipelines
import net.minecraft.client.texture.NativeImage
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import win.transgirls.cryostasis.Cryostasis
import win.transgirls.cryostasis.Cryostasis.Companion.mc
import win.transgirls.cryostasis.shared.Protocol
import win.transgirls.cryostasis.systems.network.NieveLink
import win.transgirls.cryostasis.tools.duration
import win.transgirls.cryostasis.types.NieveSession
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object NieveSessions {
    private var seen = -1
    private val cache = HashMap<Int, NieveSessionEntry>()
    private val info by lazy {
        ServerInfo(
            "local",
            "${Cryostasis.nieveHost}:${Cryostasis.nievePort}",
            ServerInfo.ServerType.OTHER
        ).also { it.favicon = icon() }
    }

    @JvmStatic
    fun tick(rebuild: Runnable) {
        if (seen == NieveLink.version) return
        seen = NieveLink.version
        rebuild.run()
    }

    @JvmStatic
    fun withSessions(
        widget: MultiplayerServerListWidget,
        screen: MultiplayerScreen,
        entries: Collection<MultiplayerServerListWidget.Entry>
    ): Collection<MultiplayerServerListWidget.Entry> {
        if (!Cryostasis.enabled) return entries
        val nieve = NieveServerEntry(widget, screen, describe())
        if (NieveLink.failed) return listOf<MultiplayerServerListWidget.Entry>(nieve) + entries

        val saved = entries.filterIsInstance<MultiplayerServerListWidget.ServerEntry>().map { it.server }

        val current = NieveLink.sessions.map { session ->
            val server = saved.firstOrNull { matches(it, session) }
            cache.getOrPut(session.id) { NieveSessionEntry(session, session.at, server) }
                .also { it.update(session, session.at, server) }
        }

        val live = current.map { it.session.id }.toSet()
        cache.entries.removeAll { (id, entry) -> (id !in live).also { gone -> if (gone) entry.close() } }
        return current + nieve + entries
    }

    @JvmStatic
    fun refresh() {
        describe()
    }

    private fun describe(): ServerInfo {
        val reachable = NieveLink.online && !NieveLink.failed
        info.status = when {
            reachable -> ServerInfo.Status.SUCCESSFUL
            NieveLink.online -> ServerInfo.Status.INCOMPATIBLE
            else -> ServerInfo.Status.UNREACHABLE
        }
        info.ping = NieveLink.latency
        info.label = if (reachable) motd() else problem()
        info.playerCountLabel = if (reachable) players() else Text.empty()
        info.version = Text.literal("Nieve")
        info.protocolVersion = SharedConstants.getProtocolVersion()
        return info
    }

    private fun motd(): Text {
        val sessions = NieveLink.sessions
        val details =
            "${sessions.size} sessions, ${sessions.count { it.detached }} detached, up ${duration(NieveLink.uptime())}"
        return Text.literal("A Nieve Server").styled { it.withColor(ACCENT_RGB) }.append("\n")
            .append(Text.literal(details).formatted(Formatting.GRAY))
    }

    private fun players(): Text {
        val sessions = NieveLink.sessions
        return Text.literal("${sessions.count { !it.detached }}").formatted(Formatting.GRAY)
            .append(Text.literal("/").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("${sessions.size}").formatted(Formatting.GRAY))
    }

    private fun problem(): Text {
        val mismatch = NieveLink.mismatch
        val title = if (mismatch != 0) "Version mismatch" else "Nieve isn't running"
        val detail =
            if (mismatch != 0) "Nieve is on $mismatch, but Cryostasis is on ${Protocol.VERSION}" else "Can't reach ${Cryostasis.nieveHost}:${Cryostasis.nievePort}"
        return Text.literal(title).styled { it.withColor(RED) }.append("\n")
            .append(Text.literal(detail).formatted(Formatting.DARK_GRAY))
    }

    private fun icon(): ByteArray {
        val source = ImageIO.read(Cryostasis::class.java.getResourceAsStream("/assets/cryostasis/icon.png"))
        val scaled = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        graphics.drawImage(source, 0, 0, 64, 64, null)
        graphics.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(scaled, "png", it) }.toByteArray()
    }

    private fun matches(server: ServerInfo, session: NieveSession): Boolean {
        val saved = ServerAddress.parse(server.address)
        val origin = ServerAddress.parse(session.origin)
        return saved.address.equals(origin.address, true) && saved.port == origin.port
    }
}

class NieveSessionEntry(session: NieveSession, private var fetchedAt: Long, private var server: ServerInfo?) : MultiplayerServerListWidget.Entry() {
    var session = session
        private set

    private val icon = WorldIcon.forServer(mc.textureManager, "cryostasis-${session.id}")
    private var iconBytes: ByteArray? = null

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float) {
        updateIcon()
        val font = mc.textRenderer
        val x = contentX + TEXT_OFFSET
        val age = session.uptime + (System.currentTimeMillis() - fetchedAt) / 1000
        val state = if (session.detached) "detached" else "attached"
        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            icon.textureId,
            contentX,
            contentY,
            0f,
            0f,
            ICON,
            ICON,
            ICON,
            ICON
        )
        context.drawTextWithShadow(font, "Nieve", x, contentY + 1, ACCENT)
        context.drawTextWithShadow(
            font,
            "$state up ${duration(age)} ${session.name}",
            x + font.getWidth("Nieve "),
            contentY + 1,
            if (session.detached) GRAY else GREEN
        )
        val motd = server?.label?.let { font.wrapLines(it, contentWidth - TEXT_OFFSET) }.orEmpty()
        if (motd.isEmpty()) {
            val origin = ServerAddress.parse(session.origin)
            val resolved = "${session.host}:${session.port}"
            context.drawTextWithShadow(font, resolved, x, contentY + 12, GRAY)
            if (!resolved.equals("${origin.address}:${origin.port}", true)) {
                context.drawTextWithShadow(font, session.origin, x, contentY + 12 + font.fontHeight, GRAY)
            }
        } else {
            motd.take(2).forEachIndexed { line, text ->
                context.drawTextWithShadow(
                    font,
                    text,
                    x,
                    contentY + 12 + line * font.fontHeight,
                    GRAY
                )
            }
        }
    }

    fun update(session: NieveSession, fetchedAt: Long, server: ServerInfo?) {
        this.session = session
        this.fetchedAt = fetchedAt
        this.server = server
    }

    private fun updateIcon() {
        val favicon = server?.favicon
        if (favicon.contentEquals(iconBytes)) return
        iconBytes = favicon
        icon.destroy()
        if (favicon != null) {
            try {
                icon.load(NativeImage.read(favicon))
            } catch (_: Exception) {
            }
        }
    }

    override fun getNarration(): Text = Text.literal("Nieve session ${session.name} on ${session.origin}")

    override fun isOfSameType(entry: MultiplayerServerListWidget.Entry) = entry is NieveSessionEntry && entry.session.id == session.id

    override fun connect() {
        val screen = mc.guiManager.currentScreen as? MultiplayerScreen ?: return
        screen.connect(
            server ?: ServerInfo(
                session.origin,
                session.origin,
                ServerInfo.ServerType.OTHER
            )
        )
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (doubled) connect()
        return super.mouseClicked(click, doubled)
    }

    override fun close() = icon.close()

    companion object {
        private const val ICON = 32
        private const val TEXT_OFFSET = 35
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val GRAY = 0xFF808080.toInt()
        private const val GREEN = 0xFF55FF55.toInt()
    }
}

private const val ACCENT = 0xFFAFFFFF.toInt()

private const val RED = 0xFF0087
private const val ACCENT_RGB = 0xAFFFFF
