package win.transgirls.cryostasis.systems.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.SharedConstants
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import win.transgirls.cryostasis.Cryostasis
import win.transgirls.cryostasis.shared.Protocol
import win.transgirls.cryostasis.types.NieveSession
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object Routing {
    private val loginThread = ThreadLocal<Boolean>()
    private val routed = ThreadLocal<Boolean>()
    private val real = ThreadLocal<InetSocketAddress>()
    private val origin = ThreadLocal<String>()
    private val session = ThreadLocal<Int>()

    @Volatile
    private var pendingSession = Protocol.NEW_SESSION

    @JvmStatic
    fun attachTo(id: Int) {
        pendingSession = id
    }

    @JvmStatic
    fun markLoginThread() {
        loginThread.set(true)
    }

    @JvmStatic
    fun route(address: InetSocketAddress): InetSocketAddress {
        if (loginThread.get() != true) return address
        session.set(pendingSession)
        pendingSession = Protocol.NEW_SESSION
        Cryostasis.viaNieve = Cryostasis.enabled
        val self = address.port == Cryostasis.nievePort &&
            (
                address.address?.isLoopbackAddress == true || address.address == runCatching {
                    InetAddress.getByName(
                        Cryostasis.nieveHost
                    )
                }.getOrNull()
                )
        if (!Cryostasis.enabled || self) {
            Cryostasis.viaNieve = false
            return address
        }

        routed.set(true)
        real.set(address)

        Cryostasis.log.info("Routing {} through Nieve at {}:{}", address, Cryostasis.nieveHost, Cryostasis.nievePort)
        return InetSocketAddress(Cryostasis.nieveHost, Cryostasis.nievePort)
    }

    @JvmStatic
    fun takeReal(): InetSocketAddress? = real.get().also { real.remove() }

    @JvmStatic
    fun rememberOrigin(address: String) = origin.set(address)

    @JvmStatic
    fun handshakeHost(host: String): String = if (routed.get() == true) {
        Protocol.encodeRoute(
            host,
            origin.get().orEmpty(),
            session.get() ?: Protocol.NEW_SESSION
        )
    } else {
        host
    }
}

object LeavePayload : CustomPayload {
    val ID = CustomPayload.Id<LeavePayload>(Identifier.of(Protocol.LEAVE_CHANNEL))
    val CODEC: PacketCodec<RegistryByteBuf, LeavePayload> = PacketCodec.unit(LeavePayload)

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}

object NieveLink {
    private const val TIMEOUT = 1500
    private const val RETRY = 2000L
    private const val STATUS_INTENT = 1

    @Volatile
    var sessions = emptyList<NieveSession>()
        private set

    @Volatile
    var online = false
        private set

    @Volatile
    var failed = false
        private set

    @Volatile
    var mismatch = 0
        private set

    @Volatile
    var uptimeBase = 0L
        private set

    @Volatile
    var uptimeAt = 0L
        private set

    private val live = LinkedHashMap<Int, NieveSession>()

    @Volatile
    var latency = -1L
        private set

    @Volatile
    var version = 0
        private set

    fun uptime() = uptimeBase + (System.currentTimeMillis() - uptimeAt) / 1000

    fun start() {
        thread(name = "cryostasis-link", isDaemon = true) {
            while (true) {
                try {
                    if (Cryostasis.enabled) listen(Cryostasis.nieveHost, Cryostasis.nievePort)
                } catch (e: Exception) {
                    report(e)
                }

                latency = -1L
                update(emptyList(), online = false, failed = Cryostasis.enabled)
                wake.tryAcquire(RETRY, TimeUnit.MILLISECONDS)
                wake.drainPermits()
            }
        }
    }

    private val wake = Semaphore(0)

    @Volatile
    private var socket: Socket? = null

    fun reconnect() {
        socket?.close()
        wake.release()
    }

    private var lastError: String? = null

    private fun report(e: Exception) {
        if (e is IOException) return
        val message = "${e.javaClass.simpleName} ${e.message.orEmpty()}"
        if (message == lastError) return
        lastError = message
        Cryostasis.log.warn("Nieve link failed: {}", message)
    }

