package win.transgirls.cryostasis.systems.ui

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.client.render.RenderPipelines
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import win.transgirls.cryostasis.Cryostasis.Companion.mc

open class CryostasisButton @JvmOverloads constructor(
    width: Int,
    height: Int,
    label: Text,
    private val action: Runnable,
    private val red: Boolean = false
) : ClickableWidget(0, 0, width, height, label) {
    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val highlighted = isHovered || isFocused
        val sprite = when {
            isRed() -> if (highlighted) RED_HIGHLIGHTED else RED_NORMAL
            else -> if (highlighted) HIGHLIGHTED else NORMAL
        }
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height)
        renderIcon(context)
        renderLabel(context)
    }

    protected open fun isRed() = red

    protected open fun renderIcon(context: DrawContext) = drawIcon(context, x + width - ICON_INSET, y + (height - ICON_SIZE) / 2)

    protected open fun renderLabel(context: DrawContext) {
        val font = mc.textRenderer
        context.drawCenteredTextWithShadow(
            font,
            message,
            x + width / 2,
            y + (height - font.fontHeight) / 2,
            if (active) ACTIVE else INACTIVE
        )
    }

    protected fun drawIcon(context: DrawContext, left: Int, top: Int) = context.drawGuiTexture(
        RenderPipelines.GUI_TEXTURED,
        if (isRed()) RED_SNOWFLAKE else SNOWFLAKE,
        left,
        top,
        ICON_SIZE,
        ICON_SIZE
    )

    override fun onClick(click: Click, doubled: Boolean) = action.run()

    override fun keyPressed(input: KeyInput): Boolean {
        if (!active || !visible || !input.isEnterOrSpace) return false
        playDownSound(mc.soundManager)
        action.run()
        return true
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) = appendDefaultNarrations(builder)

    companion object {
        const val ICON_SIZE = 17
        private const val ICON_INSET = 20
        private const val ACTIVE = 0xFFFFFFFF.toInt()
        private const val INACTIVE = 0xFFA0A0A0.toInt()
        private val NORMAL = Identifier.of("cryostasis", "widget/button")
        private val HIGHLIGHTED = Identifier.of("cryostasis", "widget/button_highlighted")
        private val SNOWFLAKE = Identifier.of("cryostasis", "widget/snowflake")
        private val RED_NORMAL = Identifier.of("cryostasis", "widget/red_button")
        private val RED_HIGHLIGHTED = Identifier.of("cryostasis", "widget/red_button_highlighted")
        private val RED_SNOWFLAKE = Identifier.of("cryostasis", "widget/red_snowflake")
    }
}
