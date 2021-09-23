package com.stebars.createirrigation.registration;

import com.simibubi.create.foundation.ponder.PonderRegistry;
import com.simibubi.create.foundation.ponder.content.PonderTag;
import com.stebars.createirrigation.CreateIrrigation;

public class PonderRegistrator {
    public static void register() {
        PonderRegistry.startRegistration(CreateIrrigation.modid);

        //PonderRegistry.forComponents(BlockRegistrator.SPRINKLER_PIPE)
        //        .addStoryBoard("gearshift", PipeScenes:: ???);

        PonderRegistry.TAGS.forTag(PonderTag.FLUIDS)
                .add(BlockRegistrator.SPRINKLER_PIPE);

        PonderRegistry.endRegistration();
    }
}
