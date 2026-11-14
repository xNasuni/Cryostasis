package win.transgirls.cryostasis.mixins;

import io.netty.channel.ChannelFuture;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkingBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import win.transgirls.cryostasis.systems.network.Routing;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Unique
    private InetSocketAddress cryostasis$real;

    @Inject(method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/network/NetworkingBackend;Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;", at = @At("RETURN"))
    private static void cryostasis$remember(InetSocketAddress address, NetworkingBackend backend, ClientConnection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        InetSocketAddress real = Routing.takeReal();
        if (real != null) ((ClientConnectionMixin) (Object) connection).cryostasis$real = real;
    }

    @ModifyVariable(method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/network/NetworkingBackend;Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;", at = @At("HEAD"), argsOnly = true)
    private static InetSocketAddress cryostasis$route(InetSocketAddress address) {
        return Routing.route(address);
    }

    @Inject(method = "getAddress", at = @At("RETURN"), cancellable = true)
    private void cryostasis$address(CallbackInfoReturnable<SocketAddress> cir) {
        if (cryostasis$real != null) cir.setReturnValue(cryostasis$real);
    }

    @ModifyVariable(method = "connect(Ljava/lang/String;ILnet/minecraft/network/state/NetworkState;Lnet/minecraft/network/state/NetworkState;Lnet/minecraft/network/listener/ClientPacketListener;Z)V", at = @At("HEAD"), argsOnly = true)
    private String cryostasis$handshakeHost(String address) {
        return Routing.handshakeHost(address);
    }
}
