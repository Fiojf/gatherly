package com.example.gatherly;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

@Config(name = "gatherly")
public class GatherlyConfig implements ConfigData {

    // ── Data (excluded from GUI — edited in GatherlyScreen) ──────────
    // Category set to "behavior" to prevent a phantom "default" category tab
    @ConfigEntry.Category("behavior")
    @ConfigEntry.Gui.Excluded
    public List<TodoItem> todos = new ArrayList<>();

    @ConfigEntry.Category("behavior")
    @ConfigEntry.Gui.Excluded
    public List<Bookmark> bookmarks = new ArrayList<>();

    // ── Behavior ─────────────────────────────────────────────────────
    @ConfigEntry.Category("behavior")
    public boolean autoCountEnabled = true;

    @ConfigEntry.Category("behavior")
    public boolean hideCompletedInHud = false;

    @ConfigEntry.Category("behavior")
    public boolean showNearestCoordinateInHud = true;

    /** When true, to-dos are filtered by the world/server they were created in. */
    @ConfigEntry.Category("behavior")
    public boolean worldFilteringEnabled = true;

    /** When true, a toast pops up when a to-do is auto-completed (all targets met). */
    @ConfigEntry.Category("behavior")
    public boolean notifyOnCompletion = true;

    /**
     * Minutes to keep a completed to-do before auto-deleting it. 0 = never auto-delete.
     * Completed to-dos always sort to the bottom of the list regardless of this value.
     * Rendered as a text field (no @BoundedDiscrete) so the user can type an exact value.
     * Clamped in validatePostLoad.
     */
    @ConfigEntry.Category("behavior")
    public int completedAutoDeleteMinutes = 5;

    /** Automatically create a bookmark at your death location. */
    @ConfigEntry.Category("behavior")
    public boolean deathWaypointEnabled = true;

    /** Show bookmarks in the HUD overlay (below to-dos). */
    @ConfigEntry.Category("behavior")
    public boolean showBookmarksInHud = true;

    // ── HUD Layout ───────────────────────────────────────────────────
    @ConfigEntry.Category("hud")
    public boolean hudEnabled = true;

    @ConfigEntry.Category("hud")
    public boolean hudBorderEnabled = false;

