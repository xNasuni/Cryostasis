package win.transgirls.cryostasis

import com.mojang.authlib.exceptions.AuthenticationException
import io.netty.buffer.Unpooled
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import win.transgirls.cryostasis.shared.Protocol
import win.transgirls.cryostasis.systems.network.LeavePayload
import win.transgirls.cryostasis.systems.network.NieveLink
import java.util.concurrent.CompletableFuture

class Cryostasis : ClientModInitializer {
    companion object {
        @JvmField
        var mc = MinecraftClient.getInstance()!!

        @JvmField
        val log = LoggerFactory.getLogger("Cryostasis")!!

        @JvmField
        @Volatile
        var enabled = true

        @JvmField
        @Volatile
        var viaNieve = false

        @JvmField
        var nieveHost = "127.0.0.1"

        @JvmField
        var nievePort = Protocol.NIEVE_PORT

        private var detaching = false

        @JvmStatic
        fun detach() {
            detaching = true
            try {
                mc.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT)
            } finally {
                detaching = false
            }
        }

        @JvmStatic
        fun leave() {
            if (viaNieve && !detaching) mc.networkHandler?.connection?.send(CustomPayloadC2SPacket(LeavePayload))
        }
    }

    override fun onInitializeClient() {
        NieveLink.start()
        PayloadTypeRegistry.serverboundPlay().register(LeavePayload.ID, LeavePayload.CODEC)
        ClientLoginNetworking.registerGlobalReceiver(Identifier.of(Protocol.AUTH_CHANNEL)) { client, _, buf, _ ->
            val serverHash = buf.readString()
            CompletableFuture.supplyAsync {
                val response = PacketByteBuf(Unpooled.buffer())
                try {
                    val session = client.session
                    client.apiServices.sessionService().joinServer(session.uuidOrNull, session.accessToken, serverHash)
                    response.writeBoolean(true)
                } catch (e: AuthenticationException) {
                    log.warn("joinServer failed", e)
                    response.writeBoolean(false)
                    response.writeString(e.message ?: "Unknown error")
                }
                response
            }
        }
        log.info("cryostasis initialized :3")
    }
}
