package com.stebars.createirrigation.registration;

import static com.simibubi.create.foundation.data.ModelGen.customItemModel;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.simibubi.create.content.contraptions.fluids.PipeAttachmentModel;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Pointing;
import com.simibubi.create.repack.registrate.providers.DataGenContext;
import com.simibubi.create.repack.registrate.providers.RegistrateBlockstateProvider;
import com.simibubi.create.repack.registrate.util.entry.BlockEntry;
import com.simibubi.create.repack.registrate.util.nullness.NonNullBiConsumer;
import com.stebars.createirrigation.blocks.SprinklerPipeBlock;
import com.stebars.createirrigation.util.Registration;

import net.minecraft.block.Block;
import net.minecraft.state.BooleanProperty;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.Direction.AxisDirection;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.MultiPartBlockStateBuilder;

public class BlockRegistrator extends Registration {
    public static BlockEntry<SprinklerPipeBlock> SPRINKLER_PIPE;

    public BlockRegistrator(CreateRegistrate r) {
        super(r);
    }

    @Override
    public void register() {
    	SPRINKLER_PIPE = REGISTRATE.block("sprinkler_pipe", SprinklerPipeBlock::new)
    		.initialProperties(SharedProperties::softMetal)
    		.blockstate(sprinklerPipeState())
    		.onRegister(CreateRegistrate.blockModel(() -> PipeAttachmentModel::new))
    		.item()
    		.transform(customItemModel())
    		.register();
    }

	public static <P extends SprinklerPipeBlock> NonNullBiConsumer<DataGenContext<Block, SprinklerPipeBlock>, RegistrateBlockstateProvider> sprinklerPipeState() {
		return (c, p) -> {
			String path = "block/" + c.getName();

			String LU = "lu";
			String RU = "ru";
			String LD = "ld";
			String RD = "rd";
			String LR = "lr";
			String UD = "ud";
			String NONE = "none";

			List<String> orientations = ImmutableList.of(LU, RU, LD, RD, LR, UD, NONE);
			Map<String, Pair<Integer, Integer>> uvs = ImmutableMap.<String, Pair<Integer, Integer>>builder()
				.put(LU, Pair.of(8, 12))
				.put(RU, Pair.of(0, 12))
				.put(LD, Pair.of(12, 8))
				.put(RD, Pair.of(8, 8))
				.put(LR, Pair.of(4, 12))
				.put(UD, Pair.of(0, 8))
				.put(NONE, Pair.of(12, 12))
				.build();

			Map<Axis, ResourceLocation> coreTemplates = new IdentityHashMap<>();
			Map<Pair<String, Axis>, ModelFile> coreModels = new HashMap<>();

			for (Axis axis : Iterate.axes)
				coreTemplates.put(axis, p.modLoc(path + "/core_" + axis.getSerializedName()));
			ModelFile end = AssetLookup.partialBaseModel(c, p, "end");

			for (Axis axis : Iterate.axes) {
				ResourceLocation parent = coreTemplates.get(axis);
				for (String s : orientations) {
					Pair<String, Axis> key = Pair.of(s, axis);
					String modelName = path + "/" + s + "_" + axis.getSerializedName();
					coreModels.put(key, p.models()
						.withExistingParent(modelName, parent)
						.element()
						.from(4, 4, 4)
						.to(12, 12, 12)
						.face(Direction.get(AxisDirection.POSITIVE, axis))
						.end()
						.face(Direction.get(AxisDirection.NEGATIVE, axis))
						.end()
						.faces((d, builder) -> {
							Pair<Integer, Integer> pair = uvs.get(s);
							float u = pair.getKey();
							float v = pair.getValue();
							if (d == Direction.UP)
								builder.uvs(u, v + 4, u + 4, v);
							else if (d.getAxisDirection() == AxisDirection.POSITIVE)
								builder.uvs(u + 4, v, u, v + 4);
							else
								builder.uvs(u, v, u + 4, v + 4);
							builder.texture("#0");
						})
						.end());
				}
			}

			MultiPartBlockStateBuilder builder = p.getMultipartBuilder(c.get());
			for (Direction d : Iterate.directions)
				builder.part()
					.modelFile(end)
					.rotationX(d == Direction.UP ? 0 : d == Direction.DOWN ? 180 : 90)
					.rotationY((int) (d.toYRot() + 180) % 360)
					.addModel()
					.condition(SprinklerPipeBlock.PROPERTY_BY_DIRECTION.get(d), true)
					.end();

			for (Axis axis : Iterate.axes) {
				putPart(coreModels, builder, axis, LU, true, false, true, false);
				putPart(coreModels, builder, axis, RU, true, false, false, true);
				putPart(coreModels, builder, axis, LD, false, true, true, false);
				putPart(coreModels, builder, axis, RD, false, true, false, true);
				putPart(coreModels, builder, axis, UD, true, true, false, false);
				putPart(coreModels, builder, axis, UD, true, false, false, false);
				putPart(coreModels, builder, axis, UD, false, true, false, false);
				putPart(coreModels, builder, axis, LR, false, false, true, true);
				putPart(coreModels, builder, axis, LR, false, false, true, false);
				putPart(coreModels, builder, axis, LR, false, false, false, true);
				putPart(coreModels, builder, axis, NONE, false, false, false, false);
			}
		};
	}

	private static void putPart(Map<Pair<String, Axis>, ModelFile> coreModels, MultiPartBlockStateBuilder builder,
		Axis axis, String s, boolean up, boolean down, boolean left, boolean right) {
		Direction positiveAxis = Direction.get(AxisDirection.POSITIVE, axis);
		Map<Direction, BooleanProperty> propertyMap = SprinklerPipeBlock.PROPERTY_BY_DIRECTION;
		builder.part()
			.modelFile(coreModels.get(Pair.of(s, axis)))
			.addModel()
			.condition(propertyMap.get(Pointing.UP.getCombinedDirection(positiveAxis)), up)
			.condition(propertyMap.get(Pointing.LEFT.getCombinedDirection(positiveAxis)), left)
			.condition(propertyMap.get(Pointing.RIGHT.getCombinedDirection(positiveAxis)), right)
			.condition(propertyMap.get(Pointing.DOWN.getCombinedDirection(positiveAxis)), down)
			.end();
	}
}
