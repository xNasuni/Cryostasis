package win.transgirls.nieve.systems.cli.commands

import win.transgirls.nieve.systems.chat.Viewer
import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.systems.session.Sessions
import win.transgirls.nieve.tools.Colors
import win.transgirls.nieve.tools.Log

object SelectCommand : Command {
    override val name = "select"
    override val aliases = listOf("sel")
    override val usage = "select [id]"
    override val description = "selects a session or deselects if no id present"

    override fun run(args: List<String>) {
        if (args.isEmpty()) {
            if (Viewer.watching == null) {
                return Log.info(
                    "view",
                    "${Colors.GRAY}selection was already clear${Colors.RESET}"
                )
            }
            Viewer.watching = null
            return Log.info("view", "selection cleared")
        }
        val id = args[0].removePrefix("#")
        val session = Sessions.find(id) ?: return Log.fail("no session #$id")
        Viewer.watching = session
        Log.success("view", "viewing #$id ${session.label}")
    }

    override fun complete(index: Int) = if (index == 0) Sessions.all().map { it.id.toString() } else emptyList()
}
