package win.transgirls.nieve.systems.cli.commands

import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.systems.cli.Console

object ClearCommand : Command {
    override val name = "clear"
    override val aliases = listOf("cls")
    override val description = "clears the console"

    override fun run(args: List<String>) = Console.clear()
}
