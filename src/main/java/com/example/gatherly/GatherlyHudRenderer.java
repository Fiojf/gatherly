package com.example.gatherly;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

public class GatherlyHudRenderer {

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        GatherlyConfig config = GatherlyManager.getInstance().getConfig();
        if (!config.hudEnabled) return;
        if (client.screen != null) return;

        Font tr = client.font;
        Player player = client.player;

        // Manager already returns the world-filtered, pinned-first sorted view
        List<TodoItem> todos = GatherlyManager.getInstance().getTodos();
        List<Bookmark> hudBookmarks = GatherlyManager.getInstance().getBookmarksForHud();
        if (todos.isEmpty() && hudBookmarks.isEmpty()) return;

        // Count display rows (some to-dos take 2 rows if waypoint sub-line is shown)
        int rowCount = 0;
        int todoCount = 0;
        for (TodoItem todo : todos) {
            if (config.hideCompletedInHud && todo.isCompleted()) continue;
            todoCount++;
            rowCount++;
            if (config.showNearestCoordinateInHud && !todo.coordinates.isEmpty()) {
                rowCount++;
            }
            if (todoCount >= config.maxHudRows) break;
        }

        // Count bookmark rows (each bookmark = 1 row, shares maxHudRows budget)
        int bookmarkCount = 0;
        boolean hasBookmarks = !hudBookmarks.isEmpty() && todoCount < config.maxHudRows;
        if (hasBookmarks) {
            rowCount++; // separator row
            for (Bookmark bm : hudBookmarks) {
                if (todoCount + bookmarkCount >= config.maxHudRows) break;
                bookmarkCount++;
                rowCount++;
            }
        }

        if (rowCount == 0) return;

        float scale = config.hudScale / 100.0f;
        context.pose().pushMatrix();
        context.pose().scale(scale, scale);

        int x = (int) (config.hudX / scale);
        int y = (int) (config.hudY / scale);
        int w = (int) (config.hudWidth / scale);

        int pad = 6;
        int lineH = 12;
        int sepH = 1;
        int gapAfterSep = 3;
        int dotSize = 4;
        int barW = 44;
        int barH = 4;

        int headerH = pad + lineH + gapAfterSep + sepH + gapAfterSep;
        int rowsH = rowCount * lineH;
        int totalH = headerH + rowsH + pad;
        int panelH = Math.min(totalH, config.hudHeight);

        int bg = applyAlpha(config.hudBackground, config.hudOpacity);
        context.fill(x + 1, y, x + w - 1, y + panelH, bg);
        context.fill(x, y + 1, x + w, y + panelH - 1, bg);

        if (config.hudBorderEnabled) {
            int bc = config.hudBorder;
            context.fill(x + 2, y, x + w - 2, y + 1, bc);
            context.fill(x + 2, y + panelH - 1, x + w - 2, y + panelH, bc);
            context.fill(x, y + 2, x + 1, y + panelH - 2, bc);
            context.fill(x + w - 1, y + 2, x + w, y + panelH - 2, bc);
            context.fill(x + 1, y + 1, x + 2, y + 2, bc);
            context.fill(x + w - 2, y + 1, x + w - 1, y + 2, bc);
            context.fill(x + 1, y + panelH - 2, x + 2, y + panelH - 1, bc);
            context.fill(x + w - 2, y + panelH - 2, x + w - 1, y + panelH - 1, bc);
        }

        int cx = x + pad;
        int cy = y + pad;
        int cw = w - pad * 2;
        int maxY = y + panelH - pad;

        int sepColor = (config.hudBorderEnabled && (config.hudBorder & 0xFF000000) != 0)
                ? applyAlpha(config.hudBorder, 80)
                : applyAlpha(config.textSecondary, 50);

        context.text(tr, "\u00A7lGatherly\u00A7r", cx, cy, config.panelTitleColor);
        cy += lineH + gapAfterSep;
        context.fill(cx, cy, cx + cw, cy + sepH, sepColor);
        cy += sepH + gapAfterSep;

