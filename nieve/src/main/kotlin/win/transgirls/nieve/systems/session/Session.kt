package win.transgirls.nieve.systems.session

import com.google.gson.JsonObject
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.ReferenceCountUtil
import win.transgirls.cryostasis.shared.Protocol
import win.transgirls.nieve.systems.chat.ChatSignatures
import win.transgirls.nieve.systems.chat.Components
import win.transgirls.nieve.systems.chat.Viewer
import win.transgirls.nieve.systems.network.CipherDecoder
import win.transgirls.nieve.systems.network.CipherEncoder
import win.transgirls.nieve.systems.network.Encryption
import win.transgirls.nieve.systems.network.FrameDecoder
import win.transgirls.nieve.systems.network.FrameEncoder
import win.transgirls.nieve.systems.network.PacketIds
import win.transgirls.nieve.systems.network.PacketTable
import win.transgirls.nieve.systems.network.Pipelines
import win.transgirls.nieve.systems.network.Subscribers
import win.transgirls.nieve.systems.network.UpstreamHandler
import win.transgirls.nieve.systems.plugin.Direction
import win.transgirls.nieve.systems.plugin.Plugins
import win.transgirls.nieve.systems.plugin.SessionView
import win.transgirls.nieve.tools.Log
import win.transgirls.nieve.tools.Text
import win.transgirls.nieve.tools.packet
import win.transgirls.nieve.tools.peekVarInt
import win.transgirls.nieve.tools.readByteArray
import win.transgirls.nieve.tools.readString
import win.transgirls.nieve.tools.readVarInt
import win.transgirls.nieve.tools.writeByteArray
import win.transgirls.nieve.tools.writeString
import win.transgirls.nieve.tools.writeUuid
import win.transgirls.nieve.tools.writeVarInt
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher

enum class Refusal(val message: String) {
    ENDED("This session has ended."),
    ALREADY_ATTACHED("You're already attached to this session."),
    ANOTHER_CLIENT("Another client is attached to this session."),
    PROTOCOL("This session runs on a different Minecraft version."),
    BUSY("Nieve is busy right now, try again later.")
}

enum class Phase {
    LOGIN,
    CONFIGURATION,
    PLAY;

    override fun toString() = name.lowercase()
}

