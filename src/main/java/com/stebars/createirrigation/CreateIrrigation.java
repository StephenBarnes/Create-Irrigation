package com.stebars.createirrigation;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.stebars.createirrigation.registration.BlockRegistrator;
import com.stebars.createirrigation.registration.TileRegistrator;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CreateIrrigation.modid)
public class CreateIrrigation {
    public static final String modid = "createirrigation";
    public static IEventBus MOD_EVENT_BUS;

    private final CreateRegistrate REGISTRATE = CreateRegistrate.lazy(modid).get();

    public static ItemGroup itemGroup = new ItemGroup(modid) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(BlockRegistrator.SPRINKLER_PIPE.get()); // Rather use sprinkler (not sprinkler pipe) once that exists
        }
    };

    public CreateIrrigation() {
        MOD_EVENT_BUS = FMLJavaModLoadingContext.get().getModEventBus();

        REGISTRATE.itemGroup(() -> itemGroup, "Create Irrigation");
        new BlockRegistrator(REGISTRATE).register();
        new TileRegistrator(REGISTRATE).register();

        /* TODO add ponders:
        OneTimeEventReceiver.addListener(MOD_EVENT_BUS, FMLClientSetupEvent.class, (event) -> {
            event.enqueueWork(PonderRegistrator::register);
        });*/
    }
}