        int displayed = 0;
        for (TodoItem todo : todos) {
            if (displayed >= config.maxHudRows) break;
            if (config.hideCompletedInHud && todo.isCompleted()) continue;
            if (cy + lineH > maxY) break;

            boolean completed = todo.isCompleted();
            boolean hasTargets = !todo.targets.isEmpty();
            boolean hasCoords = !todo.coordinates.isEmpty();

            // Status dot — use todoColor if set, otherwise auto
            int dotColor;
            if (todo.todoColor != 0) {
                dotColor = todo.todoColor;
            } else if (completed) {
                dotColor = config.progressBarComplete;
            } else if (hasTargets) {
                dotColor = config.progressBarFill;
            } else {
                dotColor = config.textSecondary;
            }

            int dotY = cy + (lineH - dotSize) / 2;
            context.fill(cx + 1, dotY, cx + dotSize - 1, dotY + dotSize, dotColor);
            context.fill(cx, dotY + 1, cx + dotSize, dotY + dotSize - 1, dotColor);

            // Right-to-left layout
            int rightEdge = cx + cw;
            int rightCursor = rightEdge;

            // Progress bar + count
            if (hasTargets) {
                float totalProgress = 0;
                int totalCurrent = 0, totalRequired = 0;
                for (TargetEntry t : todo.targets) {
                    totalProgress += t.progress();
                    totalCurrent += t.currentCount;
                    totalRequired += t.requiredCount;
                }
                totalProgress /= todo.targets.size();

                int barX = rightCursor - barW;
                int barY2 = cy + (lineH - barH) / 2;
                context.fill(barX, barY2, barX + barW, barY2 + barH, config.progressBarTrack);
                int fillW = (int) (barW * Math.min(1.0f, totalProgress));
                if (fillW > 0) {
                    int fc = completed ? config.progressBarComplete : config.progressBarFill;
                    context.fill(barX, barY2, barX + fillW, barY2 + barH, fc);
                }
                rightCursor = barX - 3;

                String countStr = totalCurrent + "/" + totalRequired;
                int countW = tr.width(countStr);
                int countX = rightCursor - countW;
                if (countX > cx + dotSize + 4) {
                    int countColor = completed
                            ? applyAlpha(config.textProgress, config.completedItemAlpha)
                            : config.textProgress;
                    context.text(tr, countStr, countX, cy + 1, countColor);
                    rightCursor = countX - 4;
                }
            }

            // Title
            int titleX = cx + dotSize + 4;
            int maxTitleW = rightCursor - titleX;
            if (maxTitleW > 0) {
                int titleColor = completed
                        ? applyAlpha(config.textCompleted, config.completedItemAlpha)
                        : config.textPrimary;
                String titleText = clipText(tr, todo.title, maxTitleW);
                if (completed) titleText = "\u00A7m" + titleText + "\u00A7r";
                context.text(tr, titleText, titleX, cy + 1, titleColor);
            }

            cy += lineH;
            displayed++;

            // Waypoint sub-line (label + coordinates + distance)
            if (config.showNearestCoordinateInHud && hasCoords && cy + lineH <= maxY) {
                CoordinateEntry nearest = findNearestCoord(todo.coordinates, player);
                if (nearest != null) {
                    // Diamond pip (indented)
                    int pipX = cx + dotSize + 4;
                    int pipCy = cy + lineH / 2;
                    context.fill(pipX, pipCy - 1, pipX + 2, pipCy + 1, config.textCoordinate);
                    context.fill(pipX - 1, pipCy, pipX + 3, pipCy, config.textCoordinate);

                    int wpTextX = pipX + 5;
                    int maxWpW = cx + cw - wpTextX;

                    String wpLabel = (nearest.label != null && !nearest.label.isEmpty())
                            ? nearest.label : "Waypoint";
                    int dist = GatherlyManager.distanceTo(nearest, player);
                    String wpInfo = wpLabel + "  " + nearest.x + ", " + nearest.y + ", " + nearest.z
                            + "  ~" + dist + "m";

                    if (maxWpW > 0) {
                        context.text(tr, clipText(tr, wpInfo, maxWpW),
                                wpTextX, cy + 1, config.textCoordinate);
                    }
                    cy += lineH;
                }
            }
        }

        // ── Bookmark rows ────────────────────────────────────────────
        if (bookmarkCount > 0 && cy + lineH <= maxY) {
            // Thin separator
            context.fill(cx, cy + lineH / 2, cx + cw, cy + lineH / 2 + sepH, sepColor);
            cy += lineH;

            int bmDisplayed = 0;
            for (Bookmark bm : hudBookmarks) {
                if (displayed + bmDisplayed >= config.maxHudRows) break;
                if (cy + lineH > maxY) break;

                int bmColor = bm.bookmarkColor != 0 ? bm.bookmarkColor : config.textCoordinate;

                // Diamond pip
                int pipX = cx + 1;
                int pipCy = cy + lineH / 2;
                context.fill(pipX + 1, pipCy - 1, pipX + 3, pipCy + 1, bmColor);
                context.fill(pipX, pipCy, pipX + 4, pipCy, bmColor);

                // Label + coordinates + distance
                int textX = cx + dotSize + 4;
                int maxBmW = cx + cw - textX;

                StringBuilder bmInfo = new StringBuilder();
                bmInfo.append(bm.label);
                if (bm.showDistanceInHud) {
                    bmInfo.append("  ").append(bm.x).append(", ").append(bm.y).append(", ").append(bm.z);
                    CoordinateEntry tempCoord = new CoordinateEntry(bm.x, bm.y, bm.z, "");
                    int dist = GatherlyManager.distanceTo(tempCoord, player);
                    bmInfo.append("  ~").append(dist).append("m");
                }

                if (maxBmW > 0) {
                    context.text(tr, clipText(tr, bmInfo.toString(), maxBmW),
                            textX, cy + 1, bmColor);
                }

                cy += lineH;
                bmDisplayed++;
            }
        }

        context.pose().popMatrix();
    }

    private static CoordinateEntry findNearestCoord(List<CoordinateEntry> coords, Player player) {
        CoordinateEntry nearest = null;
        double minDist = Double.MAX_VALUE;
        for (CoordinateEntry coord : coords) {
            double dx = player.getX() - coord.x;
            double dy = player.getY() - coord.y;
            double dz = player.getZ() - coord.z;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < minDist) {
                minDist = dist;
                nearest = coord;
            }
        }
        return nearest;
    }

    private static String clipText(Font tr, String text, int maxWidth) {
        if (tr.width(text) <= maxWidth) return text;
        int ellipsisW = tr.width("...");
        if (maxWidth <= ellipsisW) return tr.plainSubstrByWidth(text, maxWidth);
        return tr.plainSubstrByWidth(text, maxWidth - ellipsisW) + "...";
    }

    private static int applyAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
