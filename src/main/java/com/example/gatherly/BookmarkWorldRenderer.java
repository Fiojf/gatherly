package com.example.gatherly;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders 3D billboard markers in the world for bookmarks with showWorldMarker=true.
 * Style inspired by Xaero's Minimap waypoints: colored circle with letter, label above, distance below.
 *
 * Follows the same rendering pattern as vanilla NameTagFeatureRenderer:
 * translate to position, mulPose(orientation), scale, drawInBatch.
 */
public class BookmarkWorldRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gatherly");
    private static boolean loggedOnce = false;

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(BookmarkWorldRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<Bookmark> bookmarks = GatherlyManager.getInstance().getBookmarksForWorldRender();
        if (bookmarks.isEmpty()) return;

        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;

        // Use MC's own render buffer source (same one used by vanilla name tags)
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        CameraRenderState cameraState = context.levelState().cameraRenderState;
        Vec3 camPos = cameraState.pos;
        Font font = mc.font;

        if (!loggedOnce) {
            LOGGER.info("Gatherly: World marker rendering active ({} bookmarks, cam at {}, {}, {})",
                    bookmarks.size(), (int) camPos.x, (int) camPos.y, (int) camPos.z);
            loggedOnce = true;
        }

        for (Bookmark bm : bookmarks) {
            renderMarker(poseStack, bufferSource, cameraState, font, bm, camPos);
        }

        // Flush our text so it actually gets drawn
        bufferSource.endBatch();
    }

    private static void renderMarker(PoseStack poseStack, MultiBufferSource bufferSource,
                                     CameraRenderState cameraState, Font font,
                                     Bookmark bm, Vec3 camPos) {
        // Camera-relative position
        double dx = bm.x + 0.5 - camPos.x;
        double dy = bm.y + 1.5 - camPos.y;
        double dz = bm.z + 0.5 - camPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Don't render if extremely far away
        if (distance > 500) return;

        int color = bm.bookmarkColor != 0 ? bm.bookmarkColor : 0xFF55FFFF;

        // Alpha fade at great distances
        int alpha = distance > 200 ? 0x50 : (distance > 100 ? 0x90 : 0xFF);
        int fadedColor = (color & 0x00FFFFFF) | (alpha << 24);
        int bgAlpha = distance > 200 ? 0x20 : (distance > 100 ? 0x40 : 0x60);

        // Scale based on distance (same base as vanilla name tags: 0.025)
        float baseScale = 0.025f;
        float distScale = (float) Math.max(1.0, distance / 10.0);
        float finalScale = baseScale * distScale;
        finalScale = Math.min(finalScale, 0.5f);

        // Follow vanilla NameTagFeatureRenderer transform order:
        // translate → mulPose(orientation) → scale(s, -s, s)
        poseStack.pushPose();
        poseStack.translate((float) dx, (float) dy, (float) dz);
        poseStack.mulPose(cameraState.orientation);
        poseStack.scale(finalScale, -finalScale, finalScale);

        // Copy the pose matrix (vanilla does this too)
        Matrix4f pose = new Matrix4f(poseStack.last().pose());

        // ── Label above ──────────────────────────────────────────────
        String label = bm.label;
        float labelW = font.width(label);
        font.drawInBatch(label, -labelW / 2, -18, fadedColor, false,
                pose, bufferSource, Font.DisplayMode.SEE_THROUGH,
                (bgAlpha << 24), 0xF000F0);

        // ── Letter in center (with colored background) ───────────────
        String letter = bm.getDisplayLetter();
        float letterW = font.width(letter);
        font.drawInBatch(letter, -letterW / 2, -5, 0xFFFFFFFF | (alpha << 24), false,
                pose, bufferSource, Font.DisplayMode.SEE_THROUGH,
                fadedColor, 0xF000F0);

        // ── Distance below ───────────────────────────────────────────
        int dist = (int) Math.round(distance);
        String distStr = dist + "m";
        float distW = font.width(distStr);
        font.drawInBatch(distStr, -distW / 2, 8, fadedColor, false,
                pose, bufferSource, Font.DisplayMode.SEE_THROUGH,
                (bgAlpha << 24), 0xF000F0);

        poseStack.popPose();
    }
}
