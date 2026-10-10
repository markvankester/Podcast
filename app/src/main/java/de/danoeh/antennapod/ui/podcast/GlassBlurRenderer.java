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
import androidx.annotation.RequiresApi;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;

@RequiresApi(31)
final class GlassBlurRenderer {
    private static final String TAG = "GlassBlurRenderer";
    private static final float BLUR_DP = 20f;
    private static final float SATURATION = 1.8f;

    private final View glass;
    private final View content;
    private final RenderNode node = new RenderNode("podcast_glass");
    private final int[] glassLocation = new int[2];
    private final int[] contentLocation = new int[2];
    private final int padding;
    private final int backgroundColor;
    private boolean recorded = false;
    private boolean failed = false;

    GlassBlurRenderer(View glass, View content) {
        this.glass = glass;
        this.content = content;
        float blur = PodcastMetrics.dp(glass.getContext(), BLUR_DP);
        padding = Math.round(blur * 2);
        backgroundColor = MaterialColors.getColor(glass, R.attr.podcast_background, Color.WHITE);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(SATURATION);
        RenderEffect saturation = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(matrix));
        RenderEffect blurEffect = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP);
        node.setRenderEffect(RenderEffect.createChainEffect(saturation, blurEffect));
    }

    void record() {
        if (failed) {
            return;
        }
        int width = glass.getWidth();
        int height = glass.getHeight();
        if (width == 0 || height == 0 || !glass.isShown() || !content.isAttachedToWindow()) {
            recorded = false;
            return;
        }
        try {
            glass.getLocationInWindow(glassLocation);
            content.getLocationInWindow(contentLocation);
            int nodeWidth = width + 2 * padding;
            int nodeHeight = height + 2 * padding;
            node.setPosition(0, 0, nodeWidth, nodeHeight);
            RecordingCanvas canvas = node.beginRecording(nodeWidth, nodeHeight);
            try {
                canvas.drawColor(backgroundColor);
                canvas.translate(contentLocation[0] - glassLocation[0] + padding,
                        contentLocation[1] - glassLocation[1] + padding);
                canvas.clipRect(0, 0, content.getWidth(), content.getHeight());
                content.draw(canvas);
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
}
