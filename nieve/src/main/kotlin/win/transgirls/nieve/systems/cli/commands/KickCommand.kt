package win.transgirls.nieve.systems.cli.commands

import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.systems.session.Sessions
import win.transgirls.nieve.tools.Log

object KickCommand : Command {
    override val name = "kick"
    override val aliases = listOf("disconnect")
    override val usage = "kick <id> [reason]"
    override val description = "kicks the attached player and leaves the server"

    override fun run(args: List<String>) {
        if (args.isEmpty()) return Log.fail("usage: $usage")
        val session =
            Sessions.find(args[0].removePrefix("#")) ?: return Log.fail("no session #${args[0].removePrefix("#")}")
        session.disconnect(args.drop(1).joinToString(" ").ifEmpty { "Disconnected by Nieve." })
    }

    override fun complete(index: Int) = if (index == 0) Sessions.all().map { it.id.toString() } else emptyList()
}
