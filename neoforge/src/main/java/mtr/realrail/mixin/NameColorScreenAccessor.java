package mtr.realrail.mixin;

import org.mtr.libraries.gg.essential.elementa.components.ScrollComponent;
import org.mtr.screen.NameColorDataScreenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NameColorDataScreenBase.class, remap = false)
public interface NameColorScreenAccessor {
    @Accessor("firstTabScrollComponent")
    ScrollComponent mtrrealrail$getFirstTab();
}
