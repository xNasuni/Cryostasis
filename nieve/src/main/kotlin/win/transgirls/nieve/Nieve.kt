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

    private lateinit var channel: Channel

    var port = Protocol.NIEVE_PORT
        private set

    private var host = "127.0.0.1"

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

        port = args.firstOrNull()?.toInt() ?: Protocol.NIEVE_PORT
        host = args.getOrNull(1) ?: "127.0.0.1"
        val group = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
        try {
            channel = ServerBootstrap()
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
            Log.success("nieve", "listening on $host:$port")
            Console.start()
            channel.closeFuture().sync()
        } finally {
            group.shutdownGracefully(250, 1000, TimeUnit.MILLISECONDS).sync()
            Console.close()
            exitProcess(0)
        }
    }
}
