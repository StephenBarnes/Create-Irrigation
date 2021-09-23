package com.stebars.createirrigation.tiles;

import java.util.List;

import org.jline.utils.Log;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.contraptions.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.contraptions.fluids.pipes.FluidPipeTileEntity;
import com.simibubi.create.content.contraptions.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.contraptions.relays.elementary.BracketedTileEntityBehaviour;
import com.simibubi.create.foundation.advancement.AllTriggers;
import com.simibubi.create.foundation.tileEntity.SmartTileEntity;
import com.simibubi.create.foundation.tileEntity.TileEntityBehaviour;

import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;

public class SprinklerPipeTileEntity extends FluidPipeTileEntity {

	public SprinklerPipeTileEntity(TileEntityType<?> tileEntityTypeIn) {
		super(tileEntityTypeIn);
	}
	
	@Override
	public void addBehaviours(List<TileEntityBehaviour> behaviours) {
		behaviours.add(new SprinklerPipeFluidTransportBehaviour(this));
		//behaviours.add(new StandardPipeFluidTransportBehaviour(this));
		behaviours.add(new BracketedTileEntityBehaviour(this, this::canHaveBracket)
			.withTrigger(state -> AllTriggers.BRACKET_APPLY_TRIGGER.constructTriggerFor(state.getBlock())));
	}

	private boolean canHaveBracket(BlockState state) {
		return true;
	}
	

	/*class StandardPipeFluidTransportBehaviour extends FluidTransportBehaviour {

		public StandardPipeFluidTransportBehaviour(SmartTileEntity te) {
			super(te);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return (FluidPipeBlock.isPipe(state) || state.getBlock() instanceof EncasedPipeBlock)
				&& state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(direction));
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(IBlockDisplayReader world, BlockPos pos, BlockState state,
			Direction direction) {
			AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);

			if (attachment == AttachmentTypes.RIM && AllBlocks.ENCASED_FLUID_PIPE.has(state))
				return AttachmentTypes.RIM;

			BlockPos offsetPos = pos.relative(direction);
			if (!FluidPipeBlock.isPipe(world.getBlockState(offsetPos))) {
				FluidTransportBehaviour pipeBehaviour =
					TileEntityBehaviour.get(world, offsetPos, FluidTransportBehaviour.TYPE);
				if (pipeBehaviour != null
					&& pipeBehaviour.canHaveFlowToward(world.getBlockState(offsetPos), direction.getOpposite()))
					return AttachmentTypes.NONE;
			}

			if (attachment == AttachmentTypes.RIM && !FluidPipeBlock.shouldDrawRim(world, pos, state, direction))
				return AttachmentTypes.NONE;
			return attachment;
		}
		
		// so, we need to override tick() in here
		// but we can't, because it uses things that aren't visible
		// need to wait for Create to merge PR, and then somehow get that new version (not released as new version) in here as dependency
		// and, rather don't put the behaviour class right here, rather move it to its own file
		// I've copied in FluidTransportBehaviour and PipeConnection, as clones, since I can't use the ones in Create
		// but, this causes pipe rims to disappear, for unknown reason
		// Specifically, making this class extend my own FluidTransportBehaviour (instead of importing Create's) makes rims disappear
		// and, it still has no rim if I remove the AttachmentTypes.NONE stuff here; so it must be coming from FluidTransportBehaviour.getRenderedRimAttachment
		
	}*/

}
