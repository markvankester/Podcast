package de.danoeh.antennapod.ui.podcast.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.podcast.PodcastMetrics;

public class PlayerSeekBar extends AppCompatSeekBar {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bufferPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segment = new RectF();
    private final float trackHeight;
    private final float gap;
    private final float thumbRadius;
    private float[] dividerPos;

    public PlayerSeekBar(Context context) {
        this(context, null);
    }

    public PlayerSeekBar(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.seekBarStyle);
    }

    public PlayerSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackground(null);
        setThumb(null);
        setProgressDrawable(null);
        trackHeight = PodcastMetrics.dp(context, 6);
        gap = PodcastMetrics.dp(context, 3);
        thumbRadius = PodcastMetrics.dp(context, 8);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.podcast_white_a3D));
        bufferPaint.setColor(ContextCompat.getColor(context, R.color.podcast_white_a2E));
        fillPaint.setColor(Color.WHITE);
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setShadowLayer(PodcastMetrics.dp(context, 4), 0, PodcastMetrics.dp(context, 2),
                ContextCompat.getColor(context, R.color.podcast_black_a4D));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setDividerPos(float[] positions) {
        if (positions == null || positions.length == 0) {
            dividerPos = null;
        } else {
            dividerPos = new float[positions.length + 1];
            int count = 0;
            for (float position : positions) {
                if (position > 0.001f && position < 0.999f) {
                    dividerPos[count++] = position;
                }
            }
            float[] trimmed = new float[count + 2];
            System.arraycopy(dividerPos, 0, trimmed, 1, count);
            trimmed[count + 1] = 1f;
            dividerPos = trimmed;
        }
        invalidate();
    }

    public void highlightCurrentChapter() {
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        float left = getPaddingLeft();
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float centerY = getHeight() / 2f;
        float top = centerY - trackHeight / 2f;
        float bottom = centerY + trackHeight / 2f;
        float max = Math.max(1, getMax());
        float primary = left + getProgress() / max * width;
        float secondary = left + getSecondaryProgress() / max * width;
        float r = trackHeight / 2f;
        int count = dividerPos == null ? 1 : dividerPos.length - 1;
        for (int i = 0; i < count; i++) {
            float start = dividerPos == null ? 0f : dividerPos[i];
            float end = dividerPos == null ? 1f : dividerPos[i + 1];
            float segLeft = left + start * width + (i > 0 ? gap / 2f : 0);
            float segRight = left + end * width - (i < count - 1 ? gap / 2f : 0);
            segment.set(segLeft, top, segRight, bottom);
            canvas.drawRoundRect(segment, r, r, trackPaint);
            if (secondary > segLeft) {
                canvas.save();
                canvas.clipRect(segLeft, top, Math.min(secondary, segRight), bottom);
                canvas.drawRoundRect(segment, r, r, bufferPaint);
                canvas.restore();
            }
            if (primary > segLeft) {
                canvas.save();
                canvas.clipRect(segLeft, top, Math.min(primary, segRight), bottom);
                canvas.drawRoundRect(segment, r, r, fillPaint);
                canvas.restore();
            }
        }
        canvas.drawCircle(primary, centerY, thumbRadius, thumbPaint);
    }
}