    private fun listen(host: String, port: Int) = Socket().use { socket ->
        this.socket = socket
        socket.connect(InetSocketAddress(host, port), TIMEOUT)
        live.clear()

        val out = DataOutputStream(socket.getOutputStream().buffered())
        val input = DataInputStream(socket.getInputStream().buffered())
        out.writePacket(0x00) {
            writeVarInt(SharedConstants.getProtocolVersion())
            writeString(Protocol.encodeRoute(host, ""))
            writeShort(port)
            writeVarInt(STATUS_INTENT)
        }
        out.writePacket(0x00) {}
        out.ping()
        out.flush()

        while (true) {
            input.readVarInt()
            when (input.readVarInt()) {
                0x00 -> if (message(JsonParser.parseString(input.readString()).asJsonObject)) {
                    out.ping()
                    out.flush()
                }

                0x01 -> {
                    val before = bars(latency)
                    latency = (System.nanoTime() - input.readLong()) / 1_000_000L
                    if (bars(latency) != before) version++
                }

                else -> error("unexpected status packet")
            }
        }
    }

    private fun bars(ping: Long) = when {
        ping < 0 -> 0
        ping < 150 -> 5
        ping < 300 -> 4
        ping < 600 -> 3
        ping < 1000 -> 2
        else -> 1
    }

    private fun message(root: JsonObject): Boolean {
        if (mismatch != 0) return false
        root.getAsJsonObject("snapshot")?.let { return snapshot(it) }
        root.getAsJsonObject("session")?.let { session(it).let { session -> live[session.id] = session } }
        root.get("removed")?.let { live.remove(it.asInt) }
        return publish()
    }

    private fun snapshot(snapshot: JsonObject): Boolean {
        val remote = snapshot.get("version").asInt
        if (remote != Protocol.VERSION) return update(emptyList(), online = true, failed = true, mismatch = remote)
        uptimeBase = snapshot.get("uptime").asLong
        uptimeAt = System.currentTimeMillis()
        live.clear()
        snapshot.getAsJsonArray("sessions")
            .forEach { session(it.asJsonObject).let { session -> live[session.id] = session } }
        return publish()
    }

    private fun publish() = update(live.values.sortedBy { it.id }, online = true, failed = false)

    private fun session(o: JsonObject) = NieveSession(
        id = o.get("id").asInt,
        name = o.get("name").asString,
        host = o.get("host").asString,
        port = o.get("port").asInt,
        origin = o.get("origin").asString,
        detached = o.get("state").asString == "detached",
        uptime = o.get("uptime").asLong
    )

    private fun update(found: List<NieveSession>, online: Boolean, failed: Boolean, mismatch: Int = 0): Boolean {
        if (found == sessions && online == this.online && failed == this.failed && mismatch == this.mismatch) return false

        sessions = found
        this.mismatch = mismatch
        this.online = online
        this.failed = failed
        version++
        return true
    }
}

private fun DataOutputStream.ping() = writePacket(0x01) { writeLong(System.nanoTime()) }

private fun DataOutputStream.writePacket(id: Int, body: DataOutputStream.() -> Unit) {
    val buffer = ByteArrayOutputStream()
    val data = DataOutputStream(buffer)
    data.writeVarInt(id)
    data.body()
    writeVarInt(buffer.size())
    write(buffer.toByteArray())
}

private fun DataOutputStream.writeVarInt(value: Int) {
    var v = value
    while (v and 0x7F.inv() != 0) {
        writeByte((v and 0x7F) or 0x80)
        v = v ushr 7
    }
    writeByte(v)
}

private fun DataOutputStream.writeString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeVarInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readVarInt(): Int {
    var value = 0
    for (i in 0 until 5) {
        val b = readUnsignedByte()
        value = value or ((b and 0x7F) shl (7 * i))
        if (b and 0x80 == 0) return value
    }
    error("VarInt too big")
}

private fun DataInputStream.readString(): String = String(readNBytes(readVarInt()), Charsets.UTF_8)
