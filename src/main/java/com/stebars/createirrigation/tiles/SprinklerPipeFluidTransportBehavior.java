package com.stebars.createirrigation.tiles;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.jline.utils.Log;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllFluids;
import com.simibubi.create.AllTags;
import com.simibubi.create.AllTags.AllFluidTags;
import com.simibubi.create.content.contraptions.fluids.FluidFX;
import com.simibubi.create.content.contraptions.fluids.FluidReactions;
import com.simibubi.create.content.contraptions.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.contraptions.fluids.PipeConnection;
import com.simibubi.create.content.contraptions.fluids.actors.FluidSplashPacket;
import com.simibubi.create.content.contraptions.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.contraptions.fluids.potion.PotionFluid;
import com.simibubi.create.content.contraptions.fluids.potion.PotionFluidHandler;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.networking.AllPackets;
import com.simibubi.create.foundation.tileEntity.SmartTileEntity;
import com.simibubi.create.foundation.tileEntity.TileEntityBehaviour;
import com.simibubi.create.foundation.utility.VecHelper;

import static net.minecraft.state.properties.BlockStateProperties.WATERLOGGED;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.SpongeBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.BlazeEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.passive.WaterMobEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.EmptyFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.play.server.SPlaySoundEventPacket;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtils;
import net.minecraft.potion.Potions;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

class SprinklerPipeFluidTransportBehaviour extends FluidTransportBehaviour {
	// TODO maybe in future make it so fluid can flow towards sprinkler pipes even if there's no final "outlet", e.g. pipeline ends on a dirt block?
	// TODO redstone-activated sprinkler pipe
	// TODO make these affect items passing along on conveyor belts
	// TODO make milk and tea hydrate farmland, quench flames, etc.

	public final int SPRINKLE_EVERY = 50; // sprinkles every n times it gets in idle state
	public final int CONSUME_EVERY = 30; // consumes 1 unit of fluid every n sprinkles

	Field interfaces, phase, connectionSide;
	Class<?> updatePhaseEnum;
	Object WAIT_FOR_PUMPS, FLIP_FLOWS, IDLE;
	public int sprinkleTimer = SPRINKLE_EVERY; // TODO output a redstone signal when sprinkling?
	public int consumeTimer = 0; // consume first fluid unit, to prevent exploit with breaking and re-placing the sprinkler pipe

	public static Random random = new Random();

	public Fluid cachedFluid = null;
	// Caches last fluid that wasn't EmptyFluid, to prevent an exploit.
	// We reset consumeTimer to 0 when fluid changes. This prevents exploit where you can run 1 unit of water through it to get consumeTimer to max, then put potion through it
	public List<EffectInstance> cachedEffects = null;
	public FluidStack cachedPotion = null;
	// Caches potion and its effects, to speed up potion sprinklers

