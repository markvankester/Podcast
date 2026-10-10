package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;

public class GlassLayout extends FrameLayout {
    private final Path shapePath = new Path();
    private final Path highlightPath = new Path();
    private final RectF rect = new RectF();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int tintColor;
    private final int fallbackColor;
    private final int highlightColor;
    private final float configuredRadius;
    private final float borderWidth;
    private float radius;
    private GlassBlurRenderer blurRenderer;
    private boolean preDrawRegistered = false;

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        if (blurRenderer != null) {
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
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(MaterialColors.getColor(context, R.attr.podcast_glass_border,
                Color.argb(15, 0, 0, 0)));
        borderWidth = PodcastMetrics.dp(context, 1);
        borderPaint.setStrokeWidth(borderWidth);

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
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= 28) {
            setOutlineSpotShadowColor(Color.argb(60, 0, 0, 0));
            setOutlineAmbientShadowColor(Color.argb(20, 0, 0, 0));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float maxRadius = Math.min(w, h) / 2f;
        radius = configuredRadius >= 0 ? Math.min(configuredRadius, maxRadius) : maxRadius;
        rect.set(0, 0, w, h);
        shapePath.reset();
        shapePath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        highlightPath.reset();
        highlightPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        highlightPath.offset(0, borderWidth);
        invalidateOutline();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
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

    @Override
    protected void onDetachedFromWindow() {
        if (preDrawRegistered) {
            getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            preDrawRegistered = false;
        }
        if (blurRenderer != null) {
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
        if (blurRenderer != null) {
            canvas.save();
            canvas.clipPath(shapePath);
            blurred = blurRenderer.draw(canvas);
            canvas.restore();
        }
        fillPaint.setColor(blurred ? tintColor : fallbackColor);
        canvas.drawPath(shapePath, fillPaint);

        float inset = borderWidth / 2f;
        canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                Math.max(0, radius - inset), Math.max(0, radius - inset), borderPaint);

        if (Build.VERSION.SDK_INT >= 26) {
            canvas.save();
            canvas.clipPath(shapePath);
            canvas.clipOutPath(highlightPath);
            canvas.drawColor(highlightColor);
            canvas.restore();
        }
    }
}
