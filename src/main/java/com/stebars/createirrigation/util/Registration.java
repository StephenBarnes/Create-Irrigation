package com.stebars.createirrigation.util;

import com.simibubi.create.foundation.data.CreateRegistrate;

public abstract class Registration {
    public final CreateRegistrate REGISTRATE;

    public Registration(CreateRegistrate r) {
        this.REGISTRATE = r;
    }

    public abstract void register();
}
