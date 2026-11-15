package win.transgirls.nieve.systems.cli

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.ParsedLine
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import win.transgirls.nieve.tools.Colors
import win.transgirls.nieve.tools.Log
import kotlin.concurrent.thread

interface Command {
    val name: String
    val aliases: List<String> get() = emptyList()
    val usage: String get() = name
    val description: String

    fun run(args: List<String>)

    fun complete(index: Int): List<String> = emptyList()
}

object Commands {
    private val byName = LinkedHashMap<String, Command>()

    val all get() = byName.values.distinct()
    val names get() = byName.keys.toList()

    fun register(command: Command) {
        (listOf(command.name) + command.aliases).forEach { byName[it] = command }
    }

    fun unregister(command: Command) {
        byName.entries.removeAll { it.value === command }
    }

    fun find(name: String) = byName[name.lowercase()]

    fun execute(line: String) {
        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        val command = find(parts[0]) ?: return Log.fail("unknown command '${parts[0]}', try help")
        try {
            command.run(parts.drop(1))
        } catch (e: Exception) {
            Log.fail("${command.name} failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

object Console {
    private const val PROMPT = "${Colors.GRAY}> ${Colors.RESET}"

    private lateinit var terminal: Terminal

    @Volatile
    private var running = true

    private object CommandCompleter : Completer {
        override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
            val names = if (line.wordIndex() == 0) {
                Commands.names
            } else {
                Commands.find(line.words()[0])
                    ?.complete(line.wordIndex() - 1).orEmpty()
            }
            names.forEach { candidates.add(Candidate(it)) }
        }
    }

    fun start() {
        Commands.all
        terminal = TerminalBuilder.builder().system(true).ffm(false).build()
        val reader = LineReaderBuilder.builder().terminal(terminal).completer(CommandCompleter).build()
        Log.sink = reader::printAbove
        thread(name = "console", isDaemon = true) {
            while (running) {
                val line = try {
                    reader.readLine(PROMPT)
                } catch (_: UserInterruptException) {
                    "stop"
                } catch (_: EndOfFileException) {
                    break
                } catch (_: Error) {
                    break
                }
                Commands.execute(line)
            }
        }
    }

    fun clear() {
        terminal.puts(InfoCmp.Capability.clear_screen)
        terminal.flush()
    }

    fun halt() {
        running = false
    }

    fun close() {
        if (::terminal.isInitialized) terminal.close()
    }
}
