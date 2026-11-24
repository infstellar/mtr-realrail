package mtr.realrail.mixin;

import mtr.realrail.client.MtrScreenButtons;
import mtr.realrail.client.StationScreenBridge;
import org.mtr.core.data.Station;
import org.mtr.screen.StationScreen;
import org.mtr.screen.WindowBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StationScreen.class, remap = false)
abstract class EditStationScreenMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mtrrealrail$addStationDispatch(Station station, WindowBase previous, CallbackInfo callback) {
        if (station.savedRails.size() >= 2) {
            final long stationId = station.getId();
            StationScreenBridge.bind(stationId, MtrScreenButtons.add(
                    ((NameColorScreenAccessor) this).mtrrealrail$getFirstTab(),
                    "站台智能调度：读取中", () -> StationScreenBridge.toggle(stationId)));
        }
    }
}
