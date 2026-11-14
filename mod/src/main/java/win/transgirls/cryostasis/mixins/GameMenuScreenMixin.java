package win.transgirls.cryostasis.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.cryostasis.Cryostasis;
import win.transgirls.cryostasis.systems.ui.CryostasisButton;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin {
    @Inject(method = "initWidgets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/GridWidget;refreshPositions()V"))
    private void cryostasis$addDetachButton(CallbackInfo ci, @Local GridWidget.Adder helper) {
        if (Cryostasis.viaNieve) {
            helper.add(new CryostasisButton(204, 23, Text.literal("Detach"), Cryostasis::detach), 2);
        }
    }
}
