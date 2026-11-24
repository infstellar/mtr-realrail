package mtr.realrail.client;

import mtr.realrail.MtrRealRail;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.mtr.libraries.gg.essential.elementa.components.input.AbstractTextInput;
import org.mtr.screen.DepotScreen;

@EventBusSubscriber(modid = MtrRealRail.MOD_ID, value = Dist.CLIENT)
public final class OptimizeScreenHook {
    private OptimizeScreenHook() {
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof DepotScreen depot
                && OptimizeKeyMappings.OPEN_OPTIMIZE.matches(event.getKeyCode(), event.getScanCode())
                && !(depot.getWindow().getFocusedComponent() instanceof AbstractTextInput)) {
            ((MtrScreenButtons.DepotOptions) (Object) depot).mtrrealrail$openOptions();
            event.setCanceled(true);
        }
    }
}
