package win.transgirls.nieve.systems.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import win.transgirls.cryostasis.shared.Protocol
import win.transgirls.nieve.systems.network.Pipelines.enableCompression
import win.transgirls.nieve.systems.session.Detached
import win.transgirls.nieve.systems.session.Refusal
import win.transgirls.nieve.systems.session.Session
import win.transgirls.nieve.systems.session.Sessions
import win.transgirls.nieve.tools.Log
import win.transgirls.nieve.tools.Text
import win.transgirls.nieve.tools.packet
import win.transgirls.nieve.tools.peekVarInt
import win.transgirls.nieve.tools.readString
import win.transgirls.nieve.tools.readUuid
import win.transgirls.nieve.tools.readVarInt
import win.transgirls.nieve.tools.writeString
import win.transgirls.nieve.tools.writeVarInt
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private enum class Intent(val id: Int) {
    STATUS(1),
    LOGIN(2),
    TRANSFER(3);

    override fun toString() = name.lowercase()

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id }
    }
}

class DownstreamHandler : SimpleChannelInboundHandler<ByteBuf>() {
    private enum class State { HANDSHAKE, STATUS, LOGIN_START, RELAY, REJECTED }

    private var state = State.HANDSHAKE
    private var protocol = 0
    private var session: Session? = null
    private var subscriber = false
    private var attachTo = Protocol.NEW_SESSION

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) = when (state) {
        State.HANDSHAKE -> handshake(ctx, msg)
        State.STATUS -> status(ctx, msg)
        State.LOGIN_START -> loginStart(ctx, msg)
        State.RELAY -> relay(ctx, msg)
        State.REJECTED -> Unit
    }

    private fun handshake(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (msg.readVarInt() != PacketIds.HANDSHAKE) return close(ctx)
        protocol = msg.readVarInt()
        val address = msg.readString()
        val port = msg.readUnsignedShort()
        val requested = msg.readVarInt()
        val intent = Intent.of(requested)
        val remote = ctx.channel().remoteAddress()

        when (intent) {
            Intent.STATUS -> {
                state = State.STATUS
                subscriber = Protocol.decodeRoute(address) != null
                Log.info(if (subscriber) "link" else "ping", "from $remote protocol=$protocol")
            }

            Intent.LOGIN, Intent.TRANSFER -> {
                Log.info("handshake", "from $remote: intent=$intent protocol=$protocol")
                val route = Protocol.decodeRoute(address)
                    ?: return reject(ctx, "This server requires you to connect with Cryostasis.")
                if (route.version != Protocol.VERSION) {
                    return reject(
                        ctx,
                        "Version mismatch (Nieve is on ${Protocol.VERSION}, Cryostasis is on ${route.version})."
                    )
                }
                val ids = PacketTable.forProtocol(protocol)
                    ?: return reject(ctx, "Protocol $protocol isn't supported by this instance.")
                attachTo = route.session
                session = Session(ctx.channel(), protocol, route.host, port, route.origin, ids)
                state = State.LOGIN_START
            }

            null -> {
                Log.info("handshake", "from $remote: intent=$requested protocol=$protocol")
                close(ctx)
            }
        }
    }

    private fun status(ctx: ChannelHandlerContext, msg: ByteBuf) {
        when (msg.readVarInt()) {
            PacketIds.STATUS_REQUEST -> {
                if (subscriber) Subscribers.add(ctx.channel())
                ctx.writeAndFlush(
                    packet(PacketIds.STATUS_REQUEST) {
                        writeString(
                            if (subscriber) {
                                Subscribers.snapshot()
                            } else {
                                Status.json(
                                    protocol
                                )
                            }
                        )
                    }
                )
            }

            PacketIds.STATUS_PING -> {
                val payload = msg.readLong()
                ctx.writeAndFlush(packet(PacketIds.STATUS_PING) { writeLong(payload) })
            }

            else -> close(ctx)
        }
    }

    private fun loginStart(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val s = session!!
        if (msg.readVarInt() != PacketIds.LOGIN_HELLO) return close(ctx)
        s.name = msg.readString()
        s.uuid = msg.readUuid()
        val existing = if (attachTo == Protocol.NEW_SESSION) null else Sessions.find(attachTo)
        if (attachTo != Protocol.NEW_SESSION && existing == null) return reject(ctx, Refusal.ENDED.message)
        if (existing != null && existing.protocol != protocol) return reject(ctx, Refusal.PROTOCOL.message)
        Log.info(
            "login",
            "${s.name} (${s.uuid}) -> ${existing?.let { "session #${it.id}" } ?: "${s.host}:${s.port}"}"
        )

        ctx.writeAndFlush(packet(PacketIds.SET_COMPRESSION) { writeVarInt(COMPRESSION_THRESHOLD) })
        ctx.pipeline().enableCompression(COMPRESSION_THRESHOLD)
        state = State.RELAY
        if (existing != null) {
            session = existing
            return existing.attach(ctx.channel(), s.uuid)
        }

        Sessions.add(s)
        s.connectUpstream()
    }

    private fun relay(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val s = session ?: return
        val buf = msg.retain()
        s.execute { s.fromClient(ctx.channel(), buf) }
    }

    private fun reject(ctx: ChannelHandlerContext, reason: String) {
        state = State.REJECTED
        Log.error("kick", "${ctx.channel().remoteAddress()} (login): $reason")
        ctx.writeAndFlush(
            packet(PacketIds.LOGIN_DISCONNECT) {
                writeString(Text.json(reason, "gray"))
            }
        )
            .addListener(ChannelFutureListener.CLOSE)
    }

    private fun close(ctx: ChannelHandlerContext) {
        ctx.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        Subscribers.remove(ctx.channel())
        session?.downstreamClosed(ctx.channel())
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is IOException) {
            ctx.close()
            return
        }
        Log.exception("error", "client ${ctx.channel().remoteAddress()}", cause)

        session?.kick("Unexpected error.\n${cause.message ?: cause.javaClass.simpleName}") ?: ctx.close()
    }

    companion object {
        const val COMPRESSION_THRESHOLD = 256
    }
}

