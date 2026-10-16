package com.luminiflux.lmep;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Key bindings contributed by LMEP. */
public final class LmepKeybinds {

    public static final String CATEGORY = "key.category.lmep";

    /** Toggles the whole preview on/off. Default: K */
    public static final KeyMapping TOGGLE_PREVIEW = new KeyMapping(
            "key.lmep.toggle_preview",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    /** Toggles the screen-space info panel. Default: J */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.lmep.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );

    private LmepKeybinds() {
    }
}
