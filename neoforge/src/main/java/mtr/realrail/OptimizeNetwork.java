package mtr.realrail;

import mtr.realrail.client.ClientPacketHandler;
import mtr.realrail.traffic.DepotSettingsResolver;
import mtr.realrail.traffic.MidLineDepotService;
import mtr.realrail.traffic.OptimizeConfigStore;
import mtr.realrail.traffic.OptimizeSettings;
import mtr.realrail.traffic.RouteRefresher;
import mtr.realrail.traffic.TrafficRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class OptimizeNetwork {
    private static final StreamCodec<FriendlyByteBuf, OptimizeSettings> SETTINGS =
            StreamCodec.of(OptimizeNetwork::writeSettings, OptimizeNetwork::readSettings);
    private static final RealRailPayload<Long> REQUEST = new RealRailPayload<>("request_depot_settings", ByteBufCodecs.VAR_LONG);
    private static final RealRailPayload<Long> RESET = new RealRailPayload<>("reset_depot_settings", ByteBufCodecs.VAR_LONG);
    private static final RealRailPayload<Update> UPDATE = new RealRailPayload<>("update_depot_settings", Update.CODEC);
    private static final RealRailPayload<Sync> SYNC = new RealRailPayload<>("sync_depot_settings", Sync.CODEC);

    private OptimizeNetwork() {
    }

    static void register(PayloadRegistrar registrar) {
        REQUEST.serverbound(registrar, (player, depotId) -> sync(player, depotId, "已读取车厂配置"));
        UPDATE.serverbound(registrar, OptimizeNetwork::update);
        RESET.serverbound(registrar, (player, depotId) -> {
            OptimizeConfigStore.reset(depotId);
            DepotSettingsResolver.invalidate();
            sync(player, depotId, "已恢复全局默认");
        });
        SYNC.clientbound(registrar, state -> ClientPacketHandler.onOptimizeSync(state.depotId(), state.settings(), state.status()));
    }

    public static void requestSettings(long depotId) {
        REQUEST.sendToServer(depotId);
    }

    public static void sendUpdate(long depotId, OptimizeSettings settings, boolean refreshRoutes) {
        UPDATE.sendToServer(new Update(depotId, settings, refreshRoutes));
    }

    public static void sendReset(long depotId) {
        RESET.sendToServer(depotId);
    }

    private static void update(ServerPlayer player, Update update) {
        final long depotId = update.depotId();
        final OptimizeSettings settings = update.settings();
        final boolean midLineChanged = DepotSettingsResolver.settingsForDepot(depotId).midLineDepot != settings.midLineDepot;
        OptimizeConfigStore.put(depotId, settings);
        if (midLineChanged) {
            MidLineDepotService.onToggle(depotId, settings.midLineDepot);
        }
        DepotSettingsResolver.invalidate();
        final boolean refreshed = update.refreshRoutes() && RouteRefresher.refreshDepot(TrafficRegistry.data(), depotId);
        sync(player, depotId, refreshed ? "已保存并请求重新生成车厂路径" : "已保存车厂配置");
    }

    private static void sync(ServerPlayer player, long depotId, String status) {
        SYNC.sendToPlayer(player, new Sync(depotId, DepotSettingsResolver.settingsForDepot(depotId),
                status + "。当前在线列车 " + TrafficRegistry.size() + " 列，已配置车厂 " + OptimizeConfigStore.size() + " 个"));
    }

    private static void writeSettings(FriendlyByteBuf buffer, OptimizeSettings settings) {
        buffer.writeBoolean(settings.dynamicBlockRelease);
        buffer.writeDouble(settings.safetyMarginMeters);
        buffer.writeBoolean(settings.routeOptimize);
        buffer.writeDouble(settings.pathLookaheadMeters);
        buffer.writeBoolean(settings.departureGuard);
        buffer.writeInt(settings.dwellThresholdSeconds);
        buffer.writeInt(settings.conflictWindowSeconds);
        buffer.writeInt(settings.maxHoldSeconds);
        buffer.writeBoolean(settings.midLineDepot);
        buffer.writeInt(settings.deployLeadMinutes);
        buffer.writeInt(settings.idleReturnMinutes);
    }

    private static OptimizeSettings readSettings(FriendlyByteBuf buffer) {
        final OptimizeSettings settings = new OptimizeSettings();
        settings.dynamicBlockRelease = buffer.readBoolean();
        settings.safetyMarginMeters = buffer.readDouble();
        settings.routeOptimize = buffer.readBoolean();
        settings.pathLookaheadMeters = buffer.readDouble();
        settings.departureGuard = buffer.readBoolean();
        settings.dwellThresholdSeconds = buffer.readInt();
        settings.conflictWindowSeconds = buffer.readInt();
        settings.maxHoldSeconds = buffer.readInt();
        settings.midLineDepot = buffer.readBoolean();
        settings.deployLeadMinutes = buffer.readInt();
        settings.idleReturnMinutes = buffer.readInt();
        return settings;
    }

    private record Update(long depotId, OptimizeSettings settings, boolean refreshRoutes) {
        private static final StreamCodec<FriendlyByteBuf, Update> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Update::depotId, SETTINGS, Update::settings,
                ByteBufCodecs.BOOL, Update::refreshRoutes, Update::new);
    }

    private record Sync(long depotId, OptimizeSettings settings, String status) {
        private static final StreamCodec<FriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Sync::depotId, SETTINGS, Sync::settings,
                ByteBufCodecs.stringUtf8(256), Sync::status, Sync::new);
    }
}
