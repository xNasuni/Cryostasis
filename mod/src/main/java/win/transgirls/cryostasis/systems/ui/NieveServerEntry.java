package win.transgirls.cryostasis.systems.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerInfo;

public class NieveServerEntry extends MultiplayerServerListWidget.ServerEntry {
    private static final int ICON = 32;

    public NieveServerEntry(MultiplayerServerListWidget widget, MultiplayerScreen screen, ServerInfo info) {
        widget.super(screen, info);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
        prepare();
        super.render(context, mouseX, mouseY, false, delta);
    }

    protected void prepare() {
        NieveSessions.refresh();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.x() < getContentX() + ICON && click.y() < getContentY() + ICON) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return false;
    }

    @Override
    public void connect() {
    }
}
