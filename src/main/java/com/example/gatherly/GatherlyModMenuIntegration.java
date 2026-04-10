package com.example.gatherly;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfigClient;

/**
 * Mod Menu integration. Provides a config screen via AutoConfig/Cloth Config.
 * All color fields render as color pickers with alpha sliders.
 */
public class GatherlyModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfigClient.getConfigScreen(GatherlyConfig.class, parent).get();
    }
}
