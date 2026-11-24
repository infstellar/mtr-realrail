package mtr.realrail.client;

import org.mtr.libraries.gg.essential.elementa.UIComponent;
import org.mtr.libraries.gg.essential.elementa.constraints.PixelConstraint;
import org.mtr.libraries.gg.essential.elementa.constraints.RelativeConstraint;
import org.mtr.libraries.gg.essential.elementa.constraints.SiblingConstraint;
import org.mtr.widget.ButtonComponent;

public final class MtrScreenButtons {
    private MtrScreenButtons() {
    }

    public static ButtonComponent add(UIComponent parent, String text, Runnable onClick) {
        final ButtonComponent button = new ButtonComponent(true);
        button.setText(text);
        button.onClick(onClick);
        button.setChildOf(parent).setY(new SiblingConstraint(4)).setWidth(new RelativeConstraint())
                .setHeight(new PixelConstraint(20));
        return button;
    }

    public interface DepotOptions {
        void mtrrealrail$openOptions();
    }
}
