package com.example.gatherly;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client mod initializer for Gatherly.
 *
 * Keybind registration: see KEYBIND REGISTRATION below.
 * Keybind handling: see KEYBIND HANDLING in the tick callback.
 *
 * To add more keybinds:
 * 1. Declare a new KeyMapping field with a KeyMapping.Category (use GATHERLY_CATEGORY)
 * 2. Register it via KeyBindingHelper.registerKeyBinding() in onInitializeClient()
 * 3. Handle it in the END_CLIENT_TICK callback
 * All keybinds will appear under the "Gatherly" category in Controls settings.
 */
public class GatherlyMod implements ClientModInitializer {

    // ── KEYBIND REGISTRATION ─────────────────────────────────────────
    // Custom keybind category for the Controls screen.
    // Translation key: key.category.gatherly.gatherly -> "Gatherly"
    private static final KeyMapping.Category GATHERLY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("gatherly", "gatherly"));

    // Single keybind for K. Shift+K toggles HUD, plain K opens screen.
    // These appear under "Gatherly" in Options → Controls → Key Binds.
    private static KeyMapping openKey;

    private int tickCounter = 0;
    private boolean wasAlive = true;

    @Override
    public void onInitializeClient() {
        // Register config with AutoConfig + Gson serialization
        AutoConfig.register(GatherlyConfig.class, GsonConfigSerializer::new);

        // Register keybind: K — open screen (or Shift+K to toggle HUD)
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.gatherly.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                GATHERLY_CATEGORY
        ));

        // ── KEYBIND HANDLING + AUTO-COUNT (tick callback) ────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;

            // Handle keybind presses
            while (openKey.consumeClick()) {
                // Check if Shift is held to distinguish K vs Shift+K
                // Modifier logic: check via Window object since we're not in a Screen
                boolean shiftHeld = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);

                if (shiftHeld) {
                    // Shift+K: toggle HUD visibility
                    GatherlyConfig config = GatherlyManager.getInstance().getConfig();
                    config.hudEnabled = !config.hudEnabled;
                    GatherlyManager.getInstance().save();
                } else {
                    // K: open the Gatherly screen
                    client.setScreen(new GatherlyScreen());
                }
            }

            // Auto-count inventory scan — runs every 20 ticks (~1 second)
            if (tickCounter % 20 == 0 && client.player != null) {
                GatherlyConfig config = GatherlyManager.getInstance().getConfig();
                if (config.autoCountEnabled) {
                    GatherlyManager.getInstance().scanInventory(client.player.getInventory());
                }
            }

            // Theme preset application — polled every tick because Cloth Config
            // overwrites color fields after validatePostLoad in some cases.
            GatherlyConfig cfg = GatherlyManager.getInstance().getConfig();
            if (cfg.themePreset != null && cfg.themePreset != GatherlyConfig.ThemePreset.CUSTOM) {
                GatherlyConfig.ThemePreset toApply = cfg.themePreset;
                cfg.applyThemePreset(toApply);
                cfg.themePreset = GatherlyConfig.ThemePreset.CUSTOM;
                GatherlyManager.getInstance().save();
            }

            // Auto-purge old completed to-dos — runs every 100 ticks (~5 seconds)
            if (tickCounter % 100 == 0) {
                boolean changed = GatherlyManager.getInstance().purgeOldCompletedTodos();
                if (changed && client.screen instanceof GatherlyScreen gs) {
                    gs.refreshAfterExternalChange();
                }
            }

            // Death waypoint detection
            if (client.player != null) {
                boolean alive = client.player.isAlive();
                if (wasAlive && !alive) {
                    GatherlyManager.getInstance().createDeathWaypoint(
                            client.player.getX(), client.player.getY(), client.player.getZ());
                }
                wasAlive = alive;
            } else {
                wasAlive = true;
            }
        });

        // Register HUD overlay renderer via HudElementRegistry (replaces deprecated HudRenderCallback)
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("gatherly", "hud"),
                GatherlyHudRenderer::render
        );

        // Register 3D level renderer for bookmark waypoint markers
        BookmarkWorldRenderer.register();
    }
}
