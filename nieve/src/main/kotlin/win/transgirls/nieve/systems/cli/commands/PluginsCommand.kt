package win.transgirls.nieve.systems.cli.commands

import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.systems.plugin.Plugins
import win.transgirls.nieve.tools.Log

object PluginsCommand : Command {
    override val name = "plugins"
    override val aliases = listOf("pl")
    override val description = "lists the loaded plugins"

    override fun run(args: List<String>) = Log.success("plugin", Plugins.summary())
}
