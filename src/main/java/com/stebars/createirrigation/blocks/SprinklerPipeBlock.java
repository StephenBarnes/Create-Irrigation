package com.stebars.createirrigation.blocks;

import com.simibubi.create.content.contraptions.fluids.pipes.FluidPipeBlock;
import com.stebars.createirrigation.registration.TileRegistrator;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUseContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

public class SprinklerPipeBlock extends FluidPipeBlock {

	public SprinklerPipeBlock(Properties properties) {
		super(properties);
		//this.registerDefaultState(super.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}
	
	@Override
	public ActionResultType onWrenched(BlockState state, ItemUseContext context) {
		if (tryRemoveBracket(context))
			return ActionResultType.SUCCESS;
		// unlike FluidPipeBlock, don't try to turn into glass pipe block here
		return ActionResultType.PASS;
	}

	@Override
	public TileEntity createTileEntity(BlockState state, IBlockReader world) {
		return TileRegistrator.SPRINKLER_PIPE.create();
	}

	@Override
	public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
			BlockRayTraceResult hit) {
		// Don't try to turn into encased block
		// Could make it so you can swap out sprinkler pipes with regular pipes, but that would conflict with
		// the attach-pipe action.
		return ActionResultType.PASS;
	}

}
