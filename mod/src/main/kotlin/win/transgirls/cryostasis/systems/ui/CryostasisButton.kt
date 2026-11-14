package win.transgirls.cryostasis.systems.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.client.render.RenderPipelines
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class CryostasisButton(width: Int, height: Int, label: Text, private val action: Runnable) : ClickableWidget(0, 0, width, height, label) {
    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val sprite = if (isHovered || isFocused) HIGHLIGHTED else NORMAL
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height)
        val font = MinecraftClient.getInstance().textRenderer
        context.drawCenteredTextWithShadow(
            font,
            message,
            x + width / 2,
            y + (height - font.fontHeight) / 2,
            if (active) ACTIVE else INACTIVE
        )
    }

    override fun onClick(click: Click, doubled: Boolean) = action.run()

    override fun keyPressed(input: KeyInput): Boolean {
        if (!active || !visible || !input.isEnterOrSpace) return false
        playDownSound(MinecraftClient.getInstance().soundManager)
        action.run()
        return true
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) = appendDefaultNarrations(builder)

    companion object {
        private const val ACTIVE = 0xFFFFFFFF.toInt()
        private const val INACTIVE = 0xFFA0A0A0.toInt()
        private val NORMAL = Identifier.of("cryostasis", "widget/button")
        private val HIGHLIGHTED = Identifier.of("cryostasis", "widget/button_highlighted")
    }
}
