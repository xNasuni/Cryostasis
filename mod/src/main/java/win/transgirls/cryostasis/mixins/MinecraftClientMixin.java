package win.transgirls.cryostasis.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.cryostasis.Cryostasis;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void cryostasis$headDisconnect(Text reasonText, CallbackInfo ci) {
        if (reasonText == ClientWorld.QUITTING_MULTIPLAYER_TEXT) {
            Cryostasis.leave();
        }
    }
}
