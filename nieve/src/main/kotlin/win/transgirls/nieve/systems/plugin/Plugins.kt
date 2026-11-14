package win.transgirls.nieve.systems.plugin

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.util.ReferenceCountUtil
import win.transgirls.nieve.systems.cli.Command
import win.transgirls.nieve.systems.cli.Commands
import win.transgirls.nieve.tools.Colors
import win.transgirls.nieve.tools.Log
import win.transgirls.nieve.tools.packet
import win.transgirls.nieve.tools.readString
import win.transgirls.nieve.tools.readVarInt
import win.transgirls.nieve.tools.writeString
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.jar.JarFile

object Plugins {
    const val MANIFEST_KEY = "Nieve-Plugin"

    private val plugins = CopyOnWriteArrayList<Pair<NievePlugin, String>>()
    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<PayloadHandler>>()
    private val detachHandlers = CopyOnWriteArrayList<SessionHandler>()
    private val attachHandlers = CopyOnWriteArrayList<SessionHandler>()
    private val commands = CopyOnWriteArrayList<Command>()

    fun summary() = if (plugins.isEmpty()) Colors.GRAY + "none" else plugins.joinToString(", ") { (plugin, source) -> "${plugin.name} ($source)" }

    private class Api(override val name: String) : PluginApi {
        override fun log(message: String) = Log.info(name.lowercase(), message)

        override fun payloads(channel: String, handler: PayloadHandler) {
            handlers.getOrPut(channel) { CopyOnWriteArrayList() }.add(handler)
        }

        override fun onDetach(handler: SessionHandler) {
            detachHandlers += handler
        }

        override fun onAttach(handler: SessionHandler) {
            attachHandlers += handler
        }

        override fun command(command: Command) {
            commands += command
            Commands.register(command)
        }
    }

    fun start(builtin: List<NievePlugin>, directory: File = File("plugins")) {
        builtin.forEach { enable(it, "builtin") }
        directory.listFiles { file -> file.extension == "jar" }?.sortedBy { it.name }?.forEach(::load)
        Log.success("plugin", summary())
    }

    fun stop() {
        plugins.forEach { (plugin) -> runCatching { plugin.disable() } }
        commands.forEach(Commands::unregister)
        commands.clear()
        plugins.clear()
        handlers.clear()
        detachHandlers.clear()
        attachHandlers.clear()
    }

    fun detached(session: SessionView) = fire(detachHandlers, session)

    fun attached(session: SessionView) = fire(attachHandlers, session)

    private fun fire(list: List<SessionHandler>, session: SessionView) {
        for (handler in list) {
            try {
                handler.handle(session)
            } catch (e: Throwable) {
                Log.error("plugin", "${e.javaClass.simpleName} ${e.message.orEmpty()}")
            }
        }
    }

    private fun load(file: File) {
        try {
            val main = JarFile(file).use { it.manifest?.mainAttributes?.getValue(MANIFEST_KEY) }
                ?: return Log.error("plugin", "${file.name}: no $MANIFEST_KEY entry in the manifest")
            val loader = URLClassLoader(arrayOf(file.toURI().toURL()), Plugins::class.java.classLoader)
            val plugin = loader.loadClass(main).getDeclaredConstructor().newInstance() as NievePlugin
            enable(plugin, file.name)
        } catch (e: Throwable) {
            Log.error("plugin", "${file.name}: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
        }
    }

    private fun enable(plugin: NievePlugin, source: String) {
        try {
            plugin.enable(Api(plugin.name))
            plugins += plugin to source
        } catch (e: Throwable) {
            Log.error("plugin", "${plugin.name}: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
        }
    }

    fun dispatch(session: SessionView, direction: Direction, buf: ByteBuf): ByteBuf? {
        val view = buf.duplicate()
        val id = view.readVarInt()
        val channel = view.readString()
        val list = handlers[channel] ?: return buf
        val original = ByteBufUtil.getBytes(view)
        val event = PayloadEvent(session, direction, channel, original)
        for (handler in list) {
            try {
                handler.handle(event)
            } catch (e: Throwable) {
                Log.error("plugin", "$channel: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
            }
        }
        event.toClient.forEach { (target, data) -> session.sendToClient(target, data) }
        event.toServer.forEach { (target, data) -> session.sendToServer(target, data) }
        if (event.cancelled) {
            ReferenceCountUtil.release(buf)
            return null
        }
        if (event.data.contentEquals(original)) return buf
        ReferenceCountUtil.release(buf)
        return packet(id) {
            writeString(channel)
            writeBytes(event.data)
        }
    }

    fun observe(session: SessionView, direction: Direction, buf: ByteBuf) {
        dispatch(session, direction, buf.retain())?.let { ReferenceCountUtil.release(it) }
    }
}
