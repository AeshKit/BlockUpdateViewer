package aesh.kai.mixin;

import aesh.kai.network.UpdateCollector;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public class ComparatorUpdate {
    @WrapOperation(
            method = "updateNeighbourForOutputSignal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z")
    )
    private boolean aesh$onComparatorUpdate(BlockState instance, Object o, Operation<Boolean> original, @Local(name = "relativePos") BlockPos relativePos) {
        Level self = (Level)(Object)this;
        UpdateCollector.record(self, relativePos.asLong(), UpdateCollector.UpdateType.COMPARATOR);
        return original.call(instance, o);
    }
}