package win.transgirls.cryostasis.mixins;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiplayerServerListWidget.class)
public interface MultiplayerServerListWidgetAccessor {
    @Invoker("updateEntries")
    void cryostasis$updateEntries();

    @Accessor("screen")
    MultiplayerScreen cryostasis$screen();
}
