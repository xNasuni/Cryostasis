package win.transgirls.cryostasis.mixins;

import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.cryostasis.Cryostasis;
import win.transgirls.cryostasis.systems.ui.CryostasisButton;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin extends Screen {
    protected DeathScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "quitLevel", at = @At("HEAD"))
    private void cryostasis$leave(CallbackInfo ci) {
        Cryostasis.leave();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cryostasis$addDetachButton(CallbackInfo ci) {
        if (!Cryostasis.viaNieve) return;
        CryostasisButton button = new CryostasisButton(200, 23, Text.literal("Detach"), Cryostasis::detach);
        button.setPosition(width / 2 - 100, height / 4 + 120);
        addDrawableChild(button);
    }
}
