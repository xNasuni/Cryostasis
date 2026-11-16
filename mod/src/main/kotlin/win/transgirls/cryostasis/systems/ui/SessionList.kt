package win.transgirls.cryostasis.systems.ui

import net.minecraft.SharedConstants
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import win.transgirls.cryostasis.Config
import win.transgirls.cryostasis.Cryostasis
import win.transgirls.cryostasis.Cryostasis.Companion.mc
import win.transgirls.cryostasis.shared.Protocol
import win.transgirls.cryostasis.systems.network.NieveLink
import win.transgirls.cryostasis.systems.network.Routing
import win.transgirls.cryostasis.tools.duration
import win.transgirls.cryostasis.types.NieveSession
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object NieveSessions {
    private var seen = -1
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
            NieveSessionEntry(widget, screen, session, saved.firstOrNull { matches(it, session) })
        }
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
        info.name = if (Config.remote) "remote" else "local"
        info.address = "${Cryostasis.nieveHost}:${Cryostasis.nievePort}"
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

    @JvmStatic
    fun info(session: NieveSession, saved: ServerInfo?): ServerInfo {
        val info = ServerInfo("", "nieve-session-${session.id}", ServerInfo.ServerType.OTHER)
        info.status = if (session.ping >= 0) ServerInfo.Status.SUCCESSFUL else ServerInfo.Status.PINGING
        info.ping = session.ping.toLong()
        info.favicon = saved?.favicon
        info.label = saved?.label ?: location(session)
        info.playerCountLabel = Text.literal(session.players.size.toString()).formatted(Formatting.GRAY)
            .append(Text.literal("/").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(session.max.toString()).formatted(Formatting.GRAY))
        info.playerListSummary = names(session)
        info.version = Text.literal("Nieve")
        info.protocolVersion = SharedConstants.getProtocolVersion()
        return info
    }

    @JvmStatic
    fun title(session: NieveSession): Text {
        val age = session.uptime + (System.currentTimeMillis() - session.at) / 1000
        val state = if (session.detached) "detached" else "attached"
        return Text.literal("Nieve ").styled { it.withColor(ACCENT_RGB) }
            .append(
                Text.literal("$state up ${duration(age)} ${session.name}")
                    .styled { it.withColor(if (session.detached) GRAY_RGB else GREEN_RGB) }
            )
    }

    @JvmStatic
    fun attach(session: NieveSession, saved: ServerInfo?) {
        val screen = mc.guiManager.currentScreen as? MultiplayerScreen ?: return
        Routing.attachTo(session.id)
        screen.connect(saved ?: ServerInfo(session.origin, session.origin, ServerInfo.ServerType.OTHER))
    }

    private fun location(session: NieveSession): Text {
        val origin = ServerAddress.parse(session.origin)
        val resolved = "${session.host}:${session.port}"
        return Text.literal(
            if (resolved.equals(
                    "${origin.address}:${origin.port}",
                    true
                )
            ) {
                resolved
            } else {
                "$resolved\n${session.origin}"
            }
        )
    }

    private fun names(session: NieveSession): List<Text> {
        val shown = session.players.take(TOOLTIP_PLAYERS).map { Text.literal(it) as Text }
        val hidden = session.players.size - shown.size
        return if (hidden > 0) shown + Text.literal("... and $hidden more") else shown
    }

    private fun matches(server: ServerInfo, session: NieveSession): Boolean {
        val saved = ServerAddress.parse(server.address)
        val origin = ServerAddress.parse(session.origin)
        return saved.address.equals(origin.address, true) && saved.port == origin.port
    }
}

private const val RED = 0xFF0087
private const val ACCENT_RGB = 0xAFFFFF
private const val GRAY_RGB = 0x808080
private const val GREEN_RGB = 0x55FF55
private const val TOOLTIP_PLAYERS = 12
