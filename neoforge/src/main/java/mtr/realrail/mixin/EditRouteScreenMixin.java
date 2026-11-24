package mtr.realrail.mixin;

import mtr.realrail.client.MtrScreenButtons;
import mtr.realrail.client.RouteDispatchScreen;
import net.minecraft.client.Minecraft;
import org.mtr.core.data.Route;
import org.mtr.screen.RouteScreen;
import org.mtr.screen.WindowBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RouteScreen.class, remap = false)
abstract class EditRouteScreenMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mtrrealrail$addRouteDispatch(Route route, WindowBase previous, CallbackInfo callback) {
        MtrScreenButtons.add(((NameColorScreenAccessor) this).mtrrealrail$getFirstTab(), "线路智能调度站台",
                () -> Minecraft.getInstance().setScreen(new RouteDispatchScreen((RouteScreen) (Object) this, route)));
    }
}