class Session(
    downstream: Channel,
    override val protocol: Int,
    override val host: String,
    override val port: Int,
    val origin: String,
    override val ids: PacketTable
) : SessionView {
    @Volatile
    var downstream = downstream
        private set

    @Volatile
    var upstream: Channel? = null

    var phase = Phase.LOGIN
        private set

    override lateinit var name: String
    override lateinit var uuid: UUID
    override val attributes = ConcurrentHashMap<String, Any>()

    @Volatile
    override var detached = false
        private set

    var id = 0
        internal set

    val createdAt = System.nanoTime()

    val handoff = Handoff(this)
    val snapshot = Snapshot(ids)
    val mirror = Mirror(ids, protocol)
    val signatures = ChatSignatures()
    val inLogin get() = phase == Phase.LOGIN && !loginFinished

    private var loginFinished = false
    private var disconnectRelayed = false
    private var closed = false
    private var heartbeat: ScheduledFuture<*>? = null
    private var attachChannel: Channel? = null
    private var attachStage = AttachStage.NONE
    private var swallowTeleport = false
    private var swallowBatches = 0
    private var swallowLoaded = false
    private var swallowTeleportMove = false
    private var reconfiguring = false
    private var lastActivity = System.nanoTime()
    private var ticking = false
    private var transferred = false
    private var chatIndex = 0
    private var rewriteChat = false
    private var watchdog: ScheduledFuture<*>? = null

    override val label get() = if (::name.isInitialized) name else "unknown"

    private val disconnectId
        get() = when (phase) {
            Phase.LOGIN -> PacketIds.LOGIN_DISCONNECT
            Phase.CONFIGURATION -> ids.configDisconnect
            Phase.PLAY -> ids.playDisconnect
        }

    fun toJson() = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("host", host)
        addProperty("port", port)
        addProperty("origin", origin)
        addProperty("state", if (detached) "detached" else "attached")
        addProperty("uptime", (System.nanoTime() - createdAt) / 1_000_000_000L)
    }

    private fun connectsToSelf(): Boolean {
        val local = downstream.localAddress() as? InetSocketAddress ?: return false
        val loopback = host.equals(
            "localhost",
            true
        ) || runCatching { InetAddress.getByName(host).isLoopbackAddress }.getOrDefault(false)
        return port == local.port && loopback
    }

    fun connectUpstream() {
        if (connectsToSelf()) return kick("Nieve can't connect to itself.")
        Bootstrap()
            .group(downstream.eventLoop())
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline()
                        .addLast(Pipelines.FRAME_DECODER, FrameDecoder())
                        .addLast(Pipelines.FRAME_ENCODER, FrameEncoder())
                        .addLast(Pipelines.HANDLER, UpstreamHandler(this@Session))
                }
            })
            .connect(host, port)
            .addListener(
                ChannelFutureListener { f ->
                    if (!f.isSuccess) return@ChannelFutureListener kick("Could not connect to $host:$port\n${f.cause()?.message ?: "Unknown error"}")
                    upstream = f.channel()
                    watchdog = f.channel().eventLoop().scheduleAtFixedRate(::checkActivity, 1, 1, TimeUnit.SECONDS)
                    Log.success("session", "$name: connected to $host:$port")
                    f.channel().write(
                        packet(PacketIds.HANDSHAKE) {
                            writeVarInt(protocol)
                            writeString(host)
                            writeShort(port)
                            writeVarInt(LOGIN_INTENT)
                        }
                    )
                    f.channel().writeAndFlush(
                        packet(PacketIds.LOGIN_HELLO) {
                            writeString(name)
                            writeUuid(uuid)
                        }
                    )
                }
            )
    }

    fun relayToClient(received: ByteBuf) {
        var buf = received
        val id = buf.peekVarInt()
        if (phase == Phase.PLAY && id == ids.playCustomPayloadClientbound) {
            buf =
                Plugins.dispatch(this, Direction.CLIENTBOUND, buf) ?: return
        }
        if (inLogin && id == PacketIds.LOGIN_SUCCESS) loginFinished = true
        if (id == disconnectId) disconnectRelayed = true
        if (phase == Phase.PLAY && id == ids.playKeepAlive) {
            val challenge = buf.duplicate().also { it.readVarInt() }.readLong()
            upstream?.writeAndFlush(packet(ids.playKeepAliveResponse) { writeLong(challenge) })
        }
        if (phase == Phase.PLAY) {
            if (id == ids.playPlayerChat) {
                if (rewriteChat) buf = replace(buf, signatures.rewrite(buf, chatIndex++))
                signatures.record(buf)
            } else if (rewriteChat && id == ids.playDeleteChat) {
                buf = replace(buf, signatures.expandDeleted(buf))
            }
            mirror.clientbound(buf)
            Viewer.feed(this, buf)
            if (id == ids.playTransfer) transferred = true
            if (id == ids.playStartConfiguration) {
                reconfiguring = true
                clearState()
            }
        }
        snapshot.record(phase, buf)
        downstream.writeAndFlush(buf)
    }

    fun relayToServer(received: ByteBuf) {
        var buf = received
        val server = upstream
        if (server == null) {
            ReferenceCountUtil.release(buf)
            return
        }
        val id = buf.peekVarInt()
        if (phase == Phase.PLAY) {
            if (swallowed(id, buf)) {
                ReferenceCountUtil.release(buf)
                return
            }
            if (id == ids.playKeepAliveResponse) {
                ReferenceCountUtil.release(buf)
                return
            }
            mirror.serverbound(buf)
            if (id == ids.playClientTickEnd) {
                ticking = true
                lastActivity = System.nanoTime()
            }
        }
        if (phase == Phase.PLAY && id == ids.playCustomPayload) {
            if (isLeave(buf)) {
                ReferenceCountUtil.release(buf)
                return leave()
            }
            buf = Plugins.dispatch(this, Direction.SERVERBOUND, buf) ?: return
        }
        when (phase) {
            Phase.LOGIN if loginFinished && id == PacketIds.LOGIN_ACKNOWLEDGED -> phase = Phase.CONFIGURATION
            Phase.PLAY if reconfiguring && id == ids.playConfigurationAcknowledged -> {
                reconfiguring = false
                phase = Phase.CONFIGURATION
            }

            Phase.CONFIGURATION if id == ids.configFinish -> phase = Phase.PLAY

            else -> Unit
        }
        server.writeAndFlush(buf)
    }

    fun kick(reason: String, color: String? = "gray") {
        if (closed) return
        finish()
        Log.error("kick", "$label ($phase): ${reason.replace('\n', ' ')}")
        downstream.writeAndFlush(disconnectPacket(disconnectId, reason, color))
            .addListener(ChannelFutureListener.CLOSE)
        upstream?.close()
    }

    fun downstreamClosed(channel: Channel) = execute {
        when {
            channel === attachChannel -> {
                attachChannel = null
                attachStage = AttachStage.NONE
            }

            channel === downstream -> downstreamClosed()
        }
    }

    private fun downstreamClosed() {
        if (closed) return
        if (transferred) return end("client transferred to another server")
        if (phase == Phase.PLAY && upstream?.isActive == true) {
            detached = true
            startHeartbeat()
            Subscribers.updated(this)
            Plugins.detached(this)
            return Log.success("session", "$label: detached")
        }
        end("client disconnected ($phase)")
    }

    fun serverEndedDetached(reason: String) = end("server ended the detached session: $reason")

    fun execute(task: () -> Unit) {
        val loop = upstream?.eventLoop() ?: downstream.eventLoop()
        if (loop.inEventLoop()) task() else loop.execute(task)
    }

    fun fromClient(channel: Channel, buf: ByteBuf) {
        when {
            channel === attachChannel -> attachPacket(buf)
            channel !== downstream -> ReferenceCountUtil.release(buf)
            else -> clientPacket(buf)
        }
    }

    private fun checkActivity() {
        if (closed || detached || !ticking || phase != Phase.PLAY || attachChannel != null) return
        val now = System.nanoTime()
        if (now - lastActivity < INACTIVITY_NANOS) return
        lastActivity = now
        Log.info("session", "$label: client inactive, detaching")
        val parts = listOf(Text.Part("Timed out", "white"), Text.Part(" (detached safely)", Text.ACCENT))
        val channel = downstream
        channel.writeAndFlush(disconnectPacket(ids.playDisconnect, parts))
        channel.eventLoop().schedule({ channel.close() }, CLOSE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun clientPacket(buf: ByteBuf) {
        if (inLogin && handoff.waiting && buf.peekVarInt() == PacketIds.LOGIN_QUERY_RESPONSE) {
            val answer = buf.duplicate().also { it.readVarInt() }
            if (answer.readVarInt() == Handoff.QUERY_ID) {
                handoff.onAuthResponse(answer)
                ReferenceCountUtil.release(buf)
                return
            }
        }
        relayToServer(buf)
    }

    private fun swallowed(id: Int, buf: ByteBuf): Boolean {
        when {
            swallowTeleport && id == ids.playAcceptTeleport && buf.duplicate().also { it.readVarInt() }
                .readVarInt() == Mirror.TELEPORT_ID -> {
                swallowTeleport = false
                swallowTeleportMove = mirror.answersTeleportWithMove
            }

            swallowTeleportMove && id == ids.playMovePlayerPosRot -> swallowTeleportMove = false

            swallowBatches > 0 && id == ids.playChunkBatchReceived -> swallowBatches--
            swallowLoaded && id == ids.playPlayerLoaded -> swallowLoaded = false
            else -> return false
        }
        return true
    }

    fun attach(channel: Channel, account: UUID) = execute {
        val problem = when {
            closed -> Refusal.ENDED
            !detached -> if (account == uuid) Refusal.ALREADY_ATTACHED else Refusal.ANOTHER_CLIENT
            attachChannel != null -> Refusal.ANOTHER_CLIENT
            phase != Phase.PLAY || snapshot.login == null || snapshot.loginSuccess == null -> Refusal.BUSY
            else -> null
        }
        if (problem != null) {
            Log.error("kick", "${channel.remoteAddress()} (login): $label: ${problem.message}")
            return@execute reject(channel, problem.message)
        }
        attachChannel = channel
        attachStage = AttachStage.LOGIN
        Log.info("session", "$label: attaching")
        channel.writeAndFlush(wrap(snapshot.loginSuccess!!))
    }

    private fun attachPacket(buf: ByteBuf) {
        val id = buf.peekVarInt()
        ReferenceCountUtil.release(buf)
        val channel = attachChannel ?: return
        when (attachStage) {
            AttachStage.LOGIN if id == PacketIds.LOGIN_ACKNOWLEDGED -> {
                attachStage = AttachStage.CONFIGURATION
                snapshot.configuration.forEach { channel.write(wrap(it)) }
                channel.writeAndFlush(packet(ids.configFinishClientbound) {})
            }

            AttachStage.CONFIGURATION if id == ids.configFinish -> completeAttach(channel)

            else -> Unit
        }
    }

    private fun completeAttach(channel: Channel) {
        if (phase != Phase.PLAY || closed) {
            return reject(
                channel,
                Refusal.BUSY.message,
                configuration = true
            )
        }
        attachChannel = null
        attachStage = AttachStage.NONE
        downstream = channel
        heartbeat?.cancel(false)
        heartbeat = null
        detached = false
        lastActivity = System.nanoTime()
        ticking = false
        chatIndex = 0
        rewriteChat = true
        swallowTeleport = true
        swallowTeleportMove = false
        swallowLoaded = true
        swallowBatches = 1
        channel.write(wrap(snapshot.login!!))
        mirror.replay().forEach { channel.write(wrap(it)) }
        channel.flush()
        Subscribers.updated(this)
        Plugins.attached(this)
        Log.success("session", "$label: attached, ${mirror.chunkCount} chunks, ${mirror.entityCount} entities")
    }

    private fun reject(channel: Channel, reason: String, configuration: Boolean = false) {
        val message = disconnectPacket(if (configuration) ids.configDisconnect else PacketIds.LOGIN_DISCONNECT, reason)
        channel.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE)
        if (channel === attachChannel) {
            attachChannel = null
            attachStage = AttachStage.NONE
        }
    }

    private fun replace(old: ByteBuf, new: ByteBuf): ByteBuf {
        ReferenceCountUtil.release(old)
        return new
    }

    override fun say(message: String) = execute {
        val server = upstream ?: return@execute
        if (phase != Phase.PLAY) return@execute Log.fail("$label isn't in the game")
        if (message.startsWith("/")) {
            server.writeAndFlush(packet(ids.serverbound("chat_command")) { writeString(message.drop(1)) })
        } else {
            server.writeAndFlush(
                packet(ids.serverbound("chat")) {
                    writeString(message)
                    writeLong(System.currentTimeMillis())
                    writeLong(ThreadLocalRandom.current().nextLong())
                    writeBoolean(false)
                    writeVarInt(0)
                    writeBytes(ByteArray(3))
                    writeByte(0)
                }
            )
        }
    }

    override fun sendToClient(channel: String, data: ByteArray) = execute {
        if (!detached && attachChannel == null) {
            downstream.writeAndFlush(
                customPayload(
                    ids.playCustomPayloadClientbound,
                    channel,
                    data
                )
            )
        }
    }

    override fun sendToServer(channel: String, data: ByteArray) = execute {
        upstream?.writeAndFlush(customPayload(ids.playCustomPayload, channel, data))
    }

    private fun customPayload(id: Int, channel: String, data: ByteArray) = packet(id) {
        writeString(channel)
        writeBytes(data)
    }

    private fun disconnectPacket(id: Int, reason: String, color: String? = "gray") = disconnectPacket(id, listOf(Text.Part(reason, color)))

    private fun disconnectPacket(id: Int, parts: List<Text.Part>) = packet(id) {
        if (id == PacketIds.LOGIN_DISCONNECT) writeString(Text.json(parts)) else Text.writeNbt(this, parts)
    }

    private fun wrap(bytes: ByteArray) = Unpooled.wrappedBuffer(bytes)

    private fun startHeartbeat() {
        heartbeat = upstream?.eventLoop()?.scheduleAtFixedRate(
            { if (phase == Phase.PLAY) upstream?.writeAndFlush(packet(ids.playClientTickEnd) {}) },
            TICK_MS,
            TICK_MS,
            TimeUnit.MILLISECONDS
        )
    }

    fun reconfigure() {
        clearState()
        phase = Phase.CONFIGURATION
    }

    private fun clearState() {
        snapshot.reset()
        mirror.reset()
        signatures.reset()
        chatIndex = 0
    }

    fun reconfigured() {
        phase = Phase.PLAY
    }

    private enum class AttachStage { NONE, LOGIN, CONFIGURATION }

    fun disconnect(reason: String) = execute { if (detached) end("disconnected from the console") else kick(reason) }

    fun upstreamClosed() {
        if (closed) return
        if (detached) return serverEndedDetached("the connection closed")
        if (!disconnectRelayed) return kick("The server closed the connection.")
        Log.info("session", "$label: server disconnected the client ($phase)")
        finish()
        downstream.close()
    }

    private fun isLeave(buf: ByteBuf) = buf.duplicate().also { it.readVarInt() }.readString() == Protocol.LEAVE_CHANNEL

    private fun leave() {
        end("left")
        downstream.close()
    }

    private fun end(message: String) {
        if (closed) return
        finish()
        Log.info("session", "$label: $message")
        attachChannel?.close()
        upstream?.close()
    }

    private fun finish() {
        closed = true
        heartbeat?.cancel(false)
        watchdog?.cancel(false)
        if (Viewer.watching === this) Viewer.watching = null
        Sessions.remove(this)
    }

    companion object {
        const val LOGIN_INTENT = 2
        const val TICK_MS = 50L
        const val INACTIVITY_NANOS = 10_000_000_000L
        const val CLOSE_DELAY_MS = 250L
    }
}

