package one.devos.nautical.template.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import one.devos.nautical.template.TemplateMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This is an Example mixin that prints out `This line is printed by the Nautical template mod mixin` on initialization
 * of the TitleScreen, but this mixin can be replaced with any other thing you want to mixin.
 * <p>
 * Mixins **must** be in Java! They **cannot** be in Kotlin!
 */
@Mixin(TitleScreen.class)
public class ExampleMixin {
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        TemplateMod.INSTANCE.getLOGGER().info("This line is printed by the Nautical template mod mixin!");
    }
}