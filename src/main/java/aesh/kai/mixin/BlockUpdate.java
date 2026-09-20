package aesh.kai.mixin;

import aesh.kai.network.UpdateCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NeighborUpdater.class)
public interface BlockUpdate {
	@Inject(method = "executeUpdate", at = @At(value = "HEAD"))
	private static void aesh$onNC(Level level, BlockState state, BlockPos pos, Block changedBlock, Orientation orientation, boolean movedByPiston, CallbackInfo ci) {
		UpdateCollector.record(level, pos.asLong(), UpdateCollector.UpdateType.NC);
	}

	@Inject(method = "executeShapeUpdate", at = @At(value = "HEAD"))
	private static void aesh$onPP(LevelAccessor level, Direction direction, BlockPos pos, BlockPos neighborPos, BlockState neighborState, int updateFlags, int updateLimit, CallbackInfo ci) {
		if(level instanceof Level _level)
			UpdateCollector.record(_level, pos.asLong(), UpdateCollector.UpdateType.PP);
	}
}