package com.example.gatherly;

import java.util.ArrayList;
import java.util.List;

/**
 * A single to-do item containing a title, collection targets, and coordinate bookmarks.
 * Supports auto-complete mode (completes when all targets met) or manual mode.
 * Optional timer tracks how long a task should take.
 */
public class TodoItem {
    public String title = "New To-Do";
    public List<TargetEntry> targets = new ArrayList<>();
    public List<CoordinateEntry> coordinates = new ArrayList<>();
    public List<SubTask> subTasks = new ArrayList<>();

    /** Custom color for this to-do (0 = auto/default behavior). */
    public int todoColor = 0;

    /** Pinned to-dos are shown first in both the HUD and the full-screen list. */
    public boolean pinned = false;

    /**
     * World/server identifier this to-do belongs to.
     * Empty string = global (visible in any world). Set automatically on creation.
     * Format: "sp:WorldName" or "mp:server.address".
     */
    /** Free-form notes/description for this to-do. */
    public String notes = "";

    public String worldKey = "";

    /** Tracks completion state across scans for the toast notification system. Not persisted. */
    public transient boolean wasCompletedAtLastScan = false;

    /**
     * Wall-clock millis when this to-do became completed (0 = not completed).
     * Used by the auto-delete system: completed to-dos older than the configured
     * threshold are purged automatically. Set on the completion transition by
     * scanInventory and by manual checkbox toggles.
     */
    public long completedAtMillis = 0;

    /** When true, completion is auto-computed from targets. When false, manually toggled. */
    public boolean autoComplete = true;

    /** Used when autoComplete=false: manually toggled via the checkbox. */
    public boolean manuallyCompleted = false;

    /** Timer duration in seconds (0 = no timer). */
    public long timerSeconds = 0;

    /** Epoch seconds when the timer was started (0 = not started). */
    public long timerStartEpoch = 0;

    public TodoItem() {}

    public TodoItem(String title) {
        this.title = title;
    }

    /** Returns true if this to-do is considered completed. */
    public boolean isCompleted() {
        if (manuallyCompleted) return true;
        if (!autoComplete) return false;
        if (targets.isEmpty()) return false;
        for (TargetEntry t : targets) {
            if (!t.isMet()) return false;
        }
        return true;
    }

    /** Returns remaining timer seconds, or -1 if no timer / not started. */
    public long getTimerRemaining() {
        if (timerSeconds <= 0 || timerStartEpoch <= 0) return -1;
        long elapsed = System.currentTimeMillis() / 1000 - timerStartEpoch;
        long remaining = timerSeconds - elapsed;
        return Math.max(0, remaining);
    }

    /** Returns true if the timer is active and has time remaining. */
    public boolean isTimerRunning() {
        return timerSeconds > 0 && timerStartEpoch > 0 && getTimerRemaining() > 0;
    }

    /** Returns true if the timer was started and has expired. */
    public boolean isTimerExpired() {
        return timerSeconds > 0 && timerStartEpoch > 0 && getTimerRemaining() == 0;
    }

    /** Format seconds as H:MM:SS or M:SS. */
    public static String formatTime(long totalSeconds) {
        if (totalSeconds <= 0) return "0:00";
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }
}
