package win.transgirls.cryostasis.systems.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import win.transgirls.cryostasis.types.NieveSession;

import static win.transgirls.cryostasis.Cryostasis.mc;

public class NieveSessionEntry extends NieveServerEntry {
    private static final int TITLE_OFFSET = 35;
    private static final int WHITE = 0xFFFFFFFF;

    private final NieveSession session;
    private final ServerInfo saved;

    public NieveSessionEntry(MultiplayerServerListWidget widget, MultiplayerScreen screen, NieveSession session, ServerInfo saved) {
        super(widget, screen, NieveSessions.info(session, saved));
        this.session = session;
        this.saved = saved;
    }

    @Override
    protected void prepare() {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
        super.render(context, mouseX, mouseY, hovered, delta);
        context.drawTextWithShadow(mc.textRenderer, NieveSessions.title(session), getContentX() + TITLE_OFFSET, getContentY() + 1, WHITE);
    }

    @Override
    public Text getNarration() {
        return Text.literal("Nieve session " + session.getName() + " on " + session.getOrigin());
    }

    @Override
    public boolean isOfSameType(MultiplayerServerListWidget.Entry entry) {
        return entry instanceof NieveSessionEntry other && other.session.getId() == session.getId();
    }

    @Override
    public void connect() {
        NieveSessions.attach(session, saved);
    }
}