	public SprinklerPipeFluidTransportBehaviour(SmartTileEntity te) {
		super(te);

		try {
			// TODO once Create makes their fields public, I can remove this
			interfaces = FluidTransportBehaviour.class.getDeclaredField("interfaces");
			interfaces.setAccessible(true);
			phase = FluidTransportBehaviour.class.getDeclaredField("phase");
			phase.setAccessible(true);
			connectionSide = PipeConnection.class.getDeclaredField("side");
			connectionSide.setAccessible(true);

			updatePhaseEnum = Class.forName("com.simibubi.create.content.contraptions.fluids.FluidTransportBehaviour$UpdatePhase");

			Object[] enumConstants = updatePhaseEnum.getEnumConstants();
			WAIT_FOR_PUMPS = enumConstants[0];
			FLIP_FLOWS = enumConstants[1];
			IDLE = enumConstants[2];
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Map<Direction, PipeConnection> getInterfaces() {
		try {
			return (Map<Direction, PipeConnection>) interfaces.get(this);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public Object getPhase() {
		try {
			return phase.get(this);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public void setPhase(Object newVal) {
		try {
			phase.set(this, newVal);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Direction getConnectionSide(PipeConnection connection) {
		try {
			return (Direction) connectionSide.get(connection);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean canHaveFlowToward(BlockState state, Direction direction) {
		return FluidPipeBlock.isPipe(state)
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


	@Override
	public void tick() {
		super.tick();
		World world = getWorld();
		BlockPos pos = getPos();
		boolean onServer = !world.isClientSide || tileEntity.isVirtual();

		if (interfaces == null)
			return;
		Collection<PipeConnection> connections = getInterfaces().values();

		// Do not provide a lone pipe connection with its own flow input
		PipeConnection singleSource = null;

		//			if (onClient) {
		//				connections.forEach(connection -> {
		//					connection.visualizeFlow(pos);
		//					connection.visualizePressure(pos);
		//				});
		//			}

		if (getPhase() == WAIT_FOR_PUMPS) {
			setPhase(FLIP_FLOWS);
			return;
		}

		if (onServer) {
			boolean sendUpdate = false;
			for (PipeConnection connection : connections) {
				sendUpdate |= connection.flipFlowsIfPressureReversed();
				connection.manageSource(world, pos);
			}
			if (sendUpdate)
				tileEntity.notifyUpdate();
		}

		if (getPhase() == FLIP_FLOWS) { // UpdatePhase.FlipFlows
			setPhase(IDLE);
			return;
		}

		//if (onServer) {
			FluidStack availableFlow = FluidStack.EMPTY;
			FluidStack collidingFlow = FluidStack.EMPTY;

			for (PipeConnection connection : connections) {
				FluidStack fluidInFlow = connection.getProvidedFluid();
				if (fluidInFlow.isEmpty())
					continue;
				if (availableFlow.isEmpty()) {
					singleSource = connection;
					availableFlow = fluidInFlow;
					continue;
				}
				if (availableFlow.isFluidEqual(fluidInFlow)) {
					singleSource = null;
					availableFlow = fluidInFlow;
					continue;
				}
				collidingFlow = fluidInFlow;
				break;
			}

			if (!collidingFlow.isEmpty()) {
				FluidReactions.handlePipeFlowCollision(world, pos, availableFlow, collidingFlow);
				return;
			}

			Fluid fluid = availableFlow.getFluid();
			if (!availableFlow.isEmpty()) {
				if (sprinkleTimer != 0) {
					sprinkleTimer--;
				} else {
					boolean sprinkleNow = sprinkleFluid(availableFlow);
					sprinkleTimer = SPRINKLE_EVERY;
					if (sprinkleNow) {
						if (cachedFluid == null)
							cachedFluid = fluid;
						// reset consume timer every time the fluid changes, to prevent exploit
						else if (cachedFluid != fluid) {
							cachedFluid = fluid;
							consumeTimer = 0;
						}
						if (consumeTimer != 0) {
							consumeTimer--;
						} else {
							availableFlow.setAmount(availableFlow.getAmount() - 1);
							consumeTimer = CONSUME_EVERY;
						}
					}
				}
			}

			boolean sendUpdate = false;
			for (PipeConnection connection : connections) {
				FluidStack internalFluid = singleSource != connection ? availableFlow : FluidStack.EMPTY;
				Predicate<FluidStack> extractionPredicate =
						extracted -> canPullFluidFrom(extracted, tileEntity.getBlockState(), getConnectionSide(connection));
						sendUpdate |= connection.manageFlows(world, pos, internalFluid, extractionPredicate);
			}

			if (sendUpdate)
				tileEntity.notifyUpdate();
		//}

		for (PipeConnection connection : connections)
			connection.tickFlowProgress(world, pos);
	}

	// Tries to sprinkle fluid, returns whether it could
	// Caller checks that there is fluid, and times it
	boolean sprinkleFluid(FluidStack stack) {
		World world = tileEntity.getWorld();
		BlockPos pos = tileEntity.getBlockPos();
		BlockState state = tileEntity.getBlockState();
		FluidState fluidState = world.getFluidState(pos);

		if (fluidState.isEmpty()) {
			sprinkleFluidInAirEffects(stack, world, pos, state);
			return true;
		}

		// If sprinkling lava while in water, make sound and return true (so we lose water)
		if (state.hasProperty(WATERLOGGED)) {
			if (!stack.getFluid().is(FluidTags.LAVA))
				return false;
			world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.FIRE_EXTINGUISH,
					SoundCategory.BLOCKS, 0.5F,
					2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);
			return true;
		}

		// Not empty but also not waterlogged -- this should be impossible
		Log.error("Sprinkler pipe: pipe's block not empty of fluids but not waterlogged, unknown behaviour");
		return false;
	}

	private void sprinkleFluidInAirEffects(FluidStack stack, World world, BlockPos pos, BlockState state) {
		if (pos == null) {
			return; // sometimes happens on world load
		}
		Fluid fluid = stack.getFluid();
		if (Tags.Fluids.MILK.contains(fluid.getFluid())) {
			sprinkleMilkEffects(stack, world, pos, state);
		} else if (AllFluidTags.HONEY.matches(fluid)) { // NOTE this must be before water, bc it's also tagged as water
			sprinkleHoneyEffects(stack, world, pos, state);
		} else if (AllTags.forgeFluidTag("chocolate").contains(fluid)) { // NOTE this must be before water, bc it's also tagged as water
			sprinkleChocolateEffects(stack, world, pos, state);
		} else if (fluid.isSame(AllFluids.TEA.get())) {
			sprinkleBuildersTeaEffects(stack, world, pos, state);
			// TODO integrate Create with Simply Teas, allow bulk fluid crafting of those, and sprinkling effects
			// TODO integrate with suspicious stews
		} else if (fluid.isSame(AllFluids.POTION.get())) {
			sprinklePotionEffects(stack, world, pos, state);
		} else if (fluid.is(FluidTags.WATER)) { // NOTE this must be after chocolate and honey, bc they're also tagged as water
			sprinkleWaterEffects(stack, world, pos, state);
		} else if (fluid.is(FluidTags.LAVA)) {
			sprinkleLavaEffects(stack, world, pos, state);
		} else {
			Log.error("Sprinkler pipe: Unrecognized fluid: ", fluid.toString());
		}
	}

	public AxisAlignedBB getEntityAOE(BlockPos pos) {
		return  new AxisAlignedBB(
				pos.offset(-2, -5, -2),
				pos.offset(2, 1, 2));
	}


	/////////////////////// WATER

	private void sprinkleWaterEffects(FluidStack stack, World world, BlockPos pos, BlockState state) {
		FluidFX.splash(pos, stack);

		for (Entity entity : world.getEntities((Entity)null, new AxisAlignedBB(
				pos.offset(-1, -5, -1),
				pos.offset(1, 1, 1)))) {
			// Extinguish entities on fire
			if (entity.isOnFire() && random.nextInt(2) == 0) {
				entity.clearFire();
				world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
						SoundEvents.FIRE_EXTINGUISH,
						SoundCategory.BLOCKS, 1.0F,
						2.0F + (world.random.nextFloat() - world.random.nextFloat()) * 0.5F);
			}

			// Fish can survive while sprinkled
			if (entity instanceof WaterMobEntity) {
				((WaterMobEntity) entity).setAirSupply(300);
				continue;
			}

			// Hurt water-sensitive entities
			if (!(entity instanceof LivingEntity))
				continue;
			LivingEntity livingEntity = (LivingEntity) entity;
			if (livingEntity.isSensitiveToWater() && random.nextInt(2) == 0) {
				livingEntity.hurt(DamageSource.DROWN, 1.0F);
			}
		}

		// Blocks: sprinkle top layer, then go down in column until stopped by a block
		for (int columnX = -1; columnX < 2; columnX++)
			for (int columnZ = -1; columnZ < 2; columnZ++) {
				if (columnX != 0 || columnZ != 0)
					sprinkleWaterOnBlock(pos.offset(columnX, 0, columnZ),
							world);
				sprinkleWaterColumn(pos.offset(columnX, -1, columnZ),
						world);
			}
		sprinkleWaterOnBlock(pos.above(), world);
	}

	public void sprinkleWaterColumn(BlockPos top, World world) {
		for (int yDown = 0; yDown < 5; yDown++) {
			BlockPos pos = top.below(yDown);
			boolean canPass = sprinkleWaterOnBlock(pos, world);
			if (!canPass) return;
		}
	}

	// Sprinkle water on one block, returning true if water can pass through
	public boolean sprinkleWaterOnBlock(BlockPos pos, World world) {
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		FluidState fluidState = state.getFluidState();

		// If pouring on fluid, do nothing, except maybe play sound if it's lava
		if (!fluidState.isEmpty()) {
			if (random.nextInt(50) == 0 && fluidState.is(FluidTags.LAVA))
				world.playSound((PlayerEntity)null, pos, SoundEvents.LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 1.0F);
			return false;
		}

		if (block instanceof FluidPipeBlock)
			return true;

		// Extinguish fires and campfires
		if (block instanceof CampfireBlock) {
			if (CampfireBlock.isLitCampfire(state) && random.nextInt(2) == 0)
				CampfireBlock.dowse(world, pos, state);
			return true;
		}
		if (block.equals(Blocks.FIRE)) {
			if (random.nextInt(2) == 0) {
				world.playSound((PlayerEntity)null, pos, SoundEvents.FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 1.0F);
				world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
			return false;
		}

		if (block instanceof WallTorchBlock) {
			if (random.nextInt(16) == 0) {
				Block.dropResources(state,  world,  pos);
				world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				world.playSound((PlayerEntity)null, pos, SoundEvents.FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 1.0F);
			}
			return true;
		}

		// Hydrate farmland
		if (block instanceof FarmlandBlock) {
			int moisture = state.getValue(BlockStateProperties.MOISTURE);
			if (moisture < 7)
				world.setBlock(pos,
						state.setValue(FarmlandBlock.MOISTURE, moisture + 1),
						Constants.BlockFlags.DEFAULT_AND_RERENDER);
			return false;
		}

		// Wet sponges
		if (block instanceof SpongeBlock) {
			if (random.nextInt(16) == 0)
				world.setBlock(pos, Blocks.WET_SPONGE.defaultBlockState(), 2);
			return false;
		}

		// Otherwise, stop if it's a solid block
		return block.isAir(state, world, pos);
	}


	/////////////////////// LAVA

	private void sprinkleLavaEffects(FluidStack stack, World world, BlockPos pos, BlockState state) {
		FluidFX.splash(pos, stack);

		// Light entities on fire
		for (Entity entity : world.getEntities((Entity)null, new AxisAlignedBB(
				pos.offset(-1, -5, -1),
				pos.offset(1, 1, 1)))) {
			if (random.nextInt(2) == 0 && !entity.fireImmune()) {
				entity.setSecondsOnFire(4);
			}
		}

		// Blocks: sprinkle top layer, then go down in column until stopped by a block
		for (int columnX = -1; columnX < 2; columnX++)
			for (int columnZ = -1; columnZ < 2; columnZ++) {
				if (columnX != 0 || columnZ != 0)
					sprinkleLavaOnBlock(pos.offset(columnX, 0, columnZ),
							world);
				sprinkleLavaColumn(pos.offset(columnX, -1, columnZ),
						world);
			}
		sprinkleLavaOnBlock(pos.above(), world);
	}

	public void sprinkleLavaColumn(BlockPos top, World world) {
		for (int yDown = 0; yDown < 5; yDown++) {
			BlockPos pos = top.below(yDown);
			boolean canPass = sprinkleLavaOnBlock(pos, world);
			if (!canPass) return;
		}
	}

	// Sprinkle lava on one block, returning true if lava can pass through
	public boolean sprinkleLavaOnBlock(BlockPos pos, World world) {
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		FluidState fluidState = state.getFluidState();

		// If pouring on fluid other than lava, only make sounds
		if (!fluidState.isEmpty()) {
			if (random.nextInt(50) == 0 && !fluidState.is(FluidTags.LAVA))
				world.playSound((PlayerEntity)null, pos, SoundEvents.LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 1.0F);
			return false;
		}

		if (block instanceof FluidPipeBlock)
			return true;

		// Start fires
		if (random.nextInt(32) == 0 && FireBlock.canBePlacedAt(world, pos, Direction.UP)) {
			world.setBlock(pos, Blocks.FIRE.defaultBlockState(), 11);
			return true;
		}

		// Dehydrate farmland
		if (block instanceof FarmlandBlock) {
			int moisture = state.getValue(BlockStateProperties.MOISTURE);
			if (moisture > 0)
				world.setBlock(pos,
						state.setValue(FarmlandBlock.MOISTURE, moisture - 1),
						Constants.BlockFlags.DEFAULT_AND_RERENDER);
			return false;
		}

		// Dry sponges
		if (block instanceof SpongeBlock) {
			if (random.nextInt(16) == 0)
				world.setBlock(pos, Blocks.SPONGE.defaultBlockState(), 2);
			return false;
		}

		// Otherwise, stop if it's a solid block
		return block.isAir(state, world, pos);
	}


	/////////////////////// BUILDER'S TEA

	private void sprinkleBuildersTeaEffects(FluidStack stack, World world, BlockPos pos, BlockState state) {
		//fluidSplashParticles(world, stack, pos, 100); // TODO not working because only run server-side

		List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, getEntityAOE(pos),
				LivingEntity::isAffectedByPotions);
		for (LivingEntity livingEntity : list) {
			livingEntity.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED,
					15 * 20 // duration in ticks
					));
		}
	}


	/////////////////////// MILK

	private void sprinkleMilkEffects(FluidStack stack, World world, BlockPos pos, BlockState state) {
		//fluidSplashParticles(world, stack, pos, 100); // TODO not working because only run server-side
		if (world.isClientSide)
			makeFlameParticles(world, stack, pos, 10); // never runs

		List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, getEntityAOE(pos),
				LivingEntity::isAffectedByPotions);
		ItemStack curativeItem = new ItemStack(Items.MILK_BUCKET);
		for (LivingEntity livingEntity : list) {
			livingEntity.curePotionEffects(curativeItem);
		}
	}	

	/////////////////////// POTIONS

	private void sprinklePotionEffects(FluidStack stack, World world, BlockPos pos, BlockState state) {
		if (!world.isClientSide) {
			ItemStack bottle = PotionFluidHandler.fillBottle(new ItemStack(Items.GLASS_BOTTLE), stack);
			Potion potion = PotionUtils.getPotion(bottle);
			this.createPotionCloud(world, pos, bottle, potion);
		}

		if (cachedPotion == null || cachedEffects == null || cachedPotion != stack) {
			cachedPotion = stack;
			FluidStack copy = stack.copy();
			copy.setAmount(250);
			ItemStack bottle = PotionFluidHandler.fillBottle(new ItemStack(Items.GLASS_BOTTLE), stack);
			cachedEffects = PotionUtils.getMobEffects(bottle);
		}

		if (cachedEffects.isEmpty())
			return;

		List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, getEntityAOE(pos),
				LivingEntity::isAffectedByPotions);
		for (LivingEntity livingEntity : list) {
			for (EffectInstance effectinstance : cachedEffects) {
				Effect effect = effectinstance.getEffect();

				if (effect.isInstantenous()) {
					effect.applyInstantenousEffect(null, null, livingEntity, effectinstance.getAmplifier(), 0.5D);
				} else {
					int modifiedDuration = (int)(0.2 * (double)effectinstance.getDuration()) + 2;
					livingEntity.addEffect(new EffectInstance(effect, modifiedDuration, effectinstance.getAmplifier(), effectinstance.isAmbient(), effectinstance.isVisible()));
				}
			}
		}
	}

	public AxisAlignedBB getPotionAOE(BlockPos pos) {
		return  new AxisAlignedBB(
				pos.offset(-3, -3, -3),
				pos.offset(3, 3, 3));
	}

	private void createPotionCloud(World world, BlockPos pos, ItemStack p_190542_1_, Potion p_190542_2_) {
		PotionSprinkleEntity areaeffectcloudentity = new PotionSprinkleEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

		areaeffectcloudentity.setRadius(0.5F);
		areaeffectcloudentity.setPotion(p_190542_2_);

		CompoundNBT compoundnbt = p_190542_1_.getTag();
		if (compoundnbt != null && compoundnbt.contains("CustomPotionColor", 99)) {
			areaeffectcloudentity.setFixedColor(compoundnbt.getInt("CustomPotionColor"));
		}

		world.addFreshEntity(areaeffectcloudentity);
	}


	/////////////////////// CHOCOLATE

	private void sprinkleChocolateEffects(FluidStack stack, World world, BlockPos pos, BlockState state) {
		FluidFX.splash(pos, stack);

		List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, getEntityAOE(pos),
				LivingEntity::isAffectedByPotions);
		for (LivingEntity livingEntity : list) {
			livingEntity.addEffect(new EffectInstance(Effects.SATURATION,
					2 * 20 // duration in ticks
					));
			livingEntity.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN,
					10 * 20 // duration in ticks
					));
		}

		world.playSound((PlayerEntity)null, pos, SoundEvents.BEEHIVE_DRIP, SoundCategory.BLOCKS, 1.0F, 1.0F);
	}


	/////////////////////// HONEY

	private void sprinkleHoneyEffects(FluidStack stack, World world, BlockPos pos, BlockState state) {
		FluidFX.splash(pos, stack);

		List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, getEntityAOE(pos),
				LivingEntity::isAffectedByPotions);
		for (LivingEntity livingEntity : list) {
			livingEntity.addEffect(new EffectInstance(Effects.REGENERATION,
					10 * 20 // duration in ticks
					));
			livingEntity.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN,
					10 * 20, // duration in ticks
					1 // amplifier
					));
		}

