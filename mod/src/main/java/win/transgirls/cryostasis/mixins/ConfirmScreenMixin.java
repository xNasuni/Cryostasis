package win.transgirls.cryostasis.mixins;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.cryostasis.Cryostasis;
import win.transgirls.cryostasis.systems.ui.CryostasisButton;

@Mixin(ConfirmScreen.class)
public abstract class ConfirmScreenMixin {
    @Shadow
    @Final
    protected DirectionalLayoutWidget layout;

    @Inject(method = "addButtons", at = @At("TAIL"))
    private void cryostasis$addDetachButton(DirectionalLayoutWidget layout, CallbackInfo ci) {
        if (Cryostasis.viaNieve && (Object) this instanceof DeathScreen.TitleScreenConfirmScreen) {
            this.layout.add(new CryostasisButton(204, 23, Text.literal("Detach"), Cryostasis::detach));
        }
    }
}
