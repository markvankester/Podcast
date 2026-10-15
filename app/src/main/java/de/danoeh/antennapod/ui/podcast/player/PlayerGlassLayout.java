package de.danoeh.antennapod.ui.podcast.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.ColorInt;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.podcast.GlassEffects;
import de.danoeh.antennapod.ui.podcast.PodcastMetrics;

public class PlayerGlassLayout extends FrameLayout {
    private static final int BLUR_DARKEN = Color.rgb(104, 104, 104);

    private final ImageView blurView;
    private final View tintView;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final Paint borderPaint;
    private final float radius;
    private final float blurDp;
    private final float tintAlpha;
    private int baseColor = PlayerPalette.FALLBACK_COLOR;
    private boolean powerSave = false;
    private BroadcastReceiver powerSaveReceiver;

    public PlayerGlassLayout(Context context) {
        this(context, null);
    }

    public PlayerGlassLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerGlassLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        float r = PodcastMetrics.dp(context, 34);
        float blur = 36;
        float alpha = 0.42f;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PlayerGlassLayout);
            r = a.getDimension(R.styleable.PlayerGlassLayout_glassRadius, r);
            blur = a.getFloat(R.styleable.PlayerGlassLayout_glassBlurDp, blur);
            alpha = a.getFloat(R.styleable.PlayerGlassLayout_glassTintAlpha, alpha);
            a.recycle();
        }
        radius = r;
        blurDp = blur;
        tintAlpha = alpha;

        blurView = new ImageView(context);
        blurView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        blurView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        blurView.setColorFilter(new PorterDuffColorFilter(BLUR_DARKEN, PorterDuff.Mode.MULTIPLY));
        addView(blurView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tintView = new View(context);
        addView(tintView, 1, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        borderPaint = GlassEffects.createBorderPaint(Color.argb(77, 255, 255, 255), PodcastMetrics.dp(context, 1));

        if (Build.VERSION.SDK_INT >= 31) {
            float px = PodcastMetrics.dp(context, blurDp);
            blurView.setRenderEffect(GlassEffects.blur(px));
        } else {
            blurView.setVisibility(GONE);
        }
        applyColor(baseColor);
    }

    public void setBlurBitmap(Bitmap bitmap) {
        blurView.setImageBitmap(bitmap);
    }

    public void setBaseColor(@ColorInt int color) {
        baseColor = color;
        applyColor(color);
    }

    private void applyColor(@ColorInt int color) {
        boolean blurEnabled = Build.VERSION.SDK_INT >= 31 && !powerSave;
        int alpha = blurEnabled ? Math.round(tintAlpha * 255) : Math.round(
                Math.max(tintAlpha, 0.9f) * 255);
        tintView.setBackgroundColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        setBackgroundColor(Color.TRANSPARENT);
        blurView.setVisibility(blurEnabled ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        powerSave = GlassEffects.isPowerSave(getContext());
        applyColor(baseColor);
        powerSaveReceiver = GlassEffects.watchPowerSave(getContext(), () -> {
            powerSave = GlassEffects.isPowerSave(getContext());
            applyColor(baseColor);
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        if (powerSaveReceiver != null) {
            GlassEffects.unwatchPowerSave(getContext(), powerSaveReceiver);
            powerSaveReceiver = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        GlassEffects.roundRectPath(clipPath, rect, radius, 0);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final int save = canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawColor(Color.rgb(Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)));
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), radius);
        GlassEffects.drawBorder(canvas, borderPaint, getWidth(), getHeight() + radius, radius);
        canvas.restore();
    }
}
