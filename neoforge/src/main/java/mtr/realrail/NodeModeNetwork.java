package mtr.realrail;

import mtr.realrail.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NodeModeNetwork {
    private static final RealRailPayload<BlockPos> REQUEST = new RealRailPayload<>("request_node_mode", BlockPos.STREAM_CODEC);
    private static final RealRailPayload<BlockPos> TOGGLE = new RealRailPayload<>("toggle_node_mode", BlockPos.STREAM_CODEC);
    private static final RealRailPayload<NodeMode> OPEN = new RealRailPayload<>("open_node_mode", NodeMode.CODEC);

    private NodeModeNetwork() {
    }

    static void register(PayloadRegistrar registrar) {
        REQUEST.serverbound(registrar, (player, pos) ->
                sendOpen(player, pos, NodeModeStore.getMode(pos.getX(), pos.getY(), pos.getZ())));
        TOGGLE.serverbound(registrar, (player, pos) ->
                sendOpen(player, pos, NodeModeStore.toggle(pos.getX(), pos.getY(), pos.getZ())));
        OPEN.clientbound(registrar, value -> ClientPacketHandler.openNodeModeScreen(value.pos(), value.tailMode()));
    }

    public static void requestOpen(BlockPos pos) {
        REQUEST.sendToServer(pos);
    }

    public static void sendToggle(BlockPos pos) {
        TOGGLE.sendToServer(pos);
    }

    private static void sendOpen(ServerPlayer player, BlockPos pos, RealRailConfig.NodeMode mode) {
        OPEN.sendToPlayer(player, new NodeMode(pos, mode == RealRailConfig.NodeMode.TAIL));
    }

    private record NodeMode(BlockPos pos, boolean tailMode) {
        private static final StreamCodec<FriendlyByteBuf, NodeMode> CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, NodeMode::pos,
                        ByteBufCodecs.BOOL, NodeMode::tailMode, NodeMode::new);
    }
}