class Handoff(private val session: Session) {
    private class Pending(val publicKey: ByteArray, val verifyToken: ByteArray, val secret: ByteArray)

    private var pending: Pending? = null

    val waiting get() = pending != null

    fun onEncryptionRequest(buf: ByteBuf) {
        val serverId = buf.readString()
        val publicKey = buf.readByteArray()
        val verifyToken = buf.readByteArray()
        val authenticate = !buf.isReadable || buf.readBoolean()
        val secret = Encryption.newSecret()
        pending = Pending(publicKey, verifyToken, secret)
        if (!authenticate) return complete()

        val hash = Encryption.serverHash(serverId, secret, publicKey)
        session.downstream.writeAndFlush(
            packet(PacketIds.LOGIN_QUERY) {
                writeVarInt(QUERY_ID)
                writeString(Protocol.AUTH_CHANNEL)
                writeString(hash)
            }
        )
    }

    fun onAuthResponse(buf: ByteBuf) {
        check(buf.readBoolean()) { "Missing login response" }
        when {
            !buf.readBoolean() -> session.kick("Authentication with Mojang failed.\n${buf.readString()}")
            else -> complete()
        }
    }

    private fun complete() {
        val auth = pending ?: return
        val upstream = session.upstream ?: return
        pending = null

        upstream.pipeline().addFirst("cipher-dec", CipherDecoder(Encryption.aes(Cipher.DECRYPT_MODE, auth.secret)))
        upstream.writeAndFlush(
            packet(PacketIds.ENCRYPTION_RESPONSE) {
                writeByteArray(Encryption.rsaEncrypt(auth.publicKey, auth.secret))
                writeByteArray(Encryption.rsaEncrypt(auth.publicKey, auth.verifyToken))
            }
        ).addListener(
            ChannelFutureListener {
                it.channel().pipeline()
                    .addFirst("cipher-enc", CipherEncoder(Encryption.aes(Cipher.ENCRYPT_MODE, auth.secret)))
            }
        )
    }

