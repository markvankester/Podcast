package de.danoeh.antennapod.ui.podcast;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import androidx.annotation.RequiresApi;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;
import java.util.HashMap;
import java.util.Map;

@RequiresApi(31)
final class GlassBlurRenderer {
    private static final String TAG = "GlassBlurRenderer";
    private static final float BLUR_DP = 20f;
    private static final float SATURATION = 1.8f;
    private static final long SAME_FRAME_NANOS = 4_000_000L;
    private static final Map<View, SharedContent> SHARED = new HashMap<>();

    private final View glass;
    private final View content;
    private final SharedContent shared;
    private final RenderNode node = new RenderNode("podcast_glass");
    private final int[] glassLocation = new int[2];
    private final int[] contentLocation = new int[2];
    private final int padding;
    private boolean recorded = false;
    private boolean failed = false;

    GlassBlurRenderer(View glass, View content) {
        this.glass = glass;
        this.content = content;
        float blur = PodcastMetrics.dp(glass.getContext(), BLUR_DP);
        padding = Math.round(blur * 2);
        int backgroundColor = MaterialColors.getColor(glass, R.attr.podcast_background, Color.WHITE);
        SharedContent existing = SHARED.get(content);
        if (existing == null) {
            existing = new SharedContent(content, backgroundColor);
            SHARED.put(content, existing);
        }
        shared = existing;
        shared.users++;
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(SATURATION);
        RenderEffect saturation = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(matrix));
        RenderEffect blurEffect = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP);
        node.setRenderEffect(RenderEffect.createChainEffect(saturation, blurEffect));
    }

    void release() {
        node.discardDisplayList();
        recorded = false;
        shared.users--;
        if (shared.users <= 0) {
            shared.node.discardDisplayList();
            SHARED.remove(content);
        }
    }

    private boolean isVisibleOnScreen() {
        if (!glass.isShown()) {
            return false;
        }
        View view = glass;
        while (true) {
            if (view.getAlpha() <= 0f) {
                return false;
            }
            ViewParent parent = view.getParent();
            if (!(parent instanceof View)) {
                return true;
            }
            view = (View) parent;
        }
    }

    void record() {
        if (failed) {
            return;
        }
        int width = glass.getWidth();
        int height = glass.getHeight();
        if (width == 0 || height == 0 || !isVisibleOnScreen() || !content.isAttachedToWindow()) {
            recorded = false;
            return;
        }
        try {
            if (!shared.record()) {
                recorded = false;
                return;
            }
            glass.getLocationOnScreen(glassLocation);
            content.getLocationOnScreen(contentLocation);
            int nodeWidth = width + 2 * padding;
            int nodeHeight = height + 2 * padding;
            node.setPosition(0, 0, nodeWidth, nodeHeight);
            RecordingCanvas canvas = node.beginRecording(nodeWidth, nodeHeight);
            try {
                canvas.translate(contentLocation[0] - glassLocation[0] + padding,
                        contentLocation[1] - glassLocation[1] + padding);
                canvas.drawRenderNode(shared.node);
            } finally {
                node.endRecording();
            }
            recorded = true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Disabling glass blur", e);
            failed = true;
            recorded = false;
        }
    }

    boolean draw(Canvas canvas) {
        if (failed || !canvas.isHardwareAccelerated()) {
            return false;
        }
        if (!recorded) {
            record();
        }
        if (!recorded) {
            return false;
        }
        canvas.translate(-padding, -padding);
        canvas.drawRenderNode(node);
        return true;
    }

    private static final class SharedContent {
        final View content;
        final RenderNode node = new RenderNode("podcast_glass_content");
        final int backgroundColor;
        int users = 0;
        long lastRecordEnd = 0;
        boolean recorded = false;

        SharedContent(View content, int backgroundColor) {
            this.content = content;
            this.backgroundColor = backgroundColor;
        }

        boolean record() {
            long now = System.nanoTime();
            if (recorded && now - lastRecordEnd < SAME_FRAME_NANOS) {
                return true;
            }
            int width = content.getWidth();
            int height = content.getHeight();
            if (width == 0 || height == 0) {
                return false;
            }
            node.setPosition(0, 0, width, height);
            RecordingCanvas canvas = node.beginRecording(width, height);
            try {
                canvas.drawColor(backgroundColor);
                content.draw(canvas);
            } finally {
                node.endRecording();
            }
            recorded = true;
            lastRecordEnd = System.nanoTime();
            return true;
        }
    }
}
