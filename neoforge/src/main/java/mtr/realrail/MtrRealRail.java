package mtr.realrail;

import mtr.realrail.dispatch.TscDispatchBridge;
import mtr.realrail.traffic.DepotSettingsResolver;
import mtr.realrail.traffic.OptimizeConfigStore;
import mtr.realrail.traffic.PlatformDispatchStore;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.EventPriority;
import org.mtbridge.forge.BridgeCommands;
import org.mtbridge.forge.EmbeddedBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod(MtrRealRail.MOD_ID)
public final class MtrRealRail {
    public static final String MOD_ID = "mtrrealrail";
    public static final Logger LOGGER = LogManager.getLogger("MTR RealRail");
    private static volatile EmbeddedBridge bridge;
    private static volatile EmbeddedDispatchService dispatcher;

    public static EmbeddedDispatchService dispatcher() {
        return dispatcher;
    }

    public MtrRealRail(IEventBus modBus) {
        final Path config = configPath();
        RealRailConfig.INSTANCE.load(config);
        if (!Files.isRegularFile(config)) {
            RealRailConfig.INSTANCE.save(config);
        }
        modBus.addListener(MtrRealRail::registerPayloads);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MtrRealRail::onServerStarted);
        NeoForge.EVENT_BUS.addListener(MtrRealRail::onServerTick);
        NeoForge.EVENT_BUS.addListener(MtrRealRail::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(MtrRealRail::onServerStopping);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        NodeModeNetwork.register(registrar);
        OptimizeNetwork.register(registrar);
        PlatformNetwork.register(registrar);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        RealRailConfig.INSTANCE.load(configPath());
        final Path saveDir = event.getServer().getWorldPath(LevelResource.ROOT).resolve(MOD_ID);
        NodeModeStore.load(saveDir.resolve("nodes.json"));
        OptimizeConfigStore.load(saveDir.resolve("optimize.json"));
        PlatformDispatchStore.load(saveDir.resolve("platforms.json"));
        TscDispatchBridge.clear();
        bridge = new EmbeddedBridge();
        bridge.start(event.getServer());
        dispatcher = new EmbeddedDispatchService();
        dispatcher.start(bridge, saveDir.resolve("dispatch"));
        TscDispatchBridge.start();
        DepotSettingsResolver.invalidate();
        LOGGER.info("[{}] 已从 {} 读取 {} 个轨道节点和 {} 个车厂的设置",
                MOD_ID, saveDir, NodeModeStore.size(), OptimizeConfigStore.size());
        if (Boolean.getBoolean("mtrrealrail.verifyMixins")) {
            verifyMixinTargets();
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        final EmbeddedBridge current = bridge;
        if (current != null) {
            current.tick(event.getServer());
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        BridgeCommands.register(event.getDispatcher(), () -> bridge);
        final EmbeddedDispatchService current = dispatcher;
        if (current != null) {
            current.register(event.getDispatcher());
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        final EmbeddedDispatchService oldDispatcher = dispatcher;
        dispatcher = null;
        if (oldDispatcher != null) {
            oldDispatcher.close();
        }
        final EmbeddedBridge oldBridge = bridge;
        bridge = null;
        if (oldBridge != null) {
            oldBridge.stop();
        }
        TscDispatchBridge.stop();
    }

    private static void verifyMixinTargets() {
        for (final Class<?> target : new Class<?>[]{
                org.mtr.core.data.Vehicle.class, org.mtr.core.data.Rail.class,
                org.mtr.core.data.RoutePlatformData.class,
                org.mtr.core.generated.data.VehicleSchema.class,
                org.mtr.core.generated.data.NameColorDataBaseSchema.class}) {
            LOGGER.info("[{}] 已加载 Mixin 目标 {}", MOD_ID, target.getName());
        }
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve("mtrrealrail.properties");
    }
}