    companion object {
        const val QUERY_ID = 0x4E495645
    }
}

class Snapshot(private val ids: PacketTable) {
    val configuration = mutableListOf<ByteArray>()
    var loginSuccess: ByteArray? = null
        private set
    var login: ByteArray? = null
        private set

    fun reset() {
        configuration.clear()
        login = null
    }

    fun record(phase: Phase, buf: ByteBuf) {
        val id = buf.peekVarInt()
        when (phase) {
            Phase.LOGIN if id == PacketIds.LOGIN_SUCCESS -> loginSuccess = ByteBufUtil.getBytes(buf)
            Phase.CONFIGURATION if id in ids.configReplayed -> configuration += ByteBufUtil.getBytes(buf)
            Phase.PLAY if login == null && id == ids.playLogin -> login = ByteBufUtil.getBytes(buf)

            else -> Unit
        }
    }
}

object Detached {
    private const val CHUNKS_PER_TICK = 9f

    private fun text(buf: ByteBuf) = runCatching { Components.read(buf) }.getOrElse { "unreadable reason" }

    private fun failed(what: String) = ChannelFutureListener { if (!it.isSuccess) Log.error("detached", "$what write failed: ${it.cause()}") }

    fun respond(session: Session, buf: ByteBuf) {
        val upstream = session.upstream ?: return
        when (session.phase) {
            Phase.PLAY -> play(session, upstream, buf)
            Phase.CONFIGURATION -> configuration(session, upstream, buf)
            Phase.LOGIN -> {}
        }
    }

