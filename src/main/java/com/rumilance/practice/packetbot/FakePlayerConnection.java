package com.rumilance.practice.packetbot;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * Carpet's FakeClientConnection for Paper: every send is dropped (there is no client on the
 * other end), the channel is a local {@link EmbeddedChannel} so {@code isOpen()} stays true
 * (enderpearls and other "is connected" gates keep working), and the protocol handshake
 * steps are no-ops — {@code PlayerList#placeNewPlayer} runs its synchronous side (world add,
 * player-list add, join event) without any packet dance.
 */
public final class FakePlayerConnection extends Connection {

    public FakePlayerConnection(PacketFlow flow) {
        super(flow);
        try {
            EmbeddedChannel channel = new EmbeddedChannel();
            // Third-party packet injectors (ProtocolLib, PacketEvents) run
            // {@code pipeline.addAfter("encoder", …)} on PlayerJoinEvent. A bare EmbeddedChannel
            // has no named handlers, so that lookup throws NoSuchElementException and the bot
            // gets kicked ("failed to inject into a channel"). Registering the real pipeline's
            // names as no-op handlers lets the injection succeed; the injected handlers then
            // sit unused because send() drops everything and nothing ever reads this channel.
            channel.pipeline().addLast("splitter", new io.netty.channel.ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("decoder", new io.netty.channel.ChannelInboundHandlerAdapter());
            channel.pipeline().addLast("prepender", new io.netty.channel.ChannelOutboundHandlerAdapter());
            channel.pipeline().addLast("encoder", new io.netty.channel.ChannelOutboundHandlerAdapter());
            channel.pipeline().addLast("packet_handler", new io.netty.channel.ChannelInboundHandlerAdapter());
            java.lang.reflect.Field field = Connection.class.getDeclaredField("channel");
            field.setAccessible(true);
            field.set(this, channel);
        } catch (ReflectiveOperationException ignored) {
            // A missing channel only means vanilla gates treat the bot as disconnected;
            // gameplay keeps working.
        }
    }

    @Override
    public void setReadOnly() {
    }

    /** The local {@link EmbeddedChannel}; {@code null} if the reflective install failed. */
    public Channel channel() {
        return this.channel;
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
    }

    @Override
    public void handleDisconnection() {
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener listener) {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> info, T listener) {
    }
}
