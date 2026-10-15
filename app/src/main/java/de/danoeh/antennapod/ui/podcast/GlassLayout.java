package de.danoeh.antennapod.ui.podcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.PixelCopy;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import androidx.annotation.RequiresApi;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;

public class GlassLayout extends FrameLayout {
    private static final int SNAPSHOT_SCALE = 4;
    private final Paint snapshotPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path shapePath = new Path();
    private final Path highlightPath = new Path();
    private final RectF rect = new RectF();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint;
    private int tintColor;
    private int fallbackColor;
    private int highlightColor;
    private float configuredRadius;
    private final float borderWidth;
    private float radius;
    private boolean topCornersOnly = false;
    private Window snapshotWindow;
    private Bitmap snapshot;
    private RenderNode snapshotNode;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int snapshotPadding;
    private final float snapshotBlur;
    private boolean snapshotDirty = true;
    private int recordedWidth = -1;
    private int recordedHeight = -1;
    private int recordedOffsetX;
    private int recordedOffsetY;
    private boolean powerSave = false;
    private BroadcastReceiver powerSaveReceiver;
    private final int[] snapshotLocation = new int[2];
    private final int[] windowLocation = new int[2];
    private final ViewTreeObserver.OnPreDrawListener snapshotPreDrawListener = () -> {
        int x = snapshotLocation[0];
        int y = snapshotLocation[1];
        getLocationOnScreen(snapshotLocation);
        if (x != snapshotLocation[0] || y != snapshotLocation[1]) {
            invalidate();
        }
        return true;
    };
    private GlassBlurRenderer blurRenderer;
    private boolean preDrawRegistered = false;

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        if (Build.VERSION.SDK_INT >= 31 && blurRenderer != null && !powerSave) {
            blurRenderer.record();
        }
        return true;
    };

    public GlassLayout(Context context) {
        this(context, null);
    }

    public GlassLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        tintColor = MaterialColors.getColor(context, R.attr.podcast_glass_surface, Color.argb(184, 255, 255, 255));
        fallbackColor = MaterialColors.getColor(context, R.attr.podcast_glass_surface_fallback,
                Color.argb(235, 255, 255, 255));
        highlightColor = MaterialColors.getColor(context, R.attr.podcast_glass_highlight,
                Color.argb(204, 255, 255, 255));
        borderWidth = PodcastMetrics.dp(context, 1);
        borderPaint = GlassEffects.createBorderPaint(MaterialColors.getColor(context, R.attr.podcast_glass_border,
                Color.argb(15, 0, 0, 0)), borderWidth);
        snapshotPadding = Math.round(PodcastMetrics.dp(context, 40));
        snapshotBlur = PodcastMetrics.dp(context, 20);

        float configured = -1;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GlassLayout, defStyleAttr, 0);
            configured = a.getDimension(R.styleable.GlassLayout_glassCornerRadius, -1);
            a.recycle();
        }
        configuredRadius = configured;

        setElevation(PodcastMetrics.dp(context, 6));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int bottom = topCornersOnly ? view.getHeight() + Math.round(radius) : view.getHeight();
                outline.setRoundRect(0, 0, view.getWidth(), bottom, radius);
                outline.setAlpha(1f);
            }
        });
        setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= 28) {
            setOutlineSpotShadowColor(Color.argb(60, 0, 0, 0));
            setOutlineAmbientShadowColor(Color.argb(20, 0, 0, 0));
        }
    }

    public void setBlurSnapshot(Window window) {
        snapshotWindow = window;
    }

    public void setCornerRadius(float cornerRadius, boolean topOnly) {
        configuredRadius = cornerRadius;
        topCornersOnly = topOnly;
        updateShape(getWidth(), getHeight());
    }

    public void setGlassColors(int tint, int fallback, int border, int highlight) {
        tintColor = tint;
        fallbackColor = fallback;
        borderPaint.setColor(border);
        highlightColor = highlight;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShape(w, h);
    }

    private void updateShape(int w, int h) {
        float maxRadius = Math.min(w, h) / 2f;
        radius = configuredRadius >= 0 ? Math.min(configuredRadius, maxRadius) : maxRadius;
        float bottom = topCornersOnly ? h + radius : h;
        rect.set(0, 0, w, bottom);
        GlassEffects.roundRectPath(shapePath, rect, radius, radius);
        GlassEffects.roundRectPath(highlightPath, rect, radius, radius);
        highlightPath.offset(0, borderWidth);
        invalidateOutline();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        powerSave = GlassEffects.isPowerSave(getContext());
        powerSaveReceiver = GlassEffects.watchPowerSave(getContext(), this::onPowerSaveChanged);
        if (snapshotWindow != null) {
            if (Build.VERSION.SDK_INT >= 31) {
                if (!powerSave) {
                    requestSnapshot();
                }
                getViewTreeObserver().addOnPreDrawListener(snapshotPreDrawListener);
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 && blurRenderer == null) {
            View content = getRootView().findViewById(R.id.main_content_view);
            if (content != null) {
                blurRenderer = new GlassBlurRenderer(this, content);
            }
        }
        if (blurRenderer != null && !preDrawRegistered) {
            getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            preDrawRegistered = true;
        }
    }

    private void onPowerSaveChanged() {
        boolean enabled = GlassEffects.isPowerSave(getContext());
        if (enabled == powerSave) {
            return;
        }
        powerSave = enabled;
        if (!enabled && snapshotWindow != null && snapshot == null && Build.VERSION.SDK_INT >= 31) {
            requestSnapshot();
        }
        invalidate();
    }

    @RequiresApi(31)
    private void requestSnapshot() {
        View decor = snapshotWindow.getDecorView();
        int width = decor.getWidth() / SNAPSHOT_SCALE;
        int height = decor.getHeight() / SNAPSHOT_SCALE;
        if (width <= 0 || height <= 0) {
            return;
        }
        decor.getLocationOnScreen(windowLocation);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        PixelCopy.request(snapshotWindow, bitmap, result -> {
            if (result == PixelCopy.SUCCESS && isAttachedToWindow()) {
                snapshot = bitmap;
                snapshotDirty = true;
                invalidate();
            } else {
                bitmap.recycle();
            }
        }, mainHandler);
    }

    @RequiresApi(31)
    private boolean drawSnapshot(Canvas canvas) {
        if (!canvas.isHardwareAccelerated()) {
            return false;
        }
        int padding = snapshotPadding;
        if (snapshotNode == null) {
            snapshotNode = new RenderNode("podcast_glass_snapshot");
            snapshotNode.setRenderEffect(GlassEffects.blurSaturation(getContext(), snapshotBlur));
        }
        getLocationOnScreen(snapshotLocation);
        int width = getWidth() + 2 * padding;
        int height = getHeight() + 2 * padding;
        int offsetX = windowLocation[0] - snapshotLocation[0] + padding;
        int offsetY = windowLocation[1] - snapshotLocation[1] + padding;
        if (snapshotDirty || width != recordedWidth || height != recordedHeight
                || offsetX != recordedOffsetX || offsetY != recordedOffsetY) {
            snapshotNode.setPosition(0, 0, width, height);
            RecordingCanvas recording = snapshotNode.beginRecording(width, height);
            try {
                recording.translate(offsetX, offsetY);
                recording.scale(SNAPSHOT_SCALE, SNAPSHOT_SCALE);
                recording.drawBitmap(snapshot, 0, 0, snapshotPaint);
            } finally {
                snapshotNode.endRecording();
            }
            snapshotDirty = false;
            recordedWidth = width;
            recordedHeight = height;
            recordedOffsetX = offsetX;
            recordedOffsetY = offsetY;
        }
        canvas.translate(-padding, -padding);
        canvas.drawRenderNode(snapshotNode);
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(snapshotPreDrawListener);
        if (snapshotNode != null && Build.VERSION.SDK_INT >= 31) {
            snapshotNode.discardDisplayList();
        }
        snapshot = null;
        snapshotDirty = true;
        if (powerSaveReceiver != null) {
            GlassEffects.unwatchPowerSave(getContext(), powerSaveReceiver);
            powerSaveReceiver = null;
        }
        if (preDrawRegistered) {
            getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            preDrawRegistered = false;
        }
        if (Build.VERSION.SDK_INT >= 31 && blurRenderer != null) {
            blurRenderer.release();
        }
        blurRenderer = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }
        boolean blurred = false;
        if (!powerSave && snapshot != null && Build.VERSION.SDK_INT >= 31) {
            canvas.save();
            canvas.clipPath(shapePath);
            blurred = drawSnapshot(canvas);
            canvas.restore();
        } else if (Build.VERSION.SDK_INT >= 31 && !powerSave && blurRenderer != null) {
            canvas.save();
            canvas.clipPath(shapePath);
            blurred = blurRenderer.draw(canvas);
            canvas.restore();
        }
        fillPaint.setColor(blurred ? tintColor : fallbackColor);
        canvas.drawPath(shapePath, fillPaint);

        float borderBottom = topCornersOnly ? getHeight() + radius : getHeight() - borderWidth / 2f;
        GlassEffects.drawBorder(canvas, borderPaint, getWidth(), borderBottom, radius);

        if (Build.VERSION.SDK_INT >= 26) {
            canvas.save();
            canvas.clipPath(shapePath);
            canvas.clipOutPath(highlightPath);
            canvas.drawColor(highlightColor);
            canvas.restore();
        }
    }
}
