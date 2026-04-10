package com.example.gatherly;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * The main Gatherly screen. Renders a centered panel over the dimmed game world.
 * ALL colors/opacities are read from config — never hardcoded in this renderer.
 */
public class GatherlyScreen extends Screen {

    private enum Tab { TODOS, BOOKMARKS }
    private Tab activeTab = Tab.TODOS;

    private final GatherlyManager manager = GatherlyManager.getInstance();
    private GatherlyConfig config;
    private TodoListWidget todoList;

    private int panelX, panelY, panelW, panelH;
    private final int headerHeight = 44; // Increased for tabs + search

    private final Set<Integer> expandedIndices = new HashSet<>();
    // Track which to-dos have their settings panel open
    private final Set<Integer> settingsOpenIndices = new HashSet<>();
    // Track which to-dos have their notes panel open
    private final Set<Integer> notesOpenIndices = new HashSet<>();
    // Track which sub-tasks are expanded (key = "todoIdx_subIdx")
    private final Set<String> expandedSubTaskIndices = new HashSet<>();

    static final int[] PRESET_COLORS = {
            0,           // Auto
            0xFF55FF55,  // Green
            0xFF5555FF,  // Blue
            0xFFFF5555,  // Red
            0xFFFFFF55,  // Yellow
            0xFFFF55FF,  // Magenta
            0xFF55FFFF,  // Cyan
            0xFFFFAA00,  // Orange
            0xFFAAAAAA,  // Gray
    };
    static final String[] COLOR_NAMES = {
            "Auto", "Green", "Blue", "Red", "Yellow",
            "Magenta", "Cyan", "Orange", "Gray"
    };

    // Autocomplete suggestion state (for block ID picker)
    private List<String> suggestions = new ArrayList<>();
    private int suggestionX, suggestionY, suggestionWidth;
    private EditBox activeSuggestionField = null;
    private TargetSubEntry activeSuggestionEntry = null;

    // Bookmark settings expansion tracking
    private final Set<Integer> bookmarkSettingsOpenIndices = new HashSet<>();
    // Search field
    private EditBox searchField;
    private String searchQuery = "";

    // Drag-and-drop state for reordering top-level to-dos.
    // Set when the user clicks a TodoHeaderEntry's drag handle, cleared on release.
    private int draggingFromIndex = -1;
    private double dragMouseY = 0;

    public GatherlyScreen() {
        super(Component.literal("Gatherly"));
    }

    @Override
    protected void init() {
        config = manager.getConfig();
        panelW = (int) (width * config.panelWidthPercent / 100f);
        panelH = (int) (height * config.panelHeightPercent / 100f);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        // ── Row 1: Tab buttons (left) + Add + Close (right) ──────────
        int tabY = panelY + 4;

        // Tab: To-Dos
        addRenderableWidget(Button.builder(
                Component.literal(activeTab == Tab.TODOS ? "\u00A7lTo-Dos" : "To-Dos"), btn -> {
            playClick();
            activeTab = Tab.TODOS;
            searchQuery = "";
            if (searchField != null) searchField.setValue("");
            clearWidgets();
            init();
        }).bounds(panelX + 4, tabY, 48, 16).build());

        // Tab: Bookmarks
        addRenderableWidget(Button.builder(
                Component.literal(activeTab == Tab.BOOKMARKS ? "\u00A7lBookmarks" : "Bookmarks"), btn -> {
            playClick();
            activeTab = Tab.BOOKMARKS;
            searchQuery = "";
            if (searchField != null) searchField.setValue("");
            clearWidgets();
            init();
        }).bounds(panelX + 56, tabY, 60, 16).build());

        // Add button (tab-aware)
        addRenderableWidget(Button.builder(Component.literal("+ Add New"), btn -> {
            playClick();
            if (activeTab == Tab.TODOS) {
                manager.addTodo(new TodoItem("New To-Do"));
            } else {
                Minecraft mc = Minecraft.getInstance();
                Bookmark bm = new Bookmark();
                if (mc.player != null) {
                    bm.x = (int) mc.player.getX();
                    bm.y = (int) mc.player.getY();
                    bm.z = (int) mc.player.getZ();
                }
                manager.addBookmark(bm);
            }
            rebuildList();
        }).bounds(panelX + panelW - 86, tabY, 56, 16).build());

        // Close button
        addRenderableWidget(Button.builder(Component.literal("X"), btn -> {
            playClick();
            onClose();
        }).bounds(panelX + panelW - 22, tabY, 16, 16).build());

        // ── Row 2: Search field ──────────────────────────────────────
        searchField = new EditBox(font, panelX + 4, tabY + 20, panelW - 12, 16,
                Component.literal("Search"));
        searchField.setHint(Component.literal("Search..."));
        searchField.setMaxLength(64);
        searchField.setValue(searchQuery);
        searchField.setResponder(text -> {
            searchQuery = text;
            rebuildList();
        });
        addRenderableWidget(searchField);

        rebuildList();
    }

    /** Public hook for the tick handler to refresh the list after auto-purge. */
    public void refreshAfterExternalChange() {
        rebuildList();
    }

