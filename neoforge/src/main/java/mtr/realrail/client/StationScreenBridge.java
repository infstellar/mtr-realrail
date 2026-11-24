package mtr.realrail.client;

import mtr.realrail.PlatformNetwork;
import org.mtr.widget.ButtonComponent;

public final class StationScreenBridge {
    private static long stationId;
    private static ButtonComponent button;
    private static boolean enabled;

    private StationScreenBridge() {
    }

    public static void bind(long id, ButtonComponent stationButton) {
        stationId = id;
        button = stationButton;
        button.setDisabled(true);
        PlatformNetwork.requestStation(id);
    }

    public static void toggle(long id) {
        if (id == stationId && button != null) {
            button.setDisabled(true);
            PlatformNetwork.toggleStation(id, !enabled);
        }
    }

    public static void onSync(long id, boolean state) {
        if (id == stationId && button != null) {
            enabled = state;
            button.setText("站台智能调度：" + (enabled ? "开启" : "关闭"));
            button.setDisabled(false);
        }
    }
}
