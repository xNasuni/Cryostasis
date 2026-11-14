package win.transgirls.nieve.tools

object Log {
    private val start = System.nanoTime()

    var sink: (String) -> Unit = ::println

    fun markStart() = Unit

    fun uptime() = duration(System.nanoTime() - start)

    fun info(tag: String, message: String) = write("${Colors.BLUE}$tag${Colors.RESET} $message")

    fun success(tag: String, message: String) = write("${Colors.BLUE}$tag${Colors.RESET} ${Colors.GREEN}$message${Colors.RESET}")

    fun error(tag: String, message: String) = write("${Colors.BLUE}$tag${Colors.RESET} ${Colors.RED}$message${Colors.RESET}")

    fun out(message: String) = write(message)

    fun fail(message: String) = write("${Colors.RED}$message${Colors.RESET}")

    fun duration(nanos: Long): String {
        val total = nanos / 1_000_000_000L
        val hours = total / 3600
        val minutes = total / 60 % 60
        val seconds = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    private fun write(message: String) = sink("${Colors.GRAY}[${duration(System.nanoTime() - start)}]${Colors.RESET} $message")
}

object Colors {
    const val RESET = "\u001b[0m"
    const val BLUE = "\u001b[38;5;159m"
    const val GRAY = "\u001b[38;5;242m"
    const val RED = "\u001b[38;5;198m"
    const val GREEN = "\u001b[38;5;120m"
}
