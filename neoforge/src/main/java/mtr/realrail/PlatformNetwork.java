package mtr.realrail;

import mtr.realrail.client.ClientPacketHandler;
import mtr.realrail.traffic.PlatformDispatchStore;
import mtr.realrail.traffic.RouteRefresher;
import mtr.realrail.traffic.TrafficRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Set;
import java.util.TreeSet;

public final class PlatformNetwork {
    private static final StreamCodec<FriendlyByteBuf, Set<Long>> PLATFORM_IDS =
            ByteBufCodecs.collection(ignored -> new TreeSet<>(), ByteBufCodecs.VAR_LONG);
    private static final RealRailPayload<Long> REQUEST_STATION = new RealRailPayload<>("request_station_dispatch", ByteBufCodecs.VAR_LONG);
    private static final RealRailPayload<Long> REQUEST_ROUTE = new RealRailPayload<>("request_route_dispatch", ByteBufCodecs.VAR_LONG);
    private static final RealRailPayload<Station> TOGGLE_STATION = new RealRailPayload<>("toggle_station_dispatch", Station.CODEC);
    private static final RealRailPayload<RouteUpdate> UPDATE_ROUTE = new RealRailPayload<>("update_route_dispatch", RouteUpdate.CODEC);
    private static final RealRailPayload<RouteSync> SYNC = new RealRailPayload<>("sync_platform_dispatch", RouteSync.CODEC);

    private PlatformNetwork() {
    }

    static void register(PayloadRegistrar registrar) {
        REQUEST_STATION.serverbound(registrar, PlatformNetwork::syncStation);
        TOGGLE_STATION.serverbound(registrar, (player, station) -> {
            PlatformDispatchStore.setStationEnabled(station.id(), station.enabled());
            syncStation(player, station.id());
        });
        REQUEST_ROUTE.serverbound(registrar, PlatformNetwork::syncRoute);
        UPDATE_ROUTE.serverbound(registrar, PlatformNetwork::update);
        SYNC.clientbound(registrar, state -> {
            final Route route = state.route();
            ClientPacketHandler.onPlatformSync(route.id(), route.enabled(), route.stationId(), route.platformIds(), state.status());
        });
    }

    public static void requestStation(long stationId) {
        REQUEST_STATION.sendToServer(stationId);
    }

    public static void toggleStation(long stationId, boolean enabled) {
        TOGGLE_STATION.sendToServer(new Station(stationId, enabled));
    }

    public static void requestRoute(long routeId) {
        REQUEST_ROUTE.sendToServer(routeId);
    }

    public static void updateRoute(long routeId, boolean enabled, long stationId, Set<Long> platformIds, boolean refreshRoutes) {
        UPDATE_ROUTE.sendToServer(new RouteUpdate(new Route(routeId, enabled, stationId, platformIds), refreshRoutes));
    }

    private static void update(ServerPlayer player, RouteUpdate update) {
        final Route route = update.route();
        PlatformDispatchStore.setRouteEnabled(route.id(), route.enabled());
        if (route.stationId() != 0) {
            PlatformDispatchStore.setAllowedPlatforms(route.id(), route.stationId(), route.platformIds());
        }
        final boolean refreshed = update.refreshRoutes() && RouteRefresher.refreshForRoute(TrafficRegistry.data(), route.id());
        sync(player, route, refreshed ? "已保存并请求重新生成车厂路径" : "已保存线路配置");
    }

    private static void syncStation(ServerPlayer player, long stationId) {
        sync(player, new Route(0, PlatformDispatchStore.isStationEnabled(stationId), stationId, Set.of()), "车站配置已同步");
    }

    private static void syncRoute(ServerPlayer player, long routeId) {
        final boolean enabled = PlatformDispatchStore.isRouteEnabled(routeId);
        sync(player, new Route(routeId, enabled, 0, Set.of()), "已读取线路配置");
        for (final long stationId : PlatformDispatchStore.stationIds(routeId)) {
            sync(player, new Route(routeId, enabled, stationId, PlatformDispatchStore.allowedPlatforms(routeId, stationId)),
                    "已读取线路配置");
        }
    }

    private static void sync(ServerPlayer player, Route route, String status) {
        SYNC.sendToPlayer(player, new RouteSync(route, status));
    }

    private record Station(long id, boolean enabled) {
        private static final StreamCodec<FriendlyByteBuf, Station> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Station::id, ByteBufCodecs.BOOL, Station::enabled, Station::new);
    }

    private record Route(long id, boolean enabled, long stationId, Set<Long> platformIds) {
        private static final StreamCodec<FriendlyByteBuf, Route> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Route::id, ByteBufCodecs.BOOL, Route::enabled,
                ByteBufCodecs.VAR_LONG, Route::stationId,
                PLATFORM_IDS, Route::platformIds, Route::new);
    }

    private record RouteUpdate(Route route, boolean refreshRoutes) {
        private static final StreamCodec<FriendlyByteBuf, RouteUpdate> CODEC = StreamCodec.composite(
                Route.CODEC, RouteUpdate::route, ByteBufCodecs.BOOL, RouteUpdate::refreshRoutes, RouteUpdate::new);
    }

    private record RouteSync(Route route, String status) {
        private static final StreamCodec<FriendlyByteBuf, RouteSync> CODEC = StreamCodec.composite(
                Route.CODEC, RouteSync::route, ByteBufCodecs.stringUtf8(128), RouteSync::status, RouteSync::new);
    }
}
