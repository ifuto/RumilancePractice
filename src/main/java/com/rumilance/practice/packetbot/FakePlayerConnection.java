package com.rumilance.practice.packetbot;

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
            java.lang.reflect.Field field = Connection.class.getDeclaredField("channel");
            field.setAccessible(true);
            field.set(this, new EmbeddedChannel());
        } catch (ReflectiveOperationException ignored) {
            // A missing channel only means vanilla gates treat the bot as disconnected;
            // gameplay keeps working.
        }
    }

    @Override
    public void setReadOnly() {
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
