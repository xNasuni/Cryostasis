package win.transgirls.nieve.systems.cli.commands

import win.transgirls.nieve.Nieve
import win.transgirls.nieve.systems.cli.Command

object StopCommand : Command {
    override val name = "stop"
    override val aliases = listOf("exit", "quit")
    override val description = "disconnects every session and shuts down nieve"

    override fun run(args: List<String>) = Nieve.stop()
}