class UpstreamHandler(private val session: Session) : SimpleChannelInboundHandler<ByteBuf>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (session.detached) return Detached.respond(session, msg)
        if (session.inLogin) {
            when (msg.peekVarInt()) {
                PacketIds.ENCRYPTION_REQUEST -> return session.handoff.onEncryptionRequest(msg.also { it.readVarInt() })
                PacketIds.SET_COMPRESSION -> return ctx.pipeline()
                    .enableCompression(msg.also { it.readVarInt() }.readVarInt())
            }
        }
        session.relayToClient(msg.retain())
    }

    override fun channelInactive(ctx: ChannelHandlerContext) = session.upstreamClosed()

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is IOException) {
            ctx.close()
            return
        }
        Log.exception("error", "server ${ctx.channel().remoteAddress()} (${session.phase})", cause)
        session.kick("Unexpected error talking to the server.\n${cause.message ?: cause.javaClass.simpleName}")
    }
}

object Status {
    fun json(protocol: Int): String {
        val sessions = Sessions.all()
        val detached = sessions.count { it.detached }
        val info = "${sessions.size} sessions, $detached detached, up ${Log.uptime()}"
        val root = JsonObject()
        root.add(
            "version",
            JsonObject().apply {
                addProperty("name", "Nieve")
                addProperty("protocol", protocol)
            }
        )
        root.add(
            "players",
            JsonObject().apply {
                addProperty("max", sessions.size)
                addProperty("online", sessions.size - detached)
            }
        )
        root.add("description", JsonParser.parseString(Text.motd(info)))
        return root.toString()
    }
}

object Subscribers {
    private val channels = ConcurrentHashMap.newKeySet<Channel>()

    fun add(channel: Channel) {
        channels.add(channel)
    }

    fun remove(channel: Channel) {
        channels.remove(channel)
    }

    fun snapshot(): String = JsonObject().apply {
        add(
            "snapshot",
            JsonObject().apply {
                addProperty("version", Protocol.VERSION)
                addProperty("uptime", Log.uptimeSeconds())
                add("sessions", JsonArray().apply { Sessions.all().forEach { add(it.toJson()) } })
            }
        )
    }.toString()

    fun updated(session: Session) = send(JsonObject().apply { add("session", session.toJson()) })

    fun removed(session: Session) = send(JsonObject().apply { addProperty("removed", session.id) })

    private fun send(message: JsonObject) {
        val text = message.toString()
        channels.forEach { channel ->
            channel.eventLoop()
                .execute { channel.writeAndFlush(packet(PacketIds.STATUS_REQUEST) { writeString(text) }) }
        }
    }
}
