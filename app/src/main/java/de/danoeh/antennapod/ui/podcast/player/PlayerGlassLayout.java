package de.danoeh.antennapod.ui.podcast.player;

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
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.ColorInt;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.podcast.PodcastMetrics;

public class PlayerGlassLayout extends FrameLayout {
    private static final int BLUR_DARKEN = Color.rgb(104, 104, 104);

    private final ImageView blurView;
    private final View tintView;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private final float blurDp;
    private final float tintAlpha;
    private int baseColor = PlayerPalette.FALLBACK_COLOR;

    public PlayerGlassLayout(Context context) {
        this(context, null);
    }

    public PlayerGlassLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerGlassLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        float r = 34;
        float blur = 36;
        float alpha = 0.42f;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PlayerGlassLayout);
            r = a.getFloat(R.styleable.PlayerGlassLayout_glassRadiusDp, r);
            blur = a.getFloat(R.styleable.PlayerGlassLayout_glassBlurDp, blur);
            alpha = a.getFloat(R.styleable.PlayerGlassLayout_glassTintAlpha, alpha);
            a.recycle();
        }
        radius = PodcastMetrics.dp(context, r);
        blurDp = blur;
        tintAlpha = alpha;

        blurView = new ImageView(context);
        blurView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        blurView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        blurView.setColorFilter(new PorterDuffColorFilter(BLUR_DARKEN, PorterDuff.Mode.MULTIPLY));
        addView(blurView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tintView = new View(context);
        addView(tintView, 1, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(PodcastMetrics.dp(context, 1));
        borderPaint.setColor(Color.argb(77, 255, 255, 255));

        if (Build.VERSION.SDK_INT >= 31) {
            float px = PodcastMetrics.dp(context, blurDp);
            blurView.setRenderEffect(RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP));
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
        int alpha = Build.VERSION.SDK_INT >= 31 ? Math.round(tintAlpha * 255) : Math.round(
                Math.max(tintAlpha, 0.9f) * 255);
        tintView.setBackgroundColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT < 31) {
            blurView.setVisibility(GONE);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        clipPath.reset();
        float[] radii = {radius, radius, radius, radius, 0, 0, 0, 0};
        clipPath.addRoundRect(rect, radii, Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float inset = borderPaint.getStrokeWidth() / 2f;
        final int save = canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawColor(Color.rgb(Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)));
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), radius);
        canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() + radius,
                radius - inset, radius - inset, borderPaint);
        canvas.restore();
    }
}
