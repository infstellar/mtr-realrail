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
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod(MtrRealRail.MOD_ID)
public final class MtrRealRail {
    public static final String MOD_ID = "mtrrealrail";
    public static final Logger LOGGER = LogManager.getLogger("MTR RealRail");

    public MtrRealRail(IEventBus modBus) {
        final Path config = configPath();
        RealRailConfig.INSTANCE.load(config);
        if (!Files.isRegularFile(config)) {
            RealRailConfig.INSTANCE.save(config);
        }
        modBus.addListener(MtrRealRail::registerPayloads);
        NeoForge.EVENT_BUS.addListener(MtrRealRail::onServerStarted);
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
        if (TscDispatchBridge.isEnabled()) {
            TscDispatchBridge.start();
        }
        DepotSettingsResolver.invalidate();
        LOGGER.info("[{}] 已从 {} 读取 {} 个轨道节点和 {} 个车厂的设置",
                MOD_ID, saveDir, NodeModeStore.size(), OptimizeConfigStore.size());
        if (Boolean.getBoolean("mtrrealrail.verifyMixins")) {
            verifyMixinTargets();
        }
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
