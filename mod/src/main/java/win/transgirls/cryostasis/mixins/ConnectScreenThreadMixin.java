package win.transgirls.cryostasis.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.cryostasis.systems.network.Routing;

@Mixin(targets = "net.minecraft.client.gui.screen.multiplayer.ConnectScreen$1")
public abstract class ConnectScreenThreadMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void cryostasis$markLoginThread(CallbackInfo ci) {
        Routing.markLoginThread();
    }
}
