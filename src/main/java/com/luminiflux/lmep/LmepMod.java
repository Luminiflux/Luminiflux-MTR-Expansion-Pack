package com.luminiflux.lmep;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Luminiflux's MTR Expansion Pack (LMEP / 流光MTR拓展包).
 *
 * <p>A pure client-side addon that brings the MTR4 visual rail placement preview to
 * Minecraft Transit Railway 3.x on Minecraft 1.20.1 Forge.</p>
 *
 * <p>Forge 1.20.1's {@code @Mod} annotation only carries {@code value()} &mdash; it has no
 * {@code dist} element to restrict a mod to one side. The side check therefore lives in
 * the constructor, and every client-only class is reached exclusively through
 * {@link LmepClientSetup}. That keeps a dedicated server from ever resolving
 * {@code KeyMapping}, {@code RenderLevelStageEvent} or the MTR client cache, so LMEP
 * simply does nothing there instead of crashing.</p>
 */
@Mod(LmepMod.MOD_ID)
public final class LmepMod {

    public static final String MOD_ID = "lmep";

    public LmepMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LmepClientSetup.initialize();
        }
    }
}
