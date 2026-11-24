package mtr.realrail.mixin;

import mtr.realrail.OptimizeNetwork;
import mtr.realrail.client.MtrScreenButtons;
import mtr.realrail.client.OptimizeScreenBridge;
import org.mtr.core.data.Depot;
import org.mtr.screen.DepotScreen;
import org.mtr.screen.WindowBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DepotScreen.class, remap = false)
abstract class EditDepotScreenMixin implements MtrScreenButtons.DepotOptions {
    @Unique private Depot mtrrealrail$depot;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mtrrealrail$addOptions(Depot depot, WindowBase previous, CallbackInfo callback) {
        mtrrealrail$depot = depot;
        MtrScreenButtons.add(((NameColorScreenAccessor) this).mtrrealrail$getFirstTab(),
                "优化线路配置", this::mtrrealrail$openOptions);
    }

    @Override
    public void mtrrealrail$openOptions() {
        OptimizeScreenBridge.rememberDepot((DepotScreen) (Object) this,
                mtrrealrail$depot.getId(), mtrrealrail$depot.getName());
        OptimizeScreenBridge.openOptimizeScreen();
        OptimizeNetwork.requestSettings(mtrrealrail$depot.getId());
    }
}