    private void rebuildList() {
        clearSuggestions();
        if (todoList != null) {
            for (TodoListEntry entry : todoList.children()) {
                entry.commitPendingEdit();
            }
            removeWidget(todoList);
        }

        int listTop = panelY + headerHeight + 2;
        int listBottom = panelY + panelH - 4;
        int listHeight = listBottom - listTop;

        todoList = new TodoListWidget(minecraft, panelW - 8, listHeight, listTop);
        todoList.setX(panelX + 4);

        int rowIndex = 0;

        if (activeTab == Tab.TODOS) {
            List<TodoItem> todos = manager.getTodos();
            if (!searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase();
                todos = todos.stream()
                        .filter(t -> t.title.toLowerCase().contains(q))
                        .collect(Collectors.toList());
            }
            for (int i = 0; i < todos.size(); i++) {
                TodoItem todo = todos.get(i);
                todoList.addEntry(new TodoHeaderEntry(todo, i, rowIndex % 2 == 1));
                rowIndex++;

                if (settingsOpenIndices.contains(i)) {
                    todoList.addEntry(new TodoSettingsEntry(todo, i, rowIndex % 2 == 1));
                    rowIndex++;
                }

                if (notesOpenIndices.contains(i)) {
                    todoList.addEntry(new NotesEntry(todo, rowIndex % 2 == 1));
                    rowIndex++;
                }

                if (expandedIndices.contains(i)) {
                    for (int s = 0; s < todo.subTasks.size(); s++) {
                        SubTask subTask = todo.subTasks.get(s);
                        String subKey = i + "_" + s;
                        todoList.addEntry(new SubTaskEntry(todo, subTask, i, s, rowIndex % 2 == 1));
                        rowIndex++;

                        if (expandedSubTaskIndices.contains(subKey)) {
                            for (int t = 0; t < subTask.targets.size(); t++) {
                                final int ti = t;
                                todoList.addEntry(new TargetSubEntry(subTask.targets.get(t),
                                        () -> { manager.removeSubTaskTarget(subTask, ti); rebuildList(); },
                                        rowIndex % 2 == 1, 40));
                                rowIndex++;
                            }
                            for (int c = 0; c < subTask.coordinates.size(); c++) {
                                final int ci = c;
                                todoList.addEntry(new CoordSubEntry(subTask.coordinates.get(c),
                                        () -> { manager.removeSubTaskCoordinate(subTask, ci); rebuildList(); },
                                        rowIndex % 2 == 1, 40));
                                rowIndex++;
                            }
                            todoList.addEntry(new SubTaskAddButtonEntry(subTask, rowIndex % 2 == 1));
                            rowIndex++;
                        }
                    }
                    for (int t = 0; t < todo.targets.size(); t++) {
                        final int ti = t;
                        todoList.addEntry(new TargetSubEntry(todo.targets.get(t),
                                () -> { manager.removeTarget(todo, ti); rebuildList(); },
                                rowIndex % 2 == 1, 16));
                        rowIndex++;
                    }
                    for (int c = 0; c < todo.coordinates.size(); c++) {
                        final int ci = c;
                        todoList.addEntry(new CoordSubEntry(todo.coordinates.get(c),
                                () -> { manager.removeCoordinate(todo, ci); rebuildList(); },
                                rowIndex % 2 == 1, 16));
                        rowIndex++;
                    }
                    todoList.addEntry(new AddButtonEntry(todo, rowIndex % 2 == 1));
                    rowIndex++;
                }
            }
        } else {
            // Bookmarks tab
            List<Bookmark> bookmarks = manager.getBookmarks();
            if (!searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase();
                bookmarks = bookmarks.stream()
                        .filter(b -> b.label.toLowerCase().contains(q))
                        .collect(Collectors.toList());
            }
            for (int i = 0; i < bookmarks.size(); i++) {
                Bookmark bm = bookmarks.get(i);
                todoList.addEntry(new BookmarkHeaderEntry(bm, i, rowIndex % 2 == 1));
                rowIndex++;

                if (bookmarkSettingsOpenIndices.contains(i)) {
                    todoList.addEntry(new BookmarkSettingsEntry(bm, i, rowIndex % 2 == 1));
                    rowIndex++;
                }
            }
        }

        addRenderableWidget(todoList);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x80000000);
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, config.panelBackground);
        drawRoundedBorder(context, panelX, panelY, panelW, panelH, config.panelBorder);
        context.fill(panelX + 1, panelY + headerHeight, panelX + panelW - 1, panelY + headerHeight + 1, config.panelBorder);

        super.extractRenderState(context, mouseX, mouseY, delta);

        // Drop indicator while drag-reordering to-dos
        renderDropIndicator(context);

        // Render autocomplete suggestions on top of everything
        renderSuggestions(context, mouseX, mouseY);
    }

    private void renderDropIndicator(GuiGraphicsExtractor ctx) {
        if (draggingFromIndex < 0 || todoList == null) return;
        int x1 = todoList.getX();
        int x2 = x1 + todoList.getWidth() - 12;
        int dropY = -1;
        TodoHeaderEntry lastSeen = null;
        for (TodoListEntry e : todoList.children()) {
            if (e instanceof TodoHeaderEntry h) {
                int top = todoList.getRowTopFor(h);
                int bot = top + h.getHeight();
                if (dragMouseY < top) {
                    dropY = top;
                    break;
                }
                if (dragMouseY < bot) {
                    dropY = (dragMouseY < (top + bot) / 2.0) ? top : bot;
                    break;
                }
                lastSeen = h;
            }
        }
        if (dropY < 0 && lastSeen != null) {
            dropY = todoList.getRowTopFor(lastSeen) + lastSeen.getHeight();
        }
        if (dropY >= 0) {
            ctx.fill(x1, dropY - 1, x2, dropY + 1, config.textCoordinate);
        }
    }

    private void renderSuggestions(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (suggestions.isEmpty() || activeSuggestionField == null) return;

        int maxShow = Math.min(suggestions.size(), 6);
        int itemH = 12;
        int totalH = maxShow * itemH;
        int sx = suggestionX;
        int sy = suggestionY;
        int sw = suggestionWidth;

        // Background
        context.fill(sx, sy, sx + sw, sy + totalH, 0xF0101010);
        context.fill(sx, sy, sx + sw, sy + 1, 0xFF404040); // top border

        for (int i = 0; i < maxShow; i++) {
            int iy = sy + i * itemH;
            boolean hovered = mouseX >= sx && mouseX <= sx + sw && mouseY >= iy && mouseY < iy + itemH;
            if (hovered) {
                context.fill(sx, iy, sx + sw, iy + itemH, 0x40FFFFFF);
            }
            String name = suggestions.get(i);
            // Strip "minecraft:" prefix for display
            String display = name.startsWith("minecraft:") ? name.substring(10) : name;
            context.text(font, display, sx + 2, iy + 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        // Ctrl/Cmd+Shift+Z — redo
        if (input.hasControlDownWithQuirk() && input.hasShiftDown() && input.key() == GLFW.GLFW_KEY_Z) {
            if (manager.canRedo()) {
                manager.redo();
                playClick();
                rebuildList();
                return true;
            }
        }
        // Ctrl/Cmd+Z — undo last deletion
        if (input.hasControlDownWithQuirk() && !input.hasShiftDown() && input.key() == GLFW.GLFW_KEY_Z) {
            if (manager.canUndo()) {
                manager.undo();
                playClick();
                rebuildList();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        // Track Y for drag-drop drop indicator
        dragMouseY = click.y();
        // Check if clicking on a suggestion
        if (!suggestions.isEmpty() && activeSuggestionField != null) {
            int maxShow = Math.min(suggestions.size(), 6);
            int itemH = 12;
            int sx = suggestionX, sy = suggestionY, sw = suggestionWidth;

            if (click.x() >= sx && click.x() <= sx + sw && click.y() >= sy && click.y() < sy + maxShow * itemH) {
                int idx = (int) ((click.y() - sy) / itemH);
                if (idx >= 0 && idx < suggestions.size() && activeSuggestionEntry != null) {
                    activeSuggestionEntry.commitBlockId(suggestions.get(idx));
                    clearSuggestions();
                    playClick();
                    return true;
                }
            }
            // Clicked outside suggestions — close them
            clearSuggestions();
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (draggingFromIndex >= 0) {
            // click.x()/y() are the current cursor position; capture for the drop indicator
            dragMouseY = click.y();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (draggingFromIndex >= 0) {
            int from = draggingFromIndex;
            int to = computeDropIndex(click.y());
            draggingFromIndex = -1;
            if (to >= 0 && to != from) {
                manager.reorderTodo(from, to);
                playClick();
                rebuildList();
                return true;
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    /**
     * Convert a Y coordinate into a drop position in the display view.
     * Walks the visible TodoHeaderEntry rows and finds which one the Y is over.
     * Y above all headers → drop at 0; Y below all → drop at last index.
     */
    private int computeDropIndex(double y) {
        if (todoList == null) return -1;
        TodoHeaderEntry lastSeen = null;
        for (TodoListEntry e : todoList.children()) {
            if (e instanceof TodoHeaderEntry h) {
                int top = todoList.getRowTopFor(h);
                int bot = top + h.getHeight();
                if (y < top) {
                    return h.todoIndex;
                }
                if (y >= top && y < bot) {
                    // Drop above-or-below the midpoint
                    return (y < (top + bot) / 2.0) ? h.todoIndex : h.todoIndex + 1;
                }
                lastSeen = h;
            }
        }
        if (lastSeen != null) return lastSeen.todoIndex + 1;
        return -1;
    }

    void showSuggestions(EditBox field, TargetSubEntry entry, int x, int y, int w) {
        activeSuggestionField = field;
        activeSuggestionEntry = entry;
        suggestionX = x;
        suggestionY = y;
        suggestionWidth = w;
        updateSuggestions(field.getValue());
    }

    void updateSuggestions(String query) {
        String q = query.toLowerCase();
        suggestions = BuiltInRegistries.ITEM.keySet().stream()
                .map(Identifier::toString)
                .filter(id -> {
                    String short_ = id.startsWith("minecraft:") ? id.substring(10) : id;
                    return short_.contains(q) || id.contains(q);
                })
                .sorted((a, b) -> {
                    // Prioritize starts-with matches
                    String sa = a.startsWith("minecraft:") ? a.substring(10) : a;
                    String sb = b.startsWith("minecraft:") ? b.substring(10) : b;
                    boolean aSw = sa.startsWith(q);
                    boolean bSw = sb.startsWith(q);
                    if (aSw != bSw) return aSw ? -1 : 1;
                    return sa.compareTo(sb);
                })
                .limit(30)
                .collect(Collectors.toList());
    }

    void clearSuggestions() {
        suggestions.clear();
        activeSuggestionField = null;
        activeSuggestionEntry = null;
    }

    private void drawRoundedBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, color);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void drawCheckbox(GuiGraphicsExtractor ctx, int cx, int cy, boolean checked) {
        if (checked) {
            ctx.fill(cx, cy, cx + 7, cy + 7, config.checkboxCheckedFill);
            ctx.fill(cx, cy, cx + 7, cy + 1, config.checkboxCheckedBorder);
            ctx.fill(cx, cy + 6, cx + 7, cy + 7, config.checkboxCheckedBorder);
            ctx.fill(cx, cy, cx + 1, cy + 7, config.checkboxCheckedBorder);
            ctx.fill(cx + 6, cy, cx + 7, cy + 7, config.checkboxCheckedBorder);
            ctx.fill(cx + 1, cy + 3, cx + 2, cy + 4, config.checkboxCheckedBorder);
            ctx.fill(cx + 2, cy + 4, cx + 3, cy + 5, config.checkboxCheckedBorder);
            ctx.fill(cx + 3, cy + 3, cx + 4, cy + 4, config.checkboxCheckedBorder);
            ctx.fill(cx + 4, cy + 2, cx + 5, cy + 3, config.checkboxCheckedBorder);
            ctx.fill(cx + 5, cy + 1, cx + 6, cy + 2, config.checkboxCheckedBorder);
        } else {
            ctx.fill(cx, cy, cx + 7, cy + 1, config.checkboxBorder);
            ctx.fill(cx, cy + 6, cx + 7, cy + 7, config.checkboxBorder);
            ctx.fill(cx, cy, cx + 1, cy + 7, config.checkboxBorder);
            ctx.fill(cx + 6, cy, cx + 7, cy + 7, config.checkboxBorder);
        }
    }

    private void drawDragHandle(GuiGraphicsExtractor ctx, int hx, int hy, int color) {
        // ≡ glyph: 3 horizontal lines, 4px wide, evenly spaced
        ctx.fill(hx, hy + 2, hx + 4, hy + 3, color);
        ctx.fill(hx, hy + 6, hx + 4, hy + 7, color);
        ctx.fill(hx, hy + 10, hx + 4, hy + 11, color);
    }

    private void drawProgressBar(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float progress, boolean complete) {
        ctx.fill(x, y, x + w, y + h, config.progressBarTrack);
        int fillW = (int) (w * Math.min(1.0f, progress));
        if (fillW > 0) {
            int fillColor = complete ? config.progressBarComplete : config.progressBarFill;
            ctx.fill(x, y, x + fillW, y + h, fillColor);
        }
    }

    private void playClick() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
        }
    }

    private static int applyAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // Commit any in-progress text edits before the screen is destroyed,
        // so the user never has to press Enter to save changes.
        if (todoList != null) {
            for (TodoListEntry entry : todoList.children()) {
                entry.commitPendingEdit();
            }
        }
        super.removed();
    }

    // ══════════════════════════════════════════════════════════════════
    // Scrollable list widget
    // ══════════════════════════════════════════════════════════════════

    private class TodoListWidget extends ContainerObjectSelectionList<TodoListEntry> {
        public TodoListWidget(Minecraft client, int width, int height, int y) {
            super(client, width, height, y, 24);
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 12;
        }

        @Override
        protected int scrollBarX() {
            return this.getX() + this.getWidth() - 6;
        }

        @Override
        public int addEntry(TodoListEntry entry) {
            return super.addEntry(entry);
        }

        /** Public accessor for the inherited (protected) getRowTop(int). */
        public int getRowTopFor(TodoListEntry entry) {
            int idx = children().indexOf(entry);
            if (idx < 0) return 0;
            return getRowTop(idx);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Abstract base entry
    // ══════════════════════════════════════════════════════════════════

    private abstract class TodoListEntry extends ContainerObjectSelectionList.Entry<TodoListEntry> {
        protected final boolean isOdd;
        protected final List<GuiEventListener> entryChildren = new ArrayList<>();
        protected final List<NarratableEntry> entrySelectables = new ArrayList<>();

        TodoListEntry(boolean isOdd) {
            this.isOdd = isOdd;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return entryChildren;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return entrySelectables;
        }

        protected void renderRowBackground(GuiGraphicsExtractor context) {
            int tint = isOdd ? config.rowTintOdd : config.rowTintEven;
            if ((tint & 0xFF000000) != 0) {
                context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), tint);
            }
        }

        /** Commit any in-progress text edit. Default: no-op. Override in entries with text fields. */
        public void commitPendingEdit() {}
    }

    // ══════════════════════════════════════════════════════════════════
    // To-do header entry (checkbox + title + timer + gear + expand + delete)
    // ══════════════════════════════════════════════════════════════════

    private class TodoHeaderEntry extends TodoListEntry {
        private final TodoItem todo;
        private final int todoIndex;
        private EditBox titleField;
        private boolean editing = false;
        private final Button expandBtn;
        private final Button deleteBtn;
        private final Button gearBtn;
        private final Button pinBtn;
        private final Button notesBtn;

        TodoHeaderEntry(TodoItem todo, int todoIndex, boolean isOdd) {
            super(isOdd);
            this.todo = todo;
            this.todoIndex = todoIndex;

            boolean expanded = expandedIndices.contains(todoIndex);
            expandBtn = Button.builder(Component.literal(expanded ? "v" : ">"), btn -> {
                playClick();
                if (expandedIndices.contains(todoIndex)) expandedIndices.remove(todoIndex);
                else expandedIndices.add(todoIndex);
                rebuildList();
            }).bounds(0, 0, 14, 14).build();

            deleteBtn = Button.builder(Component.literal("X"), btn -> {
                playClick();
                expandedIndices.remove(todoIndex);
                settingsOpenIndices.remove(todoIndex);
                notesOpenIndices.remove(todoIndex);
                Set<Integer> updated = new HashSet<>();
                for (int idx : expandedIndices) updated.add(idx > todoIndex ? idx - 1 : idx);
                expandedIndices.clear();
                expandedIndices.addAll(updated);
                Set<Integer> updatedSettings = new HashSet<>();
                for (int idx : settingsOpenIndices) updatedSettings.add(idx > todoIndex ? idx - 1 : idx);
                settingsOpenIndices.clear();
                settingsOpenIndices.addAll(updatedSettings);
                Set<Integer> updatedNotes = new HashSet<>();
                for (int idx : notesOpenIndices) updatedNotes.add(idx > todoIndex ? idx - 1 : idx);
                notesOpenIndices.clear();
                notesOpenIndices.addAll(updatedNotes);
                manager.removeTodo(todoIndex);
                rebuildList();
            }).bounds(0, 0, 14, 14).build();

            // Gear button toggles per-to-do settings panel
            gearBtn = Button.builder(Component.literal("Set"), btn -> {
                playClick();
                if (settingsOpenIndices.contains(todoIndex)) settingsOpenIndices.remove(todoIndex);
                else settingsOpenIndices.add(todoIndex);
                rebuildList();
            }).bounds(0, 0, 20, 14).build();

            // Notes button toggles notes panel
            notesBtn = Button.builder(Component.literal("N"), btn -> {
                playClick();
                if (notesOpenIndices.contains(todoIndex)) notesOpenIndices.remove(todoIndex);
                else notesOpenIndices.add(todoIndex);
                rebuildList();
            }).bounds(0, 0, 14, 14).build();

            // Pin button toggles pinned state and reorders the list
            // "§6Pin" renders the text in gold when pinned, plain "Pin" otherwise
            String pinLabel = todo.pinned ? "\u00A76Pin" : "Pin";
            pinBtn = Button.builder(Component.literal(pinLabel), btn -> {
                playClick();
                // Reordering invalidates tracked expansion indices — clear them
                expandedIndices.clear();
                settingsOpenIndices.clear();
                notesOpenIndices.clear();
                expandedSubTaskIndices.clear();
                manager.togglePinned(todoIndex);
                rebuildList();
            }).bounds(0, 0, 22, 14).build();

            entryChildren.add(pinBtn);
            entryChildren.add(notesBtn);
            entryChildren.add(gearBtn);
            entryChildren.add(expandBtn);
            entryChildren.add(deleteBtn);
            entrySelectables.add(pinBtn);
            entrySelectables.add(notesBtn);
            entrySelectables.add(gearBtn);
            entrySelectables.add(expandBtn);
            entrySelectables.add(deleteBtn);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();

            // Auto-commit on focus loss (click-outside)
            if (editing && titleField != null && !titleField.isFocused()) {
                commitEdit();
            }

            renderRowBackground(context);
            Font tr = Minecraft.getInstance().font;
            boolean completed = todo.isCompleted();

            // Drag handle (left edge) — slightly brighter when row hovered
            int handleColor = (mouseX >= x && mouseX <= x + 7
                    && mouseY >= y && mouseY <= y + getHeight())
                    ? config.textPrimary
                    : applyAlpha(config.textSecondary, 100);
            drawDragHandle(context, x + 1, y + 6, handleColor);

            // Color bar (left edge indicator) — sits to the right of the drag handle
            if (todo.todoColor != 0) {
                context.fill(x + 6, y + 1, x + 8, y + getHeight() - 1, todo.todoColor);
            }

            // Checkbox — shifted right to make room for the drag handle
            drawCheckbox(context, x + 11, y + 7, completed);

            // Title
            int titleStartX = x + 22;
            if (editing && titleField != null) {
                titleField.setX(titleStartX);
                titleField.setY(y + 2);
                titleField.setWidth(w - 126);
                titleField.extractRenderState(context, mouseX, mouseY, delta);
            } else {
                int titleColor = completed ? applyAlpha(config.textCompleted, config.completedItemAlpha) : config.textPrimary;
                String displayTitle = todo.title;
                if (completed) displayTitle = "\u00A7m" + displayTitle + "\u00A7r";
                context.text(tr, displayTitle, titleStartX, y + 7, titleColor);

            }

            // Buttons (right side): pin (22w), N (14w), gear (20w), expand (14w), delete (14w)
            deleteBtn.setX(x + w - 16);
            deleteBtn.setY(y + 3);
            expandBtn.setX(x + w - 34);
            expandBtn.setY(y + 3);
            gearBtn.setX(x + w - 56);
            gearBtn.setY(y + 3);
            notesBtn.setX(x + w - 72);
            notesBtn.setY(y + 3);
            pinBtn.setX(x + w - 96);
            pinBtn.setY(y + 3);

            pinBtn.extractRenderState(context, mouseX, mouseY, delta);
            notesBtn.extractRenderState(context, mouseX, mouseY, delta);
            gearBtn.extractRenderState(context, mouseX, mouseY, delta);
            expandBtn.extractRenderState(context, mouseX, mouseY, delta);
            deleteBtn.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            int x = getX();
            int y = getY();
            // Drag handle: leftmost ~8px (covers handle + color bar). Starts a drag, no other action.
            if (click.button() == 0 && !editing
                    && click.x() >= x && click.x() <= x + 8
                    && click.y() >= y && click.y() <= y + getHeight()) {
                draggingFromIndex = todoIndex;
                dragMouseY = click.y();
                playClick();
                return true;
            }
            // Click on checkbox area to toggle manual completion (shifted right)
            if (click.button() == 0 && click.x() >= x + 9 && click.x() <= x + 20
                    && click.y() >= y && click.y() <= y + getHeight()) {
                todo.manuallyCompleted = !todo.manuallyCompleted;
                manager.markCompletionTimestamp(todo);
                manager.save();
                playClick();
                return true;
            }
            // Click on title area to start editing
            if (click.button() == 0 && !editing) {
                int titleStartX = x + 22;
                int titleEndX = x + getWidth() - 100;
                if (click.x() >= titleStartX && click.x() <= titleEndX
                        && click.y() >= y && click.y() <= y + getHeight()) {
                    startEditing();
                    return true;
                }
            }
            return super.mouseClicked(click, doubled);
        }

        private void startEditing() {
            editing = true;
            titleField = new EditBox(Minecraft.getInstance().font,
                    0, 0, 100, 16, Component.literal("Title"));
            titleField.setValue("");
            titleField.setHint(Component.literal(todo.title));
            titleField.setMaxLength(64);
            entryChildren.add(titleField);
            entrySelectables.add(titleField);
            setFocused(titleField);
            titleField.setFocused(true);
        }

        private void commitEdit() {
            if (titleField != null && editing) {
                String newTitle = titleField.getValue().trim();
                if (!newTitle.isEmpty()) {
                    todo.title = newTitle;
                }
                manager.save();
                editing = false;
                entryChildren.remove(titleField);
                entrySelectables.remove(titleField);
                titleField = null;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (editing && input.key() == 257) { // Enter
                commitEdit();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        public void commitPendingEdit() {
            if (editing) commitEdit();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Per-to-do settings entry (auto-complete toggle, timer)
    // ══════════════════════════════════════════════════════════════════

    private class TodoSettingsEntry extends TodoListEntry {
        private final TodoItem todo;
        private final Button autoCompleteBtn;
        private final Button colorBtn;
        private final Button thisWorldBtn;
        private final Button globalBtn;

        TodoSettingsEntry(TodoItem todo, int todoIndex, boolean isOdd) {
            super(isOdd);
            this.todo = todo;

            autoCompleteBtn = Button.builder(
                    Component.literal(todo.autoComplete ? "Auto-complete: ON" : "Auto-complete: OFF"),
                    btn -> {
                        playClick();
                        todo.autoComplete = !todo.autoComplete;
                        btn.setMessage(Component.literal(todo.autoComplete ? "Auto-complete: ON" : "Auto-complete: OFF"));
                        btn.setTooltip(Tooltip.create(Component.literal(
                                todo.autoComplete ? "Completes when all targets are met" : "Manual completion only")));
                        manager.save();
                    }
            ).bounds(0, 0, 120, 14).build();
            autoCompleteBtn.setTooltip(Tooltip.create(Component.literal(
                    todo.autoComplete ? "Completes when all targets are met" : "Manual completion only")));

            colorBtn = Button.builder(Component.literal(getColorName(todo.todoColor)), btn -> {
                playClick();
                int idx = getColorIndex(todo.todoColor);
                idx = (idx + 1) % PRESET_COLORS.length;
                todo.todoColor = PRESET_COLORS[idx];
                btn.setMessage(Component.literal(COLOR_NAMES[idx]));
                manager.save();
            }).bounds(0, 0, 54, 14).build();

            thisWorldBtn = Button.builder(Component.literal("This world"), btn -> {
                playClick();
                todo.worldKey = GatherlyManager.getCurrentWorldKey();
                manager.save();
            }).bounds(0, 0, 64, 14).build();

            globalBtn = Button.builder(Component.literal("Global"), btn -> {
                playClick();
                todo.worldKey = "";
                manager.save();
            }).bounds(0, 0, 42, 14).build();

            entryChildren.add(autoCompleteBtn);
            entryChildren.add(colorBtn);
            entryChildren.add(thisWorldBtn);
            entryChildren.add(globalBtn);
            entrySelectables.add(autoCompleteBtn);
            entrySelectables.add(colorBtn);
            entrySelectables.add(thisWorldBtn);
            entrySelectables.add(globalBtn);
        }

        private String getColorName(int color) {
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                if (PRESET_COLORS[i] == color) return COLOR_NAMES[i];
            }
            return COLOR_NAMES[0];
        }

        private int getColorIndex(int color) {
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                if (PRESET_COLORS[i] == color) return i;
            }
            return 0;
        }

        @Override
        public int getHeight() {
            return 40;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();

            renderRowBackground(context);
            Font tr = Minecraft.getInstance().font;

            int indent = 16;

            // ── Row 1: auto-complete + color ──────────────────────────────
            autoCompleteBtn.setX(x + indent);
            autoCompleteBtn.setY(y + 3);
            autoCompleteBtn.extractRenderState(context, mouseX, mouseY, delta);

            context.text(tr, "Color:", x + w - 136, y + 6, config.textSecondary);
            colorBtn.setX(x + w - 100);
            colorBtn.setY(y + 3);
            colorBtn.extractRenderState(context, mouseX, mouseY, delta);

            int swatchColor = todo.todoColor != 0 ? todo.todoColor : config.textSecondary;
            context.fill(x + w - 106, y + 5, x + w - 102, y + 13, swatchColor);

            // ── Row 2: world key ──────────────────────────────────────────
            int y2 = y + 22;

            context.text(tr, "World:", x + indent, y2 + 3, config.textSecondary);

            String worldDisplay = todo.worldKey.isEmpty() ? "Global" : todo.worldKey;
            int worldColor = todo.worldKey.isEmpty() ? config.textSecondary : config.textPrimary;
            int labelX = x + indent + tr.width("World:") + 4;
            int maxTextW = w - indent - tr.width("World:") - 4 - 64 - 42 - 16;
            String truncated = worldDisplay;
            if (tr.width(truncated) > maxTextW) {
                while (truncated.length() > 1 && tr.width(truncated + "\u2026") > maxTextW) {
                    truncated = truncated.substring(0, truncated.length() - 1);
                }
                truncated += "\u2026";
            }
            context.text(tr, truncated, labelX, y2 + 3, worldColor);

            String curKey = GatherlyManager.getCurrentWorldKey();
            thisWorldBtn.active = !curKey.isEmpty() && !curKey.equals(todo.worldKey);
            globalBtn.active = !todo.worldKey.isEmpty();

            globalBtn.setX(x + w - 44);
            globalBtn.setY(y2 + 1);
            globalBtn.extractRenderState(context, mouseX, mouseY, delta);

            thisWorldBtn.setX(x + w - 44 - 66);
            thisWorldBtn.setY(y2 + 1);
            thisWorldBtn.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Target sub-entry (block picker, progress bar, count edit, buttons)
    // ══════════════════════════════════════════════════════════════════

    private class TargetSubEntry extends TodoListEntry {
        private final TargetEntry target;
        private final Runnable removeAction;
        private final int indent;
        private final Button removeBtn;

        private EditBox blockIdField;
        private boolean editingBlockId = false;
        private EditBox countField;
        private boolean editingCount = false;

        TargetSubEntry(TargetEntry target, Runnable removeAction, boolean isOdd, int indent) {
            super(isOdd);
            this.target = target;
            this.removeAction = removeAction;
            this.indent = indent;

            removeBtn = Button.builder(Component.literal("x"), btn -> {
                playClick();
                removeAction.run();
            }).bounds(0, 0, 14, 14).build();

            entryChildren.add(removeBtn);
            entrySelectables.add(removeBtn);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();

            // Auto-commit on focus loss (click-outside).
            // Skip while suggestions are open: focus may transiently move to the popup.
            if (editingBlockId && blockIdField != null
                    && !blockIdField.isFocused()
                    && activeSuggestionField != blockIdField) {
                commitBlockIdFromField();
            }
            if (editingCount && countField != null && !countField.isFocused()) {
                commitCount();
            }

            renderRowBackground(context);
            Font tr = Minecraft.getInstance().font;

            if (editingBlockId && blockIdField != null) {
                blockIdField.setX(x + indent);
                blockIdField.setY(y + 2);
                blockIdField.setWidth(100);
                blockIdField.extractRenderState(context, mouseX, mouseY, delta);
            } else {
                String blockLabel = target.blockId;
                if (blockLabel.startsWith("minecraft:")) blockLabel = blockLabel.substring(10);
                context.text(tr, blockLabel, x + indent, y + 6, config.textProgress);
            }

            int barX = x + indent + 106;
            int barY = y + 8;
            int barW = 60;
            int barH = 3;
            drawProgressBar(context, barX, barY, barW, barH, target.progress(), target.isMet());

            if (editingCount && countField != null) {
                countField.setX(barX + barW + 4);
                countField.setY(y + 2);
                countField.extractRenderState(context, mouseX, mouseY, delta);
            } else {
                String countText = target.currentCount + "/" + target.requiredCount;
                context.text(tr, countText, barX + barW + 4, y + 6, config.textProgress);
            }

            removeBtn.setX(x + w - 14);
            removeBtn.setY(y + 3);
            removeBtn.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            int x = getX();
            int y = getY();

            if (click.button() == 0 && !editingBlockId) {
                if (click.x() >= x + indent && click.x() <= x + indent + 100
                        && click.y() >= y && click.y() <= y + getHeight()) {
                    startEditingBlockId();
                    return true;
                }
            }

            int barX = x + indent + 106;
            int barW = 60;
            int countX = barX + barW + 4;
            if (click.button() == 0 && !editingCount) {
                if (click.x() >= countX && click.x() <= countX + 50
                        && click.y() >= y && click.y() <= y + getHeight()) {
                    startEditingCount();
                    return true;
                }
            }

            return super.mouseClicked(click, doubled);
        }

        private void startEditingBlockId() {
            editingBlockId = true;
            blockIdField = new EditBox(Minecraft.getInstance().font,
                    0, 0, 100, 14, Component.literal("Block ID"));
            String display = target.blockId;
            if (display.startsWith("minecraft:")) display = display.substring(10);
            blockIdField.setValue(display);
            blockIdField.setMaxLength(64);
            blockIdField.setResponder(text -> {
                showSuggestions(blockIdField, this, getX() + indent, getY() + getHeight(), 140);
                updateSuggestions(text);
            });
            entryChildren.add(blockIdField);
            entrySelectables.add(blockIdField);
            setFocused(blockIdField);
            blockIdField.setFocused(true);

            showSuggestions(blockIdField, this,
                    getX() + indent, getY() + getHeight(), 140);
        }

        void commitBlockId(String fullId) {
            target.blockId = fullId;
            editingBlockId = false;
            if (blockIdField != null) {
                entryChildren.remove(blockIdField);
                entrySelectables.remove(blockIdField);
                blockIdField = null;
            }
            manager.save();
        }

        /** Commit the current text in blockIdField, auto-prefixing "minecraft:" if needed. */
        private void commitBlockIdFromField() {
            if (blockIdField == null) return;
            String text = blockIdField.getValue().trim();
            if (text.isEmpty()) {
                // Empty: just exit edit mode without changing blockId
                editingBlockId = false;
                entryChildren.remove(blockIdField);
                entrySelectables.remove(blockIdField);
                blockIdField = null;
            } else {
                if (!text.contains(":")) text = "minecraft:" + text;
                commitBlockId(text);
            }
            clearSuggestions();
        }

        private void startEditingCount() {
            editingCount = true;
            countField = new EditBox(Minecraft.getInstance().font,
                    0, 0, 40, 14, Component.literal("Count"));
            countField.setValue(String.valueOf(target.requiredCount));
            countField.setMaxLength(6);
            entryChildren.add(countField);
            entrySelectables.add(countField);
            setFocused(countField);
            countField.setFocused(true);
        }

        private void commitCount() {
            if (countField != null && editingCount) {
                try {
                    target.requiredCount = Math.max(1, Integer.parseInt(countField.getValue().trim()));
                } catch (NumberFormatException ignored) {}
                editingCount = false;
                entryChildren.remove(countField);
                entrySelectables.remove(countField);
                countField = null;
                manager.save();
            }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (input.key() == 257) {
                if (editingBlockId && blockIdField != null) {
                    commitBlockIdFromField();
                    return true;
                }
                if (editingCount) {
                    commitCount();
                    return true;
                }
            }
            return super.keyPressed(input);
        }

        @Override
        public void commitPendingEdit() {
            if (editingBlockId && blockIdField != null) {
                commitBlockIdFromField();
            }
            if (editingCount && countField != null) {
                commitCount();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Coordinate sub-entry
    // ══════════════════════════════════════════════════════════════════

    private class CoordSubEntry extends TodoListEntry {
        private final CoordinateEntry coord;
        private final Runnable removeAction;
        private final int indent;
        private final Button copyBtn;
        private final Button removeBtn;
        private EditBox labelField;
        private boolean editingLabel = false;

        CoordSubEntry(CoordinateEntry coord, Runnable removeAction, boolean isOdd, int indent) {
            super(isOdd);
            this.coord = coord;
            this.removeAction = removeAction;
            this.indent = indent;

            copyBtn = Button.builder(Component.literal("Copy"), btn -> {
                playClick();
                Minecraft.getInstance().keyboardHandler.setClipboard(
                        coord.x + " " + coord.y + " " + coord.z);
            }).bounds(0, 0, 30, 14).build();

            removeBtn = Button.builder(Component.literal("x"), btn -> {
                playClick();
                removeAction.run();
            }).bounds(0, 0, 14, 14).build();

            entryChildren.add(copyBtn);
            entryChildren.add(removeBtn);
            entrySelectables.add(copyBtn);
            entrySelectables.add(removeBtn);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();

            // Auto-commit on focus loss (click-outside)
            if (editingLabel && labelField != null && !labelField.isFocused()) {
                commitLabel();
            }

            renderRowBackground(context);
            Font tr = Minecraft.getInstance().font;

            context.fill(x + indent, y + 7, x + indent + 3, y + 10, config.textCoordinate);

            int labelX = x + indent + 6;
            if (editingLabel && labelField != null) {
                labelField.setX(labelX);
                labelField.setY(y + 2);
                labelField.setWidth(100);
                labelField.extractRenderState(context, mouseX, mouseY, delta);
            } else {
                String label = (coord.label != null && !coord.label.isEmpty()) ? coord.label : "Waypoint";
                context.text(tr, label, labelX, y + 6, config.textCoordinate);

                String coordText = coord.x + ", " + coord.y + ", " + coord.z;
                int coordTextX = labelX + tr.width(label) + 8;
                context.text(tr, coordText, coordTextX, y + 6, config.textSecondary);
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int dist = GatherlyManager.distanceTo(coord, mc.player);
                String distText = "~" + dist + " blocks";
                context.text(tr, distText, x + w - 100, y + 6, config.textCoordinate);
            }

            copyBtn.setX(x + w - 50);
            copyBtn.setY(y + 3);
            removeBtn.setX(x + w - 14);
            removeBtn.setY(y + 3);

            copyBtn.extractRenderState(context, mouseX, mouseY, delta);
            removeBtn.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            int x = getX();
            int y = getY();

            if (click.button() == 0 && !editingLabel) {
                int labelX = x + indent + 6;
                if (click.x() >= labelX && click.x() <= labelX + 100
                        && click.y() >= y && click.y() <= y + getHeight()) {
                    startEditingLabel();
                    return true;
                }
            }
            return super.mouseClicked(click, doubled);
        }

        private void startEditingLabel() {
            editingLabel = true;
            String currentLabel = (coord.label != null && !coord.label.isEmpty()) ? coord.label : "Waypoint";
            labelField = new EditBox(Minecraft.getInstance().font,
                    0, 0, 100, 14, Component.literal("Label"));
            labelField.setValue("");
            labelField.setHint(Component.literal(currentLabel));
            labelField.setMaxLength(32);
            entryChildren.add(labelField);
            entrySelectables.add(labelField);
            setFocused(labelField);
            labelField.setFocused(true);
        }

        private void commitLabel() {
            if (labelField != null && editingLabel) {
                String newLabel = labelField.getValue().trim();
                if (!newLabel.isEmpty()) {
                    coord.label = newLabel;
                }
                manager.save();
                editingLabel = false;
                entryChildren.remove(labelField);
                entrySelectables.remove(labelField);
                labelField = null;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (editingLabel && input.key() == 257) {
                commitLabel();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        public void commitPendingEdit() {
            if (editingLabel) commitLabel();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Sub-task entry
    // ══════════════════════════════════════════════════════════════════

    private class SubTaskEntry extends TodoListEntry {
        private final TodoItem parentTodo;
        private final SubTask subTask;
        private final int todoIndex;
        private final int subTaskIndex;
        private final Button expandBtn;
        private final Button removeBtn;
        private EditBox titleField;
        private boolean editing = false;

        SubTaskEntry(TodoItem parentTodo, SubTask subTask, int todoIndex, int subTaskIndex, boolean isOdd) {
            super(isOdd);
            this.parentTodo = parentTodo;
            this.subTask = subTask;
            this.todoIndex = todoIndex;
            this.subTaskIndex = subTaskIndex;

            String subKey = todoIndex + "_" + subTaskIndex;
            boolean expanded = expandedSubTaskIndices.contains(subKey);
            expandBtn = Button.builder(Component.literal(expanded ? "v" : ">"), btn -> {
                playClick();
                String key = todoIndex + "_" + subTaskIndex;
                if (expandedSubTaskIndices.contains(key)) expandedSubTaskIndices.remove(key);
                else expandedSubTaskIndices.add(key);
                rebuildList();
            }).bounds(0, 0, 14, 14).build();

            removeBtn = Button.builder(Component.literal("x"), btn -> {
                playClick();
                expandedSubTaskIndices.remove(todoIndex + "_" + subTaskIndex);
                manager.removeSubTask(parentTodo, subTaskIndex);
                rebuildList();
            }).bounds(0, 0, 14, 14).build();

            entryChildren.add(expandBtn);
            entryChildren.add(removeBtn);
            entrySelectables.add(expandBtn);
            entrySelectables.add(removeBtn);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();

            // Auto-commit on focus loss (click-outside)
            if (editing && titleField != null && !titleField.isFocused()) {
                commitEdit();
            }

            renderRowBackground(context);
            Font tr = Minecraft.getInstance().font;
            int indent = 28;

            boolean completed = subTask.isCompleted();
            drawCheckbox(context, x + indent, y + 8, completed);

            int titleStartX = x + indent + 12;
            if (editing && titleField != null) {
                titleField.setX(titleStartX);
                titleField.setY(y + 4);
                titleField.setWidth(w - indent - 60);
                titleField.extractRenderState(context, mouseX, mouseY, delta);
            } else {
                int titleColor = completed
                        ? applyAlpha(config.textCompleted, config.completedItemAlpha)
                        : config.textPrimary;
                String displayTitle = subTask.title;
                if (completed) displayTitle = "\u00A7m" + displayTitle + "\u00A7r";
                context.text(tr, displayTitle, titleStartX, y + 8, titleColor);

                // Show target progress summary if has targets
                if (!subTask.targets.isEmpty()) {
                    int done = 0;
                    for (TargetEntry t : subTask.targets) { if (t.isMet()) done++; }
                    String prog = done + "/" + subTask.targets.size();
                    int titleW = tr.width(subTask.title);
                    context.text(tr, prog, titleStartX + titleW + 8, y + 8, config.textProgress);
                }
            }

            expandBtn.setX(x + w - 34);
            expandBtn.setY(y + 3);
            removeBtn.setX(x + w - 14);
            removeBtn.setY(y + 3);

            expandBtn.extractRenderState(context, mouseX, mouseY, delta);
            removeBtn.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            int x = getX();
            int y = getY();
            int indent = 28;

            // Checkbox click — toggle manual completion
            if (click.button() == 0 && click.x() >= x + indent && click.x() <= x + indent + 10
                    && click.y() >= y && click.y() <= y + getHeight()) {
                subTask.completed = !subTask.completed;
                manager.save();
                playClick();
                return true;
            }

            // Title click to edit
            if (click.button() == 0 && !editing) {
                int titleStartX = x + indent + 12;
                int titleEndX = x + getWidth() - 40;
                if (click.x() >= titleStartX && click.x() <= titleEndX
                        && click.y() >= y && click.y() <= y + getHeight()) {
                    startEditing();
                    return true;
                }
            }
            return super.mouseClicked(click, doubled);
        }

        private void startEditing() {
            editing = true;
            titleField = new EditBox(Minecraft.getInstance().font,
                    0, 0, 100, 16, Component.literal("Sub-task"));
            titleField.setValue("");
            titleField.setHint(Component.literal(subTask.title));
            titleField.setMaxLength(64);
            entryChildren.add(titleField);
            entrySelectables.add(titleField);
            setFocused(titleField);
            titleField.setFocused(true);
        }

        private void commitEdit() {
            if (titleField != null && editing) {
                String newTitle = titleField.getValue().trim();
                if (!newTitle.isEmpty()) {
                    subTask.title = newTitle;
                }
                manager.save();
                editing = false;
                entryChildren.remove(titleField);
                entrySelectables.remove(titleField);
                titleField = null;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (editing && input.key() == 257) {
                commitEdit();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        public void commitPendingEdit() {
            if (editing) commitEdit();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Sub-task add buttons entry (+ Target, + Coord for a sub-task)
    // ══════════════════════════════════════════════════════════════════

    private class SubTaskAddButtonEntry extends TodoListEntry {
        private final Button addTargetBtn;
        private final Button addCoordBtn;

        SubTaskAddButtonEntry(SubTask subTask, boolean isOdd) {
            super(isOdd);

            addTargetBtn = Button.builder(Component.literal("+ Target"), btn -> {
                playClick();
                manager.addSubTaskTarget(subTask, new TargetEntry());
                rebuildList();
            }).bounds(0, 0, 54, 14).build();

            addCoordBtn = Button.builder(Component.literal("+ Coord"), btn -> {
                playClick();
                Minecraft mc = Minecraft.getInstance();
                Player player = mc != null ? mc.player : null;
                CoordinateEntry coord = new CoordinateEntry();
                if (player != null) {
                    coord.x = (int) player.getX();
                    coord.y = (int) player.getY();
                    coord.z = (int) player.getZ();
                }
                manager.addSubTaskCoordinate(subTask, coord);
                rebuildList();
            }).bounds(0, 0, 54, 14).build();

            entryChildren.add(addTargetBtn);
            entryChildren.add(addCoordBtn);
            entrySelectables.add(addTargetBtn);
            entrySelectables.add(addCoordBtn);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();

            renderRowBackground(context);

            int indent = 40;
            addTargetBtn.setX(x + indent);
            addTargetBtn.setY(y + 3);
            addCoordBtn.setX(x + indent + 58);
            addCoordBtn.setY(y + 3);

            addTargetBtn.extractRenderState(context, mouseX, mouseY, delta);
            addCoordBtn.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Notes entry (editable text field for to-do notes)
    // ══════════════════════════════════════════════════════════════════

    private class NotesEntry extends TodoListEntry {
        private final TodoItem todo;
        private final EditBox notesField;

        NotesEntry(TodoItem todo, boolean isOdd) {
            super(isOdd);
            this.todo = todo;

            notesField = new EditBox(Minecraft.getInstance().font,
                    0, 0, 200, 16, Component.literal("Notes"));
            notesField.setValue(todo.notes != null ? todo.notes : "");
            notesField.setHint(Component.literal("Add notes..."));
            notesField.setMaxLength(256);
            notesField.setResponder(text -> {
                todo.notes = text;
                manager.save();
            });

            entryChildren.add(notesField);
            entrySelectables.add(notesField);
        }

        @Override
        public int getHeight() {
            return 22;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();

            renderRowBackground(context);
            Font tr = Minecraft.getInstance().font;

            context.text(tr, "Notes:", x + 16, y + 6, config.textSecondary);

            notesField.setX(x + 16 + tr.width("Notes:") + 4);
            notesField.setY(y + 2);
            notesField.setWidth(w - 16 - tr.width("Notes:") - 8);
            notesField.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            // Focus the notes field on click
            if (click.button() == 0) {
                setFocused(notesField);
                notesField.setFocused(true);
            }
            return super.mouseClicked(click, doubled);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Add buttons entry
    // ══════════════════════════════════════════════════════════════════

    private class AddButtonEntry extends TodoListEntry {
        private final Button addSubTaskBtn;
        private final Button addTargetBtn;
        private final Button addCoordBtn;

        AddButtonEntry(TodoItem todo, boolean isOdd) {
            super(isOdd);

            addSubTaskBtn = Button.builder(Component.literal("+ Sub-task"), btn -> {
                playClick();
                manager.addSubTask(todo, new SubTask());
                rebuildList();
            }).bounds(0, 0, 58, 14).build();

            addTargetBtn = Button.builder(Component.literal("+ Target"), btn -> {
                playClick();
                manager.addTarget(todo, new TargetEntry());
                rebuildList();
            }).bounds(0, 0, 54, 14).build();

            addCoordBtn = Button.builder(Component.literal("+ Coord"), btn -> {
                playClick();
                Minecraft mc = Minecraft.getInstance();
                Player player = mc != null ? mc.player : null;
                CoordinateEntry coord = new CoordinateEntry();
                if (player != null) {
                    coord.x = (int) player.getX();
                    coord.y = (int) player.getY();
                    coord.z = (int) player.getZ();
                }
                manager.addCoordinate(todo, coord);
                rebuildList();
            }).bounds(0, 0, 54, 14).build();

            entryChildren.add(addSubTaskBtn);
            entryChildren.add(addTargetBtn);
            entryChildren.add(addCoordBtn);
            entrySelectables.add(addSubTaskBtn);
            entrySelectables.add(addTargetBtn);
            entrySelectables.add(addCoordBtn);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();

            renderRowBackground(context);

            int indent = 16;
            addSubTaskBtn.setX(x + indent);
            addSubTaskBtn.setY(y + 3);
            addTargetBtn.setX(x + indent + 62);
            addTargetBtn.setY(y + 3);
            addCoordBtn.setX(x + indent + 120);
            addCoordBtn.setY(y + 3);

            addSubTaskBtn.extractRenderState(context, mouseX, mouseY, delta);
            addTargetBtn.extractRenderState(context, mouseX, mouseY, delta);
            addCoordBtn.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Bookmark header entry (color dot + label + coords + expand + delete)
    // ══════════════════════════════════════════════════════════════════

    private class BookmarkHeaderEntry extends TodoListEntry {
        private final Bookmark bookmark;
        private final int bookmarkIndex;
        private final Button expandBtn;
        private final Button deleteBtn;
        private EditBox labelField;
        private boolean editing = false;

        BookmarkHeaderEntry(Bookmark bookmark, int bookmarkIndex, boolean isOdd) {
            super(isOdd);
            this.bookmark = bookmark;
            this.bookmarkIndex = bookmarkIndex;

            boolean expanded = bookmarkSettingsOpenIndices.contains(bookmarkIndex);
            expandBtn = Button.builder(Component.literal(expanded ? "v" : ">"), btn -> {
                playClick();
                if (bookmarkSettingsOpenIndices.contains(bookmarkIndex))
                    bookmarkSettingsOpenIndices.remove(bookmarkIndex);
                else bookmarkSettingsOpenIndices.add(bookmarkIndex);
                rebuildList();
            }).bounds(0, 0, 14, 14).build();

            deleteBtn = Button.builder(Component.literal("X"), btn -> {
                playClick();
                bookmarkSettingsOpenIndices.remove(bookmarkIndex);
                Set<Integer> updated = new HashSet<>();
                for (int idx : bookmarkSettingsOpenIndices)
                    updated.add(idx > bookmarkIndex ? idx - 1 : idx);
                bookmarkSettingsOpenIndices.clear();
                bookmarkSettingsOpenIndices.addAll(updated);
                manager.removeBookmark(bookmarkIndex);
                rebuildList();
            }).bounds(0, 0, 14, 14).build();

            entryChildren.add(expandBtn);
            entryChildren.add(deleteBtn);
            entrySelectables.add(expandBtn);
            entrySelectables.add(deleteBtn);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();

            if (editing && labelField != null && !labelField.isFocused()) {
                commitEdit();
            }

            renderRowBackground(context);
            Font tr = Minecraft.getInstance().font;

            // Color dot
            int dotColor = bookmark.bookmarkColor != 0 ? bookmark.bookmarkColor : config.textCoordinate;
            int dotSize = 4;
            int dotY = y + (getHeight() - dotSize) / 2;
            context.fill(x + 5, dotY, x + 5 + dotSize, dotY + dotSize, dotColor);

            // Label
            int labelX = x + 12;
            if (editing && labelField != null) {
                labelField.setX(labelX);
                labelField.setY(y + 2);
                labelField.setWidth(w / 3);
                labelField.extractRenderState(context, mouseX, mouseY, delta);
            } else {
                context.text(tr, bookmark.label, labelX, y + 7, config.textPrimary);
            }

            // Coordinates
            String coordText = bookmark.x + ", " + bookmark.y + ", " + bookmark.z;
            int coordX = x + w / 3 + 20;
            context.text(tr, coordText, coordX, y + 7, config.textSecondary);

            // Distance
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                CoordinateEntry tempCoord = new CoordinateEntry(bookmark.x, bookmark.y, bookmark.z, "");
                int dist = GatherlyManager.distanceTo(tempCoord, mc.player);
                String distText = "~" + dist + "m";
                context.text(tr, distText, x + w - 80, y + 7, config.textCoordinate);
            }

            // Buttons
            deleteBtn.setX(x + w - 16);
            deleteBtn.setY(y + 3);
            expandBtn.setX(x + w - 34);
            expandBtn.setY(y + 3);

            expandBtn.extractRenderState(context, mouseX, mouseY, delta);
            deleteBtn.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            int x = getX();
            int y = getY();

            if (click.button() == 0 && !editing) {
                int labelX = x + 12;
                int labelEndX = x + getWidth() / 3 + 16;
                if (click.x() >= labelX && click.x() <= labelEndX
                        && click.y() >= y && click.y() <= y + getHeight()) {
                    startEditing();
                    return true;
                }
            }
            return super.mouseClicked(click, doubled);
        }

        private void startEditing() {
            editing = true;
            labelField = new EditBox(Minecraft.getInstance().font,
                    0, 0, 100, 16, Component.literal("Label"));
            labelField.setValue("");
            labelField.setHint(Component.literal(bookmark.label));
            labelField.setMaxLength(64);
            entryChildren.add(labelField);
            entrySelectables.add(labelField);
            setFocused(labelField);
            labelField.setFocused(true);
        }

        private void commitEdit() {
            if (labelField != null && editing) {
                String newLabel = labelField.getValue().trim();
                if (!newLabel.isEmpty()) {
                    bookmark.label = newLabel;
                }
                manager.save();
                editing = false;
                entryChildren.remove(labelField);
                entrySelectables.remove(labelField);
                labelField = null;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (editing && input.key() == 257) {
                commitEdit();
                return true;
            }
            return super.keyPressed(input);
        }

        @Override
        public void commitPendingEdit() {
            if (editing) commitEdit();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Bookmark settings entry (HUD toggles, world marker, color, world scope)
    // ══════════════════════════════════════════════════════════════════

    private class BookmarkSettingsEntry extends TodoListEntry {
        private final Bookmark bookmark;
        private final Button hudToggleBtn;
        private final Button distToggleBtn;
        private final Button markerToggleBtn;
        private final Button colorBtn;
        private final Button thisWorldBtn;
        private final Button globalBtn;
        private final EditBox letterField;

        BookmarkSettingsEntry(Bookmark bookmark, int bookmarkIndex, boolean isOdd) {
            super(isOdd);
            this.bookmark = bookmark;

            hudToggleBtn = Button.builder(
                    Component.literal(bookmark.showInHud ? "HUD: ON" : "HUD: OFF"), btn -> {
                playClick();
                bookmark.showInHud = !bookmark.showInHud;
                btn.setMessage(Component.literal(bookmark.showInHud ? "HUD: ON" : "HUD: OFF"));
                manager.save();
            }).bounds(0, 0, 52, 14).build();

            distToggleBtn = Button.builder(
                    Component.literal(bookmark.showDistanceInHud ? "Dist: ON" : "Dist: OFF"), btn -> {
                playClick();
                bookmark.showDistanceInHud = !bookmark.showDistanceInHud;
                btn.setMessage(Component.literal(bookmark.showDistanceInHud ? "Dist: ON" : "Dist: OFF"));
                manager.save();
            }).bounds(0, 0, 52, 14).build();

            markerToggleBtn = Button.builder(
                    Component.literal(bookmark.showWorldMarker ? "Marker: ON" : "Marker: OFF"), btn -> {
                playClick();
                bookmark.showWorldMarker = !bookmark.showWorldMarker;
                btn.setMessage(Component.literal(bookmark.showWorldMarker ? "Marker: ON" : "Marker: OFF"));
                manager.save();
            }).bounds(0, 0, 64, 14).build();

            colorBtn = Button.builder(Component.literal(getColorName(bookmark.bookmarkColor)), btn -> {
                playClick();
                int idx = getColorIndex(bookmark.bookmarkColor);
                idx = (idx + 1) % PRESET_COLORS.length;
                bookmark.bookmarkColor = PRESET_COLORS[idx];
                btn.setMessage(Component.literal(COLOR_NAMES[idx]));
                manager.save();
            }).bounds(0, 0, 54, 14).build();

            thisWorldBtn = Button.builder(Component.literal("This world"), btn -> {
                playClick();
                bookmark.worldKey = GatherlyManager.getCurrentWorldKey();
                manager.save();
            }).bounds(0, 0, 64, 14).build();

            globalBtn = Button.builder(Component.literal("Global"), btn -> {
                playClick();
                bookmark.worldKey = "";
                manager.save();
            }).bounds(0, 0, 42, 14).build();

            letterField = new EditBox(Minecraft.getInstance().font,
                    0, 0, 20, 14, Component.literal("Letter"));
            letterField.setValue(bookmark.markerLetter != null ? bookmark.markerLetter : "");
            letterField.setMaxLength(1);
            letterField.setResponder(text -> {
                bookmark.markerLetter = text;
                manager.save();
            });

            entryChildren.add(hudToggleBtn);
            entryChildren.add(distToggleBtn);
            entryChildren.add(markerToggleBtn);
            entryChildren.add(colorBtn);
            entryChildren.add(thisWorldBtn);
            entryChildren.add(globalBtn);
            entryChildren.add(letterField);
            entrySelectables.add(hudToggleBtn);
            entrySelectables.add(distToggleBtn);
            entrySelectables.add(markerToggleBtn);
            entrySelectables.add(colorBtn);
            entrySelectables.add(thisWorldBtn);
            entrySelectables.add(globalBtn);
            entrySelectables.add(letterField);
        }

        private String getColorName(int color) {
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                if (PRESET_COLORS[i] == color) return COLOR_NAMES[i];
            }
            return COLOR_NAMES[0];
        }

        private int getColorIndex(int color) {
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                if (PRESET_COLORS[i] == color) return i;
            }
            return 0;
        }

        @Override
        public int getHeight() {
            return 58;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();

            renderRowBackground(context);
            Font tr = Minecraft.getInstance().font;
            int indent = 16;

            // Row 1: HUD toggle, distance toggle, marker toggle
            hudToggleBtn.setX(x + indent);
            hudToggleBtn.setY(y + 3);
            distToggleBtn.setX(x + indent + 56);
            distToggleBtn.setY(y + 3);
            markerToggleBtn.setX(x + indent + 112);
            markerToggleBtn.setY(y + 3);

            hudToggleBtn.extractRenderState(context, mouseX, mouseY, delta);
            distToggleBtn.extractRenderState(context, mouseX, mouseY, delta);
            markerToggleBtn.extractRenderState(context, mouseX, mouseY, delta);

            // Row 2: Color + marker letter
            int y2 = y + 20;
            context.text(tr, "Color:", x + indent, y2 + 3, config.textSecondary);
            colorBtn.setX(x + indent + tr.width("Color:") + 4);
            colorBtn.setY(y2 + 1);
            colorBtn.extractRenderState(context, mouseX, mouseY, delta);

            int swatchColor = bookmark.bookmarkColor != 0 ? bookmark.bookmarkColor : config.textSecondary;
            context.fill(x + indent + tr.width("Color:") - 2, y2 + 3,
                    x + indent + tr.width("Color:") + 2, y2 + 11, swatchColor);

            context.text(tr, "Letter:", x + w / 2, y2 + 3, config.textSecondary);
            letterField.setX(x + w / 2 + tr.width("Letter:") + 4);
            letterField.setY(y2 + 1);
            letterField.extractRenderState(context, mouseX, mouseY, delta);

            // Row 3: World scope
            int y3 = y + 38;
            context.text(tr, "World:", x + indent, y3 + 3, config.textSecondary);

            String worldDisplay = bookmark.worldKey.isEmpty() ? "Global" : bookmark.worldKey;
            int worldColor = bookmark.worldKey.isEmpty() ? config.textSecondary : config.textPrimary;
            int labelX = x + indent + tr.width("World:") + 4;
            context.text(tr, worldDisplay, labelX, y3 + 3, worldColor);

            String curKey = GatherlyManager.getCurrentWorldKey();
            thisWorldBtn.active = !curKey.isEmpty() && !curKey.equals(bookmark.worldKey);
            globalBtn.active = !bookmark.worldKey.isEmpty();

            globalBtn.setX(x + w - 44);
            globalBtn.setY(y3 + 1);
            globalBtn.extractRenderState(context, mouseX, mouseY, delta);

            thisWorldBtn.setX(x + w - 44 - 66);
            thisWorldBtn.setY(y3 + 1);
            thisWorldBtn.extractRenderState(context, mouseX, mouseY, delta);
        }
    }
}
