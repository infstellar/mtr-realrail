package mtr.realrail.client;

import mtr.realrail.MtrRealRail;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MtrRealRail.MOD_ID, value = Dist.CLIENT)
public final class OptimizeKeyMappings {
    public static final KeyMapping OPEN_OPTIMIZE = new KeyMapping(
            "key.mtrrealrail.optimize", GLFW.GLFW_KEY_O, "key.categories.mtrrealrail");

    private OptimizeKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_OPTIMIZE);
    }
}
