package com.example.gatherly;

/**
 * A saved location bookmark with optional HUD display and world marker.
 * Persisted via GatherlyConfig.bookmarks (Gson/AutoConfig).
 */
public class Bookmark {
    public String label = "New Bookmark";
    public int x = 0;
    public int y = 64;
    public int z = 0;

    /** Custom color (0 = auto/default). Same ARGB presets as TodoItem.todoColor. */
    public int bookmarkColor = 0;

    /** World/server scope (same format as TodoItem.worldKey). Empty = global. */
    public String worldKey = "";

    /** Whether this bookmark is shown in the HUD overlay. */
    public boolean showInHud = true;

    /** Whether distance and coordinates are shown for this bookmark in the HUD. */
    public boolean showDistanceInHud = true;

    /** Whether a 3D marker is rendered in the world at this bookmark's position. */
    public boolean showWorldMarker = true;

    /** Letter displayed on the world marker. Empty = first letter of label. */
    public String markerLetter = "";

    public Bookmark() {}

    public Bookmark(String label, int x, int y, int z) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Returns the single letter to display on the world marker. */
    public String getDisplayLetter() {
        if (markerLetter != null && !markerLetter.isEmpty()) {
            return markerLetter.substring(0, 1).toUpperCase();
        }
        if (label != null && !label.isEmpty()) {
            return label.substring(0, 1).toUpperCase();
        }
        return "?";
    }
}
