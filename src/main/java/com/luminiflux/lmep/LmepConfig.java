package com.luminiflux.lmep;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * LMEP client configuration ({@code config/lmep-client.toml}).
 *
 * <p>Every read is funnelled through a small accessor that falls back to a sane
 * default when the config file has not been loaded yet. Forge throws
 * {@code IllegalStateException("Cannot get config value before config is loaded")}
 * otherwise, and our accessors are called from render/tick callbacks that may run
 * before or independently of the config load.</p>
 */
public final class LmepConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue SHOW_WORLD_PREVIEW;
    private static final ForgeConfigSpec.BooleanValue SHOW_WORLD_TEXT;
    private static final ForgeConfigSpec.BooleanValue SHOW_HUD;
    private static final ForgeConfigSpec.BooleanValue SHOW_CUMULATIVE_LENGTH;
    private static final ForgeConfigSpec.BooleanValue FLASH_ENABLED;
    private static final ForgeConfigSpec.DoubleValue GHOST_ALPHA;
    private static final ForgeConfigSpec.DoubleValue GHOST_WIDTH_SCALE;
    private static final ForgeConfigSpec.IntValue MAX_WALK_VISITS;
    private static final ForgeConfigSpec.IntValue MAX_WALK_DEPTH;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Luminiflux's MTR Expansion Pack - visual rail placement preview.")
                .push("preview");

        ENABLED = builder
                .comment("Master switch of the track preview.",
                        "轨道预览总开关。")
                .define("enabled", true);
        SHOW_WORLD_PREVIEW = builder
                .comment("Draw the ghost track in the world.",
                        "在世界中绘制幽灵轨道。")
                .define("showWorldPreview", true);
        SHOW_WORLD_TEXT = builder
                .comment("Draw floating numbers next to the ghost track.",
                        "在幽灵轨道旁绘制漂浮数值。")
                .define("showWorldText", true);
        SHOW_HUD = builder
                .comment("Draw the screen-space info panel.",
                        "显示屏幕信息面板。")
                .define("showHud", true);
        SHOW_CUMULATIVE_LENGTH = builder
                .comment("Walk the existing rail network from the anchor and add up the total line length.",
                        "从锚点沿既有线路累计线路总长（便于估算编组长度）。")
                .define("showCumulativeLength", true);
        GHOST_ALPHA = builder
                .comment("Opacity of the ghost track, 0.0 - 1.0.",
                        "幽灵轨道不透明度。")
                .defineInRange("ghostAlpha", 0.6D, 0.0D, 1.0D);
        GHOST_WIDTH_SCALE = builder
                .comment("Multiplier applied to MTR's own rail width, so the preview matches the real track.",
                        "相对 MTR 原生轨道宽度的倍率，使预览与实际轨道等宽。")
                .defineInRange("ghostWidthScale", 1.0D, 0.1D, 4.0D);
        FLASH_ENABLED = builder
                .comment("Pulse the ghost track colour like MTR4 does.",
                        "像 MTR4 一样让幽灵轨道颜色脉动。")
                .define("flashEnabled", true);
        MAX_WALK_VISITS = builder
                .comment("Safety budget for the cumulative-length walk: graph nodes visited per recompute.",
                        "累计线路长度搜索的访问节点上限，防止线网过大时卡顿。")
                .defineInRange("maxWalkVisits", 512, 16, 8192);
        MAX_WALK_DEPTH = builder
                .comment("Maximum rail segments walked outwards from the anchor.",
                        "从锚点起最多向外回溯的轨道段数。")
                .defineInRange("maxWalkDepth", 64, 1, 512);

        builder.pop();
        SPEC = builder.build();
    }

    private LmepConfig() {
    }

    // ---------------------------------------------------------------- readers

    public static boolean enabled() {
        return read(ENABLED, true);
    }

    public static boolean showWorldPreview() {
        return read(SHOW_WORLD_PREVIEW, true);
    }

    public static boolean showWorldText() {
        return read(SHOW_WORLD_TEXT, true);
    }

    public static boolean showHud() {
        return read(SHOW_HUD, true);
    }

    public static boolean showCumulativeLength() {
        return read(SHOW_CUMULATIVE_LENGTH, true);
    }

    public static boolean flashEnabled() {
        return read(FLASH_ENABLED, true);
    }

    public static double ghostAlpha() {
        return read(GHOST_ALPHA, 0.6D);
    }

    public static double ghostWidthScale() {
        return read(GHOST_WIDTH_SCALE, 1.0D);
    }

    public static int maxWalkVisits() {
        return read(MAX_WALK_VISITS, 512);
    }

    public static int maxWalkDepth() {
        return read(MAX_WALK_DEPTH, 64);
    }

    // ---------------------------------------------------------------- writers

    public static void setEnabled(boolean value) {
        write(ENABLED, value);
    }

    public static void setShowHud(boolean value) {
        write(SHOW_HUD, value);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean read(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (final RuntimeException notLoadedYet) {
            return fallback;
        }
    }

    private static double read(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (final RuntimeException notLoadedYet) {
            return fallback;
        }
    }

    private static int read(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (final RuntimeException notLoadedYet) {
            return fallback;
        }
    }

    private static void write(ForgeConfigSpec.BooleanValue value, boolean newValue) {
        try {
            value.set(newValue);
            SPEC.save();
        } catch (final RuntimeException notLoadedYet) {
            // Nothing sensible to do; the toggle simply will not persist.
        }
    }
}
