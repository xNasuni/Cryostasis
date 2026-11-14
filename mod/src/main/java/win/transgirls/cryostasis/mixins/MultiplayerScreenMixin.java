package win.transgirls.cryostasis.mixins;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.cryostasis.systems.ui.NieveServerEntry;
import win.transgirls.cryostasis.systems.ui.NieveSessions;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
    @Shadow
    protected MultiplayerServerListWidget serverListWidget;

    @Shadow
    private ButtonWidget buttonEdit;

    @Shadow
    private ButtonWidget buttonDelete;

    @Unique
    private void cryostasis$rebuild() {
        ((MultiplayerServerListWidgetAccessor) serverListWidget).cryostasis$updateEntries();
    }

    @Inject(method = "updateButtonActivationStates", at = @At("TAIL"))
    private void cryostasis$lockLocalEntry(CallbackInfo ci) {
        if (serverListWidget.getSelectedOrNull() instanceof NieveServerEntry) {
            buttonEdit.active = false;
            buttonDelete.active = false;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cryostasis$tickSessions(CallbackInfo ci) {
        NieveSessions.tick(this::cryostasis$rebuild);
    }
}
