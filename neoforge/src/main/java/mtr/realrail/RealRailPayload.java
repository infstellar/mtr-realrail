package mtr.realrail;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class RealRailPayload<T> {
    private final CustomPacketPayload.Type<Payload<T>> type;
    private final StreamCodec<RegistryFriendlyByteBuf, Payload<T>> codec;

    RealRailPayload(String id, StreamCodec<? super RegistryFriendlyByteBuf, T> dataCodec) {
        type = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MtrRealRail.MOD_ID, id));
        codec = StreamCodec.of(
                (buffer, packet) -> dataCodec.encode(buffer, packet.data()),
                buffer -> new Payload<>(type, dataCodec.decode(buffer)));
    }

    void serverbound(PayloadRegistrar registrar, BiConsumer<ServerPlayer, T> handler) {
        registrar.playToServer(type, codec, (packet, context) ->
                handler.accept((ServerPlayer) context.player(), packet.data()));
    }

    void clientbound(PayloadRegistrar registrar, Consumer<T> handler) {
        registrar.playToClient(type, codec, (packet, context) -> handler.accept(packet.data()));
    }

    void sendToServer(T data) {
        PacketDistributor.sendToServer(new Payload<>(type, data));
    }

    void sendToPlayer(ServerPlayer player, T data) {
        PacketDistributor.sendToPlayer(player, new Payload<>(type, data));
    }

    private record Payload<T>(Type<Payload<T>> type, T data) implements CustomPacketPayload {
    }
}
