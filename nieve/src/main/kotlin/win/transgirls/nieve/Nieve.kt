package win.transgirls.nieve

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import win.transgirls.cryostasis.shared.Protocol
import win.transgirls.nieve.systems.cli.Commands
import win.transgirls.nieve.systems.cli.Console
import win.transgirls.nieve.systems.cli.commands.ChatCommand
import win.transgirls.nieve.systems.cli.commands.ClearCommand
import win.transgirls.nieve.systems.cli.commands.HelpCommand
import win.transgirls.nieve.systems.cli.commands.KickCommand
import win.transgirls.nieve.systems.cli.commands.ListCommand
import win.transgirls.nieve.systems.cli.commands.PluginsCommand
import win.transgirls.nieve.systems.cli.commands.SelectCommand
import win.transgirls.nieve.systems.cli.commands.StopCommand
import win.transgirls.nieve.systems.network.DownstreamHandler
import win.transgirls.nieve.systems.network.FrameDecoder
import win.transgirls.nieve.systems.network.FrameEncoder
import win.transgirls.nieve.systems.network.Pipelines
import win.transgirls.nieve.systems.plugin.Plugins
import win.transgirls.nieve.systems.session.Sessions
import win.transgirls.nieve.tools.Log
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

object Nieve {
    private const val CLOSE_WAIT_MS = 1000L
    private const val MAX_PORT = 65535

    private lateinit var channel: Channel

    var port = Protocol.NIEVE_PORT
        private set

    private var host = "127.0.0.1"

    private val build = Nieve::class.java.getResource("/build.txt")!!.readText().trim()

    fun stop() {
        Console.halt()
        Log.info("nieve", "shutting down")
        Sessions.all()
            .map { session ->
                session.disconnect("Nieve is shutting down.")
                session.downstream.closeFuture()
            }
            .forEach { it.awaitUninterruptibly(CLOSE_WAIT_MS) }
        Plugins.stop()
        channel.close()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        Log.markStart()
        listOf(
            HelpCommand,
            ListCommand,
            KickCommand,
            SelectCommand,
            ChatCommand,
            ClearCommand,
            PluginsCommand,
            StopCommand
        ).forEach(Commands::register)

        Plugins.start(listOf())

        port = args.firstOrNull()?.let { text ->
            text.toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: fail("invalid port '$text', expected 1-$MAX_PORT")
        } ?: Protocol.NIEVE_PORT
        host = args.getOrNull(1) ?: "127.0.0.1"
        val group = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
        try {
            channel = try {
                bind(group)
            } catch (e: Exception) {
                fail("couldn't listen on $host:$port: ${e.message ?: e.javaClass.simpleName}")
            }
            Log.success("nieve", "$build listening on $host:$port")
            Console.start()
            channel.closeFuture().sync()
        } finally {
            group.shutdownGracefully(250, 1000, TimeUnit.MILLISECONDS).sync()
            Console.close()
            exitProcess(0)
        }
    }

    private fun fail(message: String): Nothing {
        Log.error("nieve", message)
        exitProcess(1)
    }

    private fun bind(group: MultiThreadIoEventLoopGroup): Channel = ServerBootstrap()
        .group(group)
        .channel(NioServerSocketChannel::class.java)
        .childHandler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                ch.pipeline()
                    .addLast(Pipelines.FRAME_DECODER, FrameDecoder())
                    .addLast(Pipelines.FRAME_ENCODER, FrameEncoder())
                    .addLast(Pipelines.HANDLER, DownstreamHandler())
            }
        })
        .bind(host, port)
        .sync()
        .channel()
}
