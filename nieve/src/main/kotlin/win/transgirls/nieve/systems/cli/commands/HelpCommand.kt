package win.transgirls.nieve.systems.cli.commands

import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.systems.cli.Commands
import win.transgirls.nieve.tools.Colors
import win.transgirls.nieve.tools.Log

object HelpCommand : Command {
    override val name = "help"
    override val aliases = listOf("?")
    override val description = "lists the commands"

    override fun run(args: List<String>) {
        Commands.all.forEach { Log.out("${it.usage} ${Colors.GRAY}${it.description}${Colors.RESET}") }
    }
}
