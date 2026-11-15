package win.transgirls.cryostasis.systems.ui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

class ConfigButton(private val anchor: ButtonWidget, action: Runnable) : CryostasisButton(WIDTH, anchor.height * 2 + GAP, Text.literal("Configuration"), action) {

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        x = anchor.x - GAP - width
        y = anchor.y - anchor.height - GAP
        super.renderWidget(context, mouseX, mouseY, delta)
    }

    override fun renderIcon(context: DrawContext) = drawIcon(context, x + (width - ICON_SIZE) / 2, y + (height - ICON_SIZE) / 2)

    override fun renderLabel(context: DrawContext) = Unit

    companion object {
        private const val WIDTH = 23
        private const val GAP = 4
    }
}
