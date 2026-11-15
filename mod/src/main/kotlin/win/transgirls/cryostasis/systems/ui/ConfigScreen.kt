package win.transgirls.cryostasis.systems.ui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text

class ConfigScreen(private val parent: Screen) : Screen(Text.literal("Cryostasis")) {
    override fun init() {
        addDrawableChild(
            ButtonWidget.builder(ScreenTexts.DONE) { close() }
                .dimensions(width / 2 - 100, height - 28, 200, 20).build()
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, -1)
    }

    override fun close() {
        parent.setFocused(null)
        client?.guiManager?.setScreen(parent)
    }
}
