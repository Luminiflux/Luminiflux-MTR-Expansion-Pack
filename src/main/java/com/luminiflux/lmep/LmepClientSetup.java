package com.luminiflux.lmep;

import com.luminiflux.lmep.preview.TrackPreviewHud;
import com.luminiflux.lmep.preview.TrackPreviewManager;
import com.luminiflux.lmep.preview.TrackPreviewRenderer;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client-only wiring for LMEP.
 *
 * <p>Kept separate from {@link LmepMod} on purpose: this class references client-only
 * types, so it must only ever be resolved on the physical client. Nothing else in the
 * mod refers to it.</p>
 */
public final class LmepClientSetup {

    private LmepClientSetup() {
    }

    public static void initialize() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LmepConfig.SPEC, "lmep-client.toml");

        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(LmepClientSetup::onRegisterKeyMappings);

        final IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;
        forgeEventBus.register(ClientTickHandler.class);
        forgeEventBus.register(TrackPreviewRenderer.class);
        forgeEventBus.register(TrackPreviewHud.class);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(LmepKeybinds.TOGGLE_PREVIEW);
        event.register(LmepKeybinds.TOGGLE_HUD);
    }

    /** Drains the key bindings, then refreshes the per-frame preview state. */
    public static final class ClientTickHandler {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            while (LmepKeybinds.TOGGLE_PREVIEW.consumeClick()) {
                LmepConfig.setEnabled(!LmepConfig.enabled());
            }
            while (LmepKeybinds.TOGGLE_HUD.consumeClick()) {
                LmepConfig.setShowHud(!LmepConfig.showHud());
            }
            TrackPreviewManager.clientTick();
        }

        private ClientTickHandler() {
        }
    }
}
