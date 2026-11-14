package win.transgirls.nieve.systems.cli.commands

import win.transgirls.nieve.systems.chat.Viewer
import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.tools.Log

object ChatCommand : Command {
    override val name = "chat"
    override val usage = "chat <message>"
    override val description = "sends a message or a command"

    override fun run(args: List<String>) {
        if (args.isEmpty()) return Log.fail("usage: $usage")
        val session = Viewer.watching ?: return Log.fail("no session selected")
        if (!session.detached) return Log.fail("${session.label} is currently attached")
        session.say(args.joinToString(" "))
    }
}
