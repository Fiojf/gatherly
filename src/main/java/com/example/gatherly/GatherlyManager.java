package com.example.gatherly;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Singleton that manages to-do CRUD, inventory scanning, distance calculation, and config persistence.
 */
public class GatherlyManager {
    private static GatherlyManager INSTANCE;
    private final ConfigHolder<GatherlyConfig> configHolder;

    // ── Undo stack (in-memory only, cleared on restart) ─────────────
    private static final int MAX_UNDO = 50;
    private final Deque<Runnable> undoStack = new ArrayDeque<>();
    private final Deque<Runnable> redoStack = new ArrayDeque<>();

    // ── Notification state (in-memory) ──────────────────────────────
    private boolean firstScanDone = false;
    private final Set<TodoItem> notifiedCompletions = new HashSet<>();

    private GatherlyManager() {
        this.configHolder = AutoConfig.getConfigHolder(GatherlyConfig.class);
    }

    public void pushUndo(Runnable restoreAction) {
        redoStack.clear();
        undoStack.push(restoreAction);
        while (undoStack.size() > MAX_UNDO) undoStack.pollLast();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /** Pops and executes the most recent undo action. Returns true if something was undone. */
    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        // Snapshot current state for redo
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String todosSnapshot = gson.toJson(getConfig().todos);
        String bookmarksSnapshot = gson.toJson(getConfig().bookmarks);
        java.lang.reflect.Type todoListType = new com.google.gson.reflect.TypeToken<java.util.List<TodoItem>>(){}.getType();
        java.lang.reflect.Type bookmarkListType = new com.google.gson.reflect.TypeToken<java.util.List<Bookmark>>(){}.getType();

        undoStack.pop().run();
        save();

        redoStack.push(() -> {
            com.google.gson.Gson g = new com.google.gson.Gson();
            getConfig().todos.clear();
            getConfig().todos.addAll(g.fromJson(todosSnapshot, todoListType));
            getConfig().bookmarks.clear();
            getConfig().bookmarks.addAll(g.fromJson(bookmarksSnapshot, bookmarkListType));
        });
        while (redoStack.size() > MAX_UNDO) redoStack.pollLast();

        return true;
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Pops and executes the most recent redo action. Returns true if something was redone. */
    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        // Snapshot current state for undo
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String todosSnapshot = gson.toJson(getConfig().todos);
        String bookmarksSnapshot = gson.toJson(getConfig().bookmarks);
        java.lang.reflect.Type todoListType = new com.google.gson.reflect.TypeToken<java.util.List<TodoItem>>(){}.getType();
        java.lang.reflect.Type bookmarkListType = new com.google.gson.reflect.TypeToken<java.util.List<Bookmark>>(){}.getType();

        redoStack.pop().run();
        save();

        // Push to undo without clearing redo
        undoStack.push(() -> {
            com.google.gson.Gson g = new com.google.gson.Gson();
            getConfig().todos.clear();
            getConfig().todos.addAll(g.fromJson(todosSnapshot, todoListType));
            getConfig().bookmarks.clear();
            getConfig().bookmarks.addAll(g.fromJson(bookmarksSnapshot, bookmarkListType));
        });
        while (undoStack.size() > MAX_UNDO) undoStack.pollLast();

        return true;
    }

