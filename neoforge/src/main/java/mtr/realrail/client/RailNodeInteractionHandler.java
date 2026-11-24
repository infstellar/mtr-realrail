package mtr.realrail.client;

import mtr.realrail.MtrRealRail;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.mtr.block.BlockNode;
import org.mtr.registry.Items;

@EventBusSubscriber(modid = MtrRealRail.MOD_ID, value = Dist.CLIENT)
public final class RailNodeInteractionHandler {
    private RailNodeInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() && event.getEntity().isHolding(Items.BRUSH.get())
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BlockNode) {
            RailScreenBridge.setPendingNodePos(event.getPos());
        }
    }
}
