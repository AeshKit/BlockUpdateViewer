package aesh.kai.client.mixin;

import aesh.kai.client.render.Renderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class RenderCloser {
    @Inject(method = "close", at = @At("RETURN"))
    private void aesh$onGameRendererClose(CallbackInfo ci) {
        Renderer.close();
    }
}