    public static GatherlyManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GatherlyManager();
        }
        return INSTANCE;
    }

    public GatherlyConfig getConfig() {
        return configHolder.getConfig();
    }

    // ── To-Do CRUD ───────────────────────────────────────────────────
    //
    // Indexing model:
    //   getRawTodos() returns the underlying list (all to-dos, all worlds, raw order).
    //   getTodos() returns the DISPLAY view: filtered by current world/server,
    //              then sorted pinned-first. This is what the screen and HUD see.
    //   All public index-based methods accept indices into the getTodos() view
    //   and resolve back to the raw list via item identity.

    /** Returns the underlying unfiltered list. Used for direct mutation only. */
    private List<TodoItem> getRawTodos() {
        return getConfig().todos;
    }

    /** Computes a stable identifier for the current world/server. Empty if unavailable. */
    public static String getCurrentWorldKey() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return "";
        if (client.isLocalServer() && client.getSingleplayerServer() != null) {
            try {
                return "sp:" + client.getSingleplayerServer().getWorldData().getLevelName();
            } catch (Throwable t) {
                return "sp:unknown";
            }
        }
        if (client.getCurrentServer() != null && client.getCurrentServer().ip != null) {
            return "mp:" + client.getCurrentServer().ip;
        }
        return "";
    }

    /**
     * Returns the display view: world-filtered, then sorted as
     * [active pinned, active unpinned, completed]. Completed to-dos always
     * sink to the bottom regardless of pin status.
     */
    public List<TodoItem> getTodos() {
        List<TodoItem> raw = getRawTodos();
        boolean worldFilter = getConfig().worldFilteringEnabled;
        String key = worldFilter ? getCurrentWorldKey() : null;

        List<TodoItem> activePinned = new ArrayList<>();
        List<TodoItem> activeUnpinned = new ArrayList<>();
        List<TodoItem> completed = new ArrayList<>();
        for (TodoItem t : raw) {
            if (worldFilter
                    && t.worldKey != null
                    && !t.worldKey.isEmpty()
                    && !t.worldKey.equals(key)) {
                continue; // belongs to a different world
            }
            if (t.isCompleted()) completed.add(t);
            else if (t.pinned) activePinned.add(t);
            else activeUnpinned.add(t);
        }
        List<TodoItem> result = new ArrayList<>(activePinned.size() + activeUnpinned.size() + completed.size());
        result.addAll(activePinned);
        result.addAll(activeUnpinned);
        result.addAll(completed);
        return result;
    }

    /**
     * Removes completed to-dos that have been completed for longer than the
     * configured threshold. Called from the tick handler. Returns true if any
     * were removed (so callers can rebuild the screen if it's open).
     */
    public boolean purgeOldCompletedTodos() {
        int minutes = getConfig().completedAutoDeleteMinutes;
        if (minutes <= 0) return false;
        long thresholdMs = minutes * 60_000L;
        long now = System.currentTimeMillis();
        List<TodoItem> raw = getRawTodos();
        boolean changed = false;
        java.util.Iterator<TodoItem> it = raw.iterator();
        while (it.hasNext()) {
            TodoItem t = it.next();
            if (t.isCompleted() && t.completedAtMillis > 0
                    && (now - t.completedAtMillis) >= thresholdMs) {
                it.remove();
                notifiedCompletions.remove(t);
                changed = true;
            }
        }
        if (changed) save();
        return changed;
    }

    /** Stamps completedAtMillis on a to-do when it's manually toggled to completed. */
    public void markCompletionTimestamp(TodoItem todo) {
        if (todo.isCompleted() && todo.completedAtMillis == 0) {
            todo.completedAtMillis = System.currentTimeMillis();
        } else if (!todo.isCompleted()) {
            todo.completedAtMillis = 0;
        }
    }

    public void addTodo(TodoItem item) {
        // Stamp the new to-do with the current world key so it stays scoped to this world
        if (item.worldKey == null || item.worldKey.isEmpty()) {
            item.worldKey = getCurrentWorldKey();
        }
        getRawTodos().add(item);
        save();
    }

    public void removeTodo(int displayIndex) {
        List<TodoItem> view = getTodos();
        if (displayIndex < 0 || displayIndex >= view.size()) return;
        final TodoItem removed = view.get(displayIndex);
        List<TodoItem> raw = getRawTodos();
        final int rawIdx = raw.indexOf(removed);
        if (rawIdx < 0) return;
        raw.remove(rawIdx);
        notifiedCompletions.remove(removed);
        pushUndo(() -> {
            List<TodoItem> list = getRawTodos();
            list.add(Math.min(rawIdx, list.size()), removed);
        });
        save();
    }

    public void addTarget(TodoItem item, TargetEntry target) {
        item.targets.add(target);
        save();
    }

    public void removeTarget(TodoItem item, int index) {
        if (index >= 0 && index < item.targets.size()) {
            final TargetEntry removed = item.targets.remove(index);
            final int idx = index;
            final TodoItem owner = item;
            pushUndo(() -> owner.targets.add(Math.min(idx, owner.targets.size()), removed));
            save();
        }
    }

    public void addCoordinate(TodoItem item, CoordinateEntry coord) {
        item.coordinates.add(coord);
        save();
    }

    public void removeCoordinate(TodoItem item, int index) {
        if (index >= 0 && index < item.coordinates.size()) {
            final CoordinateEntry removed = item.coordinates.remove(index);
            final int idx = index;
            final TodoItem owner = item;
            pushUndo(() -> owner.coordinates.add(Math.min(idx, owner.coordinates.size()), removed));
            save();
        }
    }

    public void addSubTask(TodoItem item, SubTask subTask) {
        item.subTasks.add(subTask);
        save();
    }

    public void removeSubTask(TodoItem item, int index) {
        if (index >= 0 && index < item.subTasks.size()) {
            final SubTask removed = item.subTasks.remove(index);
            final int idx = index;
            final TodoItem owner = item;
            pushUndo(() -> owner.subTasks.add(Math.min(idx, owner.subTasks.size()), removed));
            save();
        }
    }

    public void addSubTaskTarget(SubTask subTask, TargetEntry target) {
        subTask.targets.add(target);
        save();
    }

    public void removeSubTaskTarget(SubTask subTask, int index) {
        if (index >= 0 && index < subTask.targets.size()) {
            final TargetEntry removed = subTask.targets.remove(index);
            final int idx = index;
            final SubTask owner = subTask;
            pushUndo(() -> owner.targets.add(Math.min(idx, owner.targets.size()), removed));
            save();
        }
    }

    public void addSubTaskCoordinate(SubTask subTask, CoordinateEntry coord) {
        subTask.coordinates.add(coord);
        save();
    }

    public void removeSubTaskCoordinate(SubTask subTask, int index) {
        if (index >= 0 && index < subTask.coordinates.size()) {
            final CoordinateEntry removed = subTask.coordinates.remove(index);
            final int idx = index;
            final SubTask owner = subTask;
            pushUndo(() -> owner.coordinates.add(Math.min(idx, owner.coordinates.size()), removed));
            save();
        }
    }

    /**
     * Toggle pinned state for a to-do. The display view auto-sorts pinned-first
     * via getTodos(), so we don't need to mutate the underlying list.
     * Returns the new index in the display view.
     */
    public int togglePinned(int displayIndex) {
        List<TodoItem> view = getTodos();
        if (displayIndex < 0 || displayIndex >= view.size()) return displayIndex;
        TodoItem item = view.get(displayIndex);
        item.pinned = !item.pinned;
        save();
        return getTodos().indexOf(item);
    }

    /**
     * Reorder a to-do from one display index to another. Crossing the
     * pinned/unpinned boundary auto-changes the pin flag so the visual
     * "pinned-first" contract is preserved.
     */
    public void reorderTodo(int fromDisplay, int toDisplay) {
        List<TodoItem> view = getTodos();
        if (fromDisplay < 0 || fromDisplay >= view.size()) return;
        toDisplay = Math.max(0, Math.min(toDisplay, view.size() - 1));
        if (fromDisplay == toDisplay) return;

        TodoItem item = view.get(fromDisplay);

        // How many pinned items are in the (current) view, excluding the dragged one
        int pinnedCount = 0;
        for (int i = 0; i < view.size(); i++) {
            if (i == fromDisplay) continue;
            if (view.get(i).pinned) pinnedCount++;
        }
        // Land in pinned section if drop index is within [0, pinnedCount)
        boolean targetIsPinned = toDisplay < pinnedCount;
        item.pinned = targetIsPinned;

        // Build the desired post-move ordering of the display view
        List<TodoItem> newView = new ArrayList<>(view);
        newView.remove(fromDisplay);
        newView.add(toDisplay, item);

        // Rewrite raw list: keep non-view items in place, replace view-item slots
        // in raw with the new view order. This preserves to-dos from other worlds.
        Set<TodoItem> viewSet = new HashSet<>(newView);
        List<TodoItem> raw = getRawTodos();
        List<TodoItem> result = new ArrayList<>(raw.size());
        int cursor = 0;
        for (TodoItem t : raw) {
            if (viewSet.contains(t)) {
                result.add(newView.get(cursor++));
            } else {
                result.add(t);
            }
        }
        raw.clear();
        raw.addAll(result);
        save();
    }

    // ── Bookmark CRUD ─────────────────────────────────────────────────

    private List<Bookmark> getRawBookmarks() {
        return getConfig().bookmarks;
    }

    /** Returns bookmarks filtered by current world (same logic as getTodos). */
    public List<Bookmark> getBookmarks() {
        List<Bookmark> raw = getRawBookmarks();
        boolean worldFilter = getConfig().worldFilteringEnabled;
        String key = worldFilter ? getCurrentWorldKey() : null;

        List<Bookmark> result = new ArrayList<>();
        for (Bookmark b : raw) {
            if (worldFilter
                    && b.worldKey != null
                    && !b.worldKey.isEmpty()
                    && !b.worldKey.equals(key)) {
                continue;
            }
            result.add(b);
        }
        return result;
    }

    /** Returns bookmarks that should be visible in the HUD. */
    public List<Bookmark> getBookmarksForHud() {
        if (!getConfig().showBookmarksInHud) return List.of();
        List<Bookmark> result = new ArrayList<>();
        for (Bookmark b : getBookmarks()) {
            if (b.showInHud) result.add(b);
        }
        return result;
    }

    /** Returns bookmarks that should render a 3D marker in the world. */
    public List<Bookmark> getBookmarksForWorldRender() {
        List<Bookmark> result = new ArrayList<>();
        for (Bookmark b : getBookmarks()) {
            if (b.showWorldMarker) result.add(b);
        }
        return result;
    }

    public void addBookmark(Bookmark bookmark) {
        if (bookmark.worldKey == null || bookmark.worldKey.isEmpty()) {
            bookmark.worldKey = getCurrentWorldKey();
        }
        getRawBookmarks().add(bookmark);
        save();
    }

    public void removeBookmark(int displayIndex) {
        List<Bookmark> view = getBookmarks();
        if (displayIndex < 0 || displayIndex >= view.size()) return;
        final Bookmark removed = view.get(displayIndex);
        List<Bookmark> raw = getRawBookmarks();
        final int rawIdx = raw.indexOf(removed);
        if (rawIdx < 0) return;
        raw.remove(rawIdx);
        pushUndo(() -> {
            List<Bookmark> list = getRawBookmarks();
            list.add(Math.min(rawIdx, list.size()), removed);
        });
        save();
    }

    /** Creates a death waypoint bookmark at the given position. */
    public void createDeathWaypoint(double x, double y, double z) {
        if (!getConfig().deathWaypointEnabled) return;
        Bookmark death = new Bookmark("\u2620 Death", (int) x, (int) y, (int) z);
        death.bookmarkColor = 0xFFFF5555;
        death.worldKey = getCurrentWorldKey();
        death.showWorldMarker = true;
        death.showInHud = true;
        death.markerLetter = "\u2620";
        getRawBookmarks().add(death);
        save();
    }

    // ── Inventory Scan (client-side, runs every 20 ticks) ───────────
    // This scans the player's inventory for items matching each target's blockId
    // and updates currentCount with the total stack count found.
    // To disable: set autoCountEnabled = false in config.
    public void scanInventory(Inventory inventory) {
        // Only scan to-dos for the current world (avoids inventory bleeding across saves)
        List<TodoItem> todos = getTodos();
        for (TodoItem todo : todos) {
            scanTargets(todo.targets, inventory);
            for (SubTask subTask : todo.subTasks) {
                scanTargets(subTask.targets, inventory);
            }
        }
        // Fire completion toasts + stamp completedAtMillis for any newly-completed to-do
        boolean notify = getConfig().notifyOnCompletion;
        for (TodoItem todo : todos) {
            boolean nowCompleted = todo.isCompleted();
            // Maintain completedAtMillis: set when newly completed, clear when uncompleted.
            if (nowCompleted && todo.completedAtMillis == 0) {
                todo.completedAtMillis = System.currentTimeMillis();
            } else if (!nowCompleted && todo.completedAtMillis != 0) {
                todo.completedAtMillis = 0;
            }
            if (!firstScanDone) {
                // Prime: don't notify on first run, just record current state
                if (nowCompleted) notifiedCompletions.add(todo);
                continue;
            }
            if (notify
                    && todo.autoComplete
                    && nowCompleted
                    && !todo.manuallyCompleted
                    && !notifiedCompletions.contains(todo)) {
                showCompletionToast(todo);
                notifiedCompletions.add(todo);
            } else if (!nowCompleted) {
                notifiedCompletions.remove(todo);
            }
        }
        // Drop refs to to-dos no longer in the current view (deleted or world-switched)
        notifiedCompletions.retainAll(new HashSet<>(todos));
        firstScanDone = true;
        save();
    }

    private void showCompletionToast(TodoItem todo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.getToastManager().addToast(new SystemToast(
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal("Gatherly: To-Do Complete!"),
                Component.literal(todo.title)
        ));
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
        }
    }

    private void scanTargets(java.util.List<TargetEntry> targets, Inventory inventory) {
        for (TargetEntry target : targets) {
            int count = 0;
            Item targetItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(target.blockId));
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && stack.getItem() == targetItem) {
                    count += stack.getCount();
                }
            }
            target.currentCount = count;
        }
    }

    // ── Distance Calculation ─────────────────────────────────────────
    // Euclidean distance from player to coordinate, returned as integer blocks (rounded).
    // To change format: modify the rounding or return type here.
    public static int distanceTo(CoordinateEntry coord, Player player) {
        double dx = player.getX() - coord.x;
        double dy = player.getY() - coord.y;
        double dz = player.getZ() - coord.z;
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** Find the nearest coordinate across all to-dos, or null if none exist. */
    public CoordinateEntry findNearestCoordinate(Player player) {
        CoordinateEntry nearest = null;
        double minDist = Double.MAX_VALUE;
        for (TodoItem todo : getTodos()) {
            for (CoordinateEntry coord : todo.coordinates) {
                double dx = player.getX() - coord.x;
                double dy = player.getY() - coord.y;
                double dz = player.getZ() - coord.z;
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist < minDist) {
                    minDist = dist;
                    nearest = coord;
                }
            }
        }
        return nearest;
    }

    public void save() {
        configHolder.save();
    }
}
