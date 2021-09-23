package com.stebars.createirrigation.registration;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.repack.registrate.util.entry.TileEntityEntry;
import com.stebars.createirrigation.CreateIrrigation;
import com.stebars.createirrigation.tiles.SprinklerPipeTileEntity;
import com.stebars.createirrigation.util.Registration;

import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid= CreateIrrigation.modid, bus=Mod.EventBusSubscriber.Bus.FORGE)
public class TileRegistrator extends Registration {
    public static TileEntityEntry<SprinklerPipeTileEntity> SPRINKLER_PIPE;

    public TileRegistrator(CreateRegistrate r) {
        super(r);
    }

    @Override
    public void register() {
    	SPRINKLER_PIPE = Create.registrate()
    		.tileEntity("sprinkler_pipe", SprinklerPipeTileEntity::new)
    		.validBlocks(BlockRegistrator.SPRINKLER_PIPE)
    		.register();
    }
}
