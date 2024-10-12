package one.devos.nautical.exposeplayers.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PlayerAdvancements.class)
public interface PlayerAdvancementsMixin {
    @Accessor
    Map<AdvancementHolder, AdvancementProgress> getProgress();
}
