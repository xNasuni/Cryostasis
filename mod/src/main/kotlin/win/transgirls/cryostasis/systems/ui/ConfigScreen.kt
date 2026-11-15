package win.transgirls.cryostasis.systems.ui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CyclingButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text
import win.transgirls.cryostasis.Config

class ConfigScreen(private val parent: Screen) : Screen(Text.literal("Cryostasis")) {
    private lateinit var enabled: CyclingButtonWidget<Boolean>
    private lateinit var remote: CyclingButtonWidget<Boolean>
    private lateinit var address: TextFieldWidget

    override fun init() {
        enabled = addDrawableChild(
            CyclingButtonWidget.onOffBuilder(Config.enabled)
                .build(width / 2 - 100, 36, 200, 20, Text.literal("Cryostasis")) { _, _ -> unlock() }
        )
        remote = addDrawableChild(
            CyclingButtonWidget.onOffBuilder(Config.remote)
                .build(
                    width / 2 - 100,
                    60,
                    200,
                    20,
                    Text.literal("Use Remote Nieve Server")
                ) { _, _ -> unlock() }
        )
        address = addDrawableChild(TextFieldWidget(textRenderer, width / 2 - 100, 84, 200, 20, Text.literal("Address")))
        address.setMaxLength(255)
        address.text = Config.address
        address.setChangedListener { validate() }
        unlock()
        addDrawableChild(
            ButtonWidget.builder(ScreenTexts.DONE) { if (save()) leave() }
                .dimensions(width / 2 - 100, height - 28, 200, 20).build()
        )
    }

    private fun unlock() {
        val on = enabled.value
        val custom = on && remote.value
        remote.active = on
        address.setEditable(custom)
        address.active = custom
        validate()
    }

    private fun validate() {
        address.setEditableColor(if (!enabled.value || !remote.value || Config.parse(address.text) != null) EDITABLE else INVALID)
    }

    private fun save() = Config.update(enabled.value, remote.value, address.text.trim())

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, -1)
    }

    override fun close() {
        save()
        leave()
    }

    private fun leave() {
        parent.setFocused(null)
        client?.guiManager?.setScreen(parent)
    }

    companion object {
        private const val EDITABLE = 0xFFE0E0E0.toInt()
        private const val INVALID = 0xFFFF5555.toInt()
    }
}
