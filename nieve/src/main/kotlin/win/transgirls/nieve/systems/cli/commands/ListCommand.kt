package win.transgirls.nieve.systems.cli.commands

import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.systems.session.Sessions
import win.transgirls.nieve.tools.Colors
import win.transgirls.nieve.tools.Log

object ListCommand : Command {
    override val name = "list"
    override val aliases = listOf("ls", "sessions")
    override val description = "lists the sessions"

    override fun run(args: List<String>) {
        val sessions = Sessions.all()
        if (sessions.isEmpty()) return Log.out("${Colors.GRAY}no sessions${Colors.RESET}")
        sessions.forEach {
            val state = if (it.detached) "${Colors.GRAY}detached" else "${Colors.GREEN}attached"
            val age = Log.duration(System.nanoTime() - it.createdAt)
            Log.out("${Colors.GRAY}#${it.id}${Colors.RESET} ${it.name} ${Colors.GRAY}${it.host}:${it.port}${Colors.RESET} $state${Colors.RESET} ${Colors.GRAY}$age${Colors.RESET}")
        }
    }
}
