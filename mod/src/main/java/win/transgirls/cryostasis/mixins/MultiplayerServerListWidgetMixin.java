package win.transgirls.cryostasis.mixins;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import win.transgirls.cryostasis.systems.ui.NieveSessions;

import java.util.Collection;

@Mixin(MultiplayerServerListWidget.class)
public abstract class MultiplayerServerListWidgetMixin {
    @ModifyArg(method = "updateEntries", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget;replaceEntries(Ljava/util/Collection;)V"), index = 0)
    private Collection<MultiplayerServerListWidget.Entry> cryostasis$sessions(Collection<MultiplayerServerListWidget.Entry> entries) {
        MultiplayerServerListWidget widget = (MultiplayerServerListWidget) (Object) this;
        return NieveSessions.withSessions(widget, ((MultiplayerServerListWidgetAccessor) widget).cryostasis$screen(), entries);
    }
}
