package mtr.realrail.mixin;

import mtr.realrail.NodeModeNetwork;
import mtr.realrail.client.MtrScreenButtons;
import mtr.realrail.client.RailScreenBridge;
import net.minecraft.core.BlockPos;
import org.mtr.generated.lang.TranslationProvider;
import org.mtr.libraries.gg.essential.elementa.components.ScrollComponent;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.screen.RailModifierScreen;
import org.mtr.widget.PreviewBoxComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RailModifierScreen.class, remap = false)
abstract class RailModifierScreenMixin {
    @Inject(method = "createMainComponents", at = @At("RETURN"))
    private void mtrrealrail$addNodeMode(int tab, TranslationProvider.TranslationHolder title,
            CallbackInfoReturnable<ObjectObjectImmutablePair<ScrollComponent, PreviewBoxComponent>> callback) {
        final BlockPos nodePos = RailScreenBridge.getPendingNodePos();
        if (tab == 0 && nodePos != null) {
            MtrScreenButtons.add(callback.getReturnValue().left(), "节点限速判定", () -> {
                RailScreenBridge.rememberRailScreen((RailModifierScreen) (Object) this);
                NodeModeNetwork.requestOpen(nodePos);
            });
        }
    }
}