		world.playSound((PlayerEntity)null, pos, SoundEvents.BEEHIVE_DRIP, SoundCategory.BLOCKS, 1.0F, 1.0F);
	}

	public void fluidSplashParticles(World world, FluidStack stack, BlockPos blockPos, int num) {
		// taken from Create's FluidFX, which has int set to 20
		if (stack.isEmpty())
			return;

		FluidState defaultState = stack.getFluid().defaultFluidState();
		if (defaultState != null && !defaultState.isEmpty()) {
			BlockParticleData blockParticleData = new BlockParticleData(ParticleTypes.BLOCK, defaultState.createLegacyBlock());
			Vector3d center = VecHelper.getCenterOf(blockPos);

			// Builder's tea gets to here, but particles don't show up

			for (int i = 0; i < num; i++) {
				Vector3d motion = VecHelper.offsetRandomly(Vector3d.ZERO, random, 10f); // .25f by default, changing to 1 bc it's all directions
				Vector3d pos = center.add(motion);
				world.addParticle(blockParticleData, pos.x, pos.y, pos.z, motion.x, motion.y, motion.z);
			}
		}
	}

	// TODO only in client
	@OnlyIn(Dist.CLIENT)
	public void makeFlameParticles(World world, FluidStack stack, BlockPos blockPos, int num) {
		for (int i = 0; i < num; i++) {
			double d0 = (double)blockPos.getX() + 0.5D;
			double d1 = (double)blockPos.getY() + 0.5D;
			double d2 = (double)blockPos.getZ() + 0.5D;
			world.addParticle(ParticleTypes.SMOKE,
					d0 + random.nextFloat() * 0.2,
					d1 + random.nextFloat() * 0.2,
					d2 + random.nextFloat() * 0.2,
					random.nextFloat() * .1,		    		  
					random.nextFloat() * .1,
					random.nextFloat() * .1
					);
		}
	}

	// TODO Future: maybe configurable spraying recipes, that also work with things on conveyor belts

	// Current state: all effects work. Particles don't yet work for tea or milk.
}