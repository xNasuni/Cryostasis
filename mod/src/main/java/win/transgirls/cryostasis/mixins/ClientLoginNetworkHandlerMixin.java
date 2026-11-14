package win.transgirls.cryostasis.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.cryostasis.systems.network.Routing;

@Mixin(targets = "net.minecraft.client.network.ClientLoginNetworkHandler")
public abstract class ClientLoginNetworkHandlerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void cryostasis$rememberOrigin(CallbackInfo ci, @Local(argsOnly = true) ServerInfo info) {
        if (info != null) Routing.rememberOrigin(info.address);
    }
}
