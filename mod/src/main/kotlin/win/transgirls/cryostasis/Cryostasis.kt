package win.transgirls.cryostasis

import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.MinecraftClient
import org.slf4j.LoggerFactory

class Cryostasis : ClientModInitializer {
    companion object {
        @JvmField
        var mc = MinecraftClient.getInstance()

        @JvmField
        val log = LoggerFactory.getLogger("Cryostasis")!!
    }

    override fun onInitializeClient() {
        log.info("cryostasis initialized :3")
    }
}