    @ConfigEntry.Category("hud")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 255)
    public int hudOpacity = 128;

    @ConfigEntry.Category("hud")
    public int hudX = 4;

    @ConfigEntry.Category("hud")
    public int hudY = 4;

    @ConfigEntry.Category("hud")
    public int hudWidth = 240;

    @ConfigEntry.Category("hud")
    public int hudHeight = 140;

    @ConfigEntry.Category("hud")
    @ConfigEntry.BoundedDiscrete(min = 25, max = 400)
    public int hudScale = 100;

    @ConfigEntry.Category("hud")
    @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
    public int maxHudRows = 6;

    @Override
    public void validatePostLoad() throws ValidationException {
        if (hudScale < 25) hudScale = 100;
        // Clamp completedAutoDeleteMinutes since it's a free-form text field
        if (completedAutoDeleteMinutes < 0) completedAutoDeleteMinutes = 0;
        if (completedAutoDeleteMinutes > 1440) completedAutoDeleteMinutes = 1440;
        // NOTE: Theme preset application is intentionally NOT done here.
        // Cloth Config writes color picker fields back AFTER validatePostLoad,
        // which would overwrite our preset colors. Instead, the tick handler
        // in GatherlyMod polls themePreset every tick and applies it then.
    }

    public void applyThemePreset(ThemePreset p) {
        switch (p) {
            case VANILLA -> {
                panelBackground = 0xC0101010;
                panelBorder = 0xFF000000;
                panelTitleColor = 0xFFFFFFFF;
                textPrimary = 0xFFFFFFFF;
                textSecondary = 0xFFA0A0A0;
                textCompleted = 0xFF55FF55;
                textWarning = 0xFFFF5555;
                textProgress = 0xFFFFFF55;
                textCoordinate = 0xFF55FFFF;
                progressBarTrack = 0xFF373737;
                progressBarFill = 0xFF55FF55;
                progressBarComplete = 0xFF55FF55;
                checkboxBorder = 0xFFA0A0A0;
                checkboxCheckedBorder = 0xFF55FF55;
                checkboxCheckedFill = 0x4055FF55;
                rowTintOdd = 0x10FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0x80101010;
                hudBorder = 0x00000000;
                buttonAddColor = 0xFF55FF55;
                buttonDeleteColor = 0xFFFF5555;
                buttonCoordColor = 0xFF55AAFF;
                buttonMarkColor = 0xFFFFAA00;
            }
            case DARK -> {
                panelBackground = 0xF0050505;
                panelBorder = 0xFF222222;
                panelTitleColor = 0xFFE0E0E0;
                textPrimary = 0xFFE0E0E0;
                textSecondary = 0xFF707070;
                textCompleted = 0xFF60C060;
                textWarning = 0xFFD05050;
                textProgress = 0xFFD0B040;
                textCoordinate = 0xFF40B0D0;
                progressBarTrack = 0xFF1A1A1A;
                progressBarFill = 0xFF60C060;
                progressBarComplete = 0xFF60C060;
                checkboxBorder = 0xFF606060;
                checkboxCheckedBorder = 0xFF60C060;
                checkboxCheckedFill = 0x4060C060;
                rowTintOdd = 0x18FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0xE0050505;
                hudBorder = 0xFF222222;
                buttonAddColor = 0xFF60C060;
                buttonDeleteColor = 0xFFD05050;
                buttonCoordColor = 0xFF40B0D0;
                buttonMarkColor = 0xFFD09040;
            }
            case TRANSPARENT -> {
                panelBackground = 0x40000000;
                panelBorder = 0x80FFFFFF;
                panelTitleColor = 0xFFFFFFFF;
                textPrimary = 0xFFFFFFFF;
                textSecondary = 0xFFCCCCCC;
                textCompleted = 0xFF80FF80;
                textWarning = 0xFFFF8080;
                textProgress = 0xFFFFFF80;
                textCoordinate = 0xFF80FFFF;
                progressBarTrack = 0x60FFFFFF;
                progressBarFill = 0xFF80FF80;
                progressBarComplete = 0xFF80FF80;
                checkboxBorder = 0xFFFFFFFF;
                checkboxCheckedBorder = 0xFF80FF80;
                checkboxCheckedFill = 0x4080FF80;
                rowTintOdd = 0x10FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0x20000000;
                hudBorder = 0x60FFFFFF;
                buttonAddColor = 0xFF80FF80;
                buttonDeleteColor = 0xFFFF8080;
                buttonCoordColor = 0xFF80C0FF;
                buttonMarkColor = 0xFFFFC080;
            }
            case RETRO -> {
                panelBackground = 0xFF000000;
                panelBorder = 0xFF00FF00;
                panelTitleColor = 0xFF00FF00;
                textPrimary = 0xFF00FF00;
                textSecondary = 0xFF008800;
                textCompleted = 0xFF00FFAA;
                textWarning = 0xFFFFAA00;
                textProgress = 0xFF88FF00;
                textCoordinate = 0xFF00FFAA;
                progressBarTrack = 0xFF003300;
                progressBarFill = 0xFF00FF00;
                progressBarComplete = 0xFF00FFAA;
                checkboxBorder = 0xFF00AA00;
                checkboxCheckedBorder = 0xFF00FF00;
                checkboxCheckedFill = 0x4000FF00;
                rowTintOdd = 0x1000FF00;
                rowTintEven = 0x00000000;
                hudBackground = 0xE0000000;
                hudBorder = 0xFF00FF00;
                buttonAddColor = 0xFF00FF00;
                buttonDeleteColor = 0xFFFFAA00;
                buttonCoordColor = 0xFF00FFAA;
                buttonMarkColor = 0xFF88FF00;
            }
            case OCEAN -> {
                panelBackground = 0xE8041830;
                panelBorder = 0xFF1E78B4;
                panelTitleColor = 0xFF80D8FF;
                textPrimary = 0xFFD0ECFF;
                textSecondary = 0xFF6FA8D0;
                textCompleted = 0xFF40E0D0;
                textWarning = 0xFFFF8080;
                textProgress = 0xFFFFD060;
                textCoordinate = 0xFF80D8FF;
                progressBarTrack = 0xFF082A4A;
                progressBarFill = 0xFF1E90FF;
                progressBarComplete = 0xFF40E0D0;
                checkboxBorder = 0xFF4080C0;
                checkboxCheckedBorder = 0xFF40E0D0;
                checkboxCheckedFill = 0x4040E0D0;
                rowTintOdd = 0x18FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0xD0041830;
                hudBorder = 0xFF1E78B4;
                buttonAddColor = 0xFF40E0D0;
                buttonDeleteColor = 0xFFFF8080;
                buttonCoordColor = 0xFF80D8FF;
                buttonMarkColor = 0xFFFFD060;
            }
            case SUNSET -> {
                panelBackground = 0xE82A0A20;
                panelBorder = 0xFFFF6040;
                panelTitleColor = 0xFFFFD080;
                textPrimary = 0xFFFFE8D0;
                textSecondary = 0xFFC08070;
                textCompleted = 0xFFFFC060;
                textWarning = 0xFFFF4040;
                textProgress = 0xFFFFB040;
                textCoordinate = 0xFFFF80A0;
                progressBarTrack = 0xFF401020;
                progressBarFill = 0xFFFF8040;
                progressBarComplete = 0xFFFFC060;
                checkboxBorder = 0xFFB06060;
                checkboxCheckedBorder = 0xFFFFC060;
                checkboxCheckedFill = 0x40FFC060;
                rowTintOdd = 0x18FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0xD02A0A20;
                hudBorder = 0xFFFF6040;
                buttonAddColor = 0xFFFFC060;
                buttonDeleteColor = 0xFFFF4040;
                buttonCoordColor = 0xFFFF80A0;
                buttonMarkColor = 0xFFFFB040;
            }
            case FOREST -> {
                panelBackground = 0xE80A1A08;
                panelBorder = 0xFF3A5A20;
                panelTitleColor = 0xFFC0E080;
                textPrimary = 0xFFE0F0C0;
                textSecondary = 0xFF80A060;
                textCompleted = 0xFFA0E040;
                textWarning = 0xFFE08040;
                textProgress = 0xFFD0C040;
                textCoordinate = 0xFF80D0A0;
                progressBarTrack = 0xFF1A2A10;
                progressBarFill = 0xFF60A030;
                progressBarComplete = 0xFFA0E040;
                checkboxBorder = 0xFF60804A;
                checkboxCheckedBorder = 0xFFA0E040;
                checkboxCheckedFill = 0x40A0E040;
                rowTintOdd = 0x18FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0xD00A1A08;
                hudBorder = 0xFF3A5A20;
                buttonAddColor = 0xFFA0E040;
                buttonDeleteColor = 0xFFE08040;
                buttonCoordColor = 0xFF80D0A0;
                buttonMarkColor = 0xFFD0C040;
            }
            case ROSE -> {
                panelBackground = 0xE82A0A18;
                panelBorder = 0xFFE060A0;
                panelTitleColor = 0xFFFFC0E0;
                textPrimary = 0xFFFFE0F0;
                textSecondary = 0xFFB08090;
                textCompleted = 0xFFFF80C0;
                textWarning = 0xFFFF6060;
                textProgress = 0xFFFFA0D0;
                textCoordinate = 0xFFD080FF;
                progressBarTrack = 0xFF401028;
                progressBarFill = 0xFFE060A0;
                progressBarComplete = 0xFFFF80C0;
                checkboxBorder = 0xFFA06080;
                checkboxCheckedBorder = 0xFFFF80C0;
                checkboxCheckedFill = 0x40FF80C0;
                rowTintOdd = 0x18FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0xD02A0A18;
                hudBorder = 0xFFE060A0;
                buttonAddColor = 0xFFFF80C0;
                buttonDeleteColor = 0xFFFF6060;
                buttonCoordColor = 0xFFD080FF;
                buttonMarkColor = 0xFFFFA0D0;
            }
            case MIDNIGHT -> {
                panelBackground = 0xF0060818;
                panelBorder = 0xFF3040A0;
                panelTitleColor = 0xFFA0B0FF;
                textPrimary = 0xFFD8E0FF;
                textSecondary = 0xFF6070A0;
                textCompleted = 0xFF80A0FF;
                textWarning = 0xFFFF6070;
                textProgress = 0xFFC0A0FF;
                textCoordinate = 0xFF60C0FF;
                progressBarTrack = 0xFF0A1030;
                progressBarFill = 0xFF4060C0;
                progressBarComplete = 0xFF80A0FF;
                checkboxBorder = 0xFF4050A0;
                checkboxCheckedBorder = 0xFF80A0FF;
                checkboxCheckedFill = 0x4080A0FF;
                rowTintOdd = 0x18FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0xE0060818;
                hudBorder = 0xFF3040A0;
                buttonAddColor = 0xFF80A0FF;
                buttonDeleteColor = 0xFFFF6070;
                buttonCoordColor = 0xFF60C0FF;
                buttonMarkColor = 0xFFC0A0FF;
            }
            case NEON -> {
                panelBackground = 0xF0050005;
                panelBorder = 0xFFFF00FF;
                panelTitleColor = 0xFF00FFFF;
                textPrimary = 0xFFFFFFFF;
                textSecondary = 0xFFA040A0;
                textCompleted = 0xFF00FF80;
                textWarning = 0xFFFF2060;
                textProgress = 0xFFFFFF00;
                textCoordinate = 0xFF00FFFF;
                progressBarTrack = 0xFF200020;
                progressBarFill = 0xFFFF00FF;
                progressBarComplete = 0xFF00FF80;
                checkboxBorder = 0xFFFF00FF;
                checkboxCheckedBorder = 0xFF00FF80;
                checkboxCheckedFill = 0x4000FF80;
                rowTintOdd = 0x20FF00FF;
                rowTintEven = 0x00000000;
                hudBackground = 0xE0050005;
                hudBorder = 0xFFFF00FF;
                buttonAddColor = 0xFF00FF80;
                buttonDeleteColor = 0xFFFF2060;
                buttonCoordColor = 0xFF00FFFF;
                buttonMarkColor = 0xFFFFFF00;
            }
            case MONOCHROME -> {
                panelBackground = 0xF0101010;
                panelBorder = 0xFF808080;
                panelTitleColor = 0xFFFFFFFF;
                textPrimary = 0xFFFFFFFF;
                textSecondary = 0xFF909090;
                textCompleted = 0xFFCCCCCC;
                textWarning = 0xFFE0E0E0;
                textProgress = 0xFFB0B0B0;
                textCoordinate = 0xFFD0D0D0;
                progressBarTrack = 0xFF303030;
                progressBarFill = 0xFFB0B0B0;
                progressBarComplete = 0xFFFFFFFF;
                checkboxBorder = 0xFF909090;
                checkboxCheckedBorder = 0xFFFFFFFF;
                checkboxCheckedFill = 0x40FFFFFF;
                rowTintOdd = 0x14FFFFFF;
                rowTintEven = 0x00000000;
                hudBackground = 0xD0101010;
                hudBorder = 0xFF808080;
                buttonAddColor = 0xFFE0E0E0;
                buttonDeleteColor = 0xFFB0B0B0;
                buttonCoordColor = 0xFFCCCCCC;
                buttonMarkColor = 0xFFFFFFFF;
            }
            default -> {}
        }
    }

    // ── Panel Layout ─────────────────────────────────────────────────
    @ConfigEntry.Category("panel")
    @ConfigEntry.BoundedDiscrete(min = 30, max = 100)
    public int panelWidthPercent = 85;

    @ConfigEntry.Category("panel")
    @ConfigEntry.BoundedDiscrete(min = 30, max = 100)
    public int panelHeightPercent = 90;

    // ── Theme Preset ─────────────────────────────────────────────────
    // Picking a non-CUSTOM preset and saving applies the preset's colors to
    // all theme fields below, then resets back to CUSTOM (handled by the
    // tick handler in GatherlyMod, NOT validatePostLoad — Cloth Config
    // re-writes color fields after validatePostLoad in some cases).
    public enum ThemePreset {
        CUSTOM, VANILLA, DARK, TRANSPARENT, RETRO,
        OCEAN, SUNSET, FOREST, ROSE, MIDNIGHT, NEON, MONOCHROME
    }

    @ConfigEntry.Category("theme")
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public ThemePreset themePreset = ThemePreset.CUSTOM;

    // ── Theme: Panel — vanilla Minecraft defaults ────────────────────
    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int panelBackground = 0xC0101010;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int panelBorder = 0xFF000000;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int panelTitleColor = 0xFFFFFFFF;

    // ── Theme: Text ──────────────────────────────────────────────────
    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int textPrimary = 0xFFFFFFFF;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int textSecondary = 0xFFA0A0A0;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int textCompleted = 0xFF55FF55;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int textWarning = 0xFFFF5555;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int textProgress = 0xFFFFFF55;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int textCoordinate = 0xFF55FFFF;

    // ── Theme: Progress Bar ──────────────────────────────────────────
    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int progressBarTrack = 0xFF373737;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int progressBarFill = 0xFF55FF55;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int progressBarComplete = 0xFF55FF55;

    // ── Theme: Checkbox ──────────────────────────────────────────────
    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int checkboxBorder = 0xFFA0A0A0;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int checkboxCheckedBorder = 0xFF55FF55;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int checkboxCheckedFill = 0x4055FF55;

    // ── Theme: Row Alternation ───────────────────────────────────────
    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int rowTintOdd = 0x10FFFFFF;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int rowTintEven = 0x00000000;

    // ── Theme: Completed Opacity ─────────────────────────────────────
    @ConfigEntry.Category("theme")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 255)
    public int completedItemAlpha = 0x90;

    // ── Theme: HUD ───────────────────────────────────────────────────
    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int hudBackground = 0x80101010;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int hudBorder = 0x00000000;

    // ── Theme: Buttons ───────────────────────────────────────────────
    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int buttonAddColor = 0xFF55FF55;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int buttonDeleteColor = 0xFFFF5555;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int buttonCoordColor = 0xFF55AAFF;

    @ConfigEntry.Category("theme")
    @ConfigEntry.ColorPicker(allowAlpha = true)
    public int buttonMarkColor = 0xFFFFAA00;
}
