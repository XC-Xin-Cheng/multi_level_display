package com.example.layers;

import com.example.layers.commands.LayerCommand;
import com.example.layers.layers.Layers;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import org.slf4j.Logger;

public class LayersAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Layers addon");

        Layers.init();
        Commands.add(new LayerCommand());
        LayersAutoTest.init();
    }

    @Override
    public String getPackage() {
        return "com.example.layers";
    }
}