    private fun play(session: Session, upstream: Channel, buf: ByteBuf) {
        val ids = session.ids
        if (buf.peekVarInt() == ids.playPlayerChat) session.signatures.record(buf)
        session.mirror.clientbound(buf)
        Viewer.feed(session, buf)
        session.snapshot.record(Phase.PLAY, buf)
        if (buf.peekVarInt() == ids.playCustomPayloadClientbound) Plugins.observe(session, Direction.CLIENTBOUND, buf)
        val id = buf.readVarInt()
        when (id) {
            ids.playKeepAlive -> {
                val challenge = buf.readLong()
                upstream.writeAndFlush(packet(ids.playKeepAliveResponse) { writeLong(challenge) })
                    .addListener(failed("keepalive"))
            }

            ids.playPing -> {
                val challenge = buf.readInt()
                upstream.writeAndFlush(packet(ids.playPong) { writeInt(challenge) }).addListener(failed("pong"))
            }

            ids.playPosition -> {
                val teleportId = buf.readVarInt()
                upstream.writeAndFlush(
                    packet(ids.playAcceptTeleport) {
                        session.mirror.confirmTeleport(
                            this,
                            teleportId
                        )
                    }
                )
            }

            ids.playChunkBatchFinished -> upstream.writeAndFlush(
                packet(ids.playChunkBatchReceived) {
                    writeFloat(
                        CHUNKS_PER_TICK
                    )
                }
            )

            ids.playStartConfiguration -> {
                upstream.writeAndFlush(packet(ids.playConfigurationAcknowledged) {})
                session.reconfigure()
            }

            ids.playDisconnect -> session.serverEndedDetached(text(buf))
            ids.playTransfer -> session.serverEndedDetached("it transferred the client")
        }
    }

    private fun configuration(session: Session, upstream: Channel, buf: ByteBuf) {
        val ids = session.ids
        session.snapshot.record(Phase.CONFIGURATION, buf)
        when (buf.readVarInt()) {
            ids.configKeepAlive -> {
                val challenge = buf.readLong()
                upstream.writeAndFlush(packet(ids.configKeepAliveResponse) { writeLong(challenge) })
            }

            ids.configPing -> {
                val challenge = buf.readInt()
                upstream.writeAndFlush(packet(ids.configPong) { writeInt(challenge) })
            }

            ids.configKnownPacks -> upstream.writeAndFlush(packet(ids.configKnownPacksResponse) { writeVarInt(0) })
            ids.configCodeOfConduct -> upstream.writeAndFlush(packet(ids.configCodeOfConductAccepted) {})
            ids.configFinishClientbound -> {
                upstream.writeAndFlush(packet(ids.configFinish) {})
                session.reconfigured()
            }

            ids.configDisconnect -> session.serverEndedDetached(text(buf))
        }
    }
}

object Sessions {
    private val sessions = ConcurrentHashMap<Int, Session>()
    private val ids = AtomicInteger()

    fun add(session: Session) {
        session.id = ids.incrementAndGet()
        sessions[session.id] = session
        Subscribers.updated(session)
    }

    fun remove(session: Session) {
        sessions.remove(session.id)
        Subscribers.removed(session)
    }

    fun all() = sessions.values.sortedBy { it.id }

    fun find(id: Int) = sessions[id]

    fun find(id: String) = id.toIntOrNull()?.let { find(it) }
}
