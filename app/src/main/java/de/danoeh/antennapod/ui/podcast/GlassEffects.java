package de.danoeh.antennapod.ui.podcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.PowerManager;
import android.util.TypedValue;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import de.danoeh.antennapod.R;

public final class GlassEffects {
    private GlassEffects() {
    }

    static float glassSaturation(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.podcast_glass_saturation, value, true)
                && value.type == TypedValue.TYPE_FLOAT) {
            return value.getFloat();
        }
        return 1.8f;
    }

    @RequiresApi(31)
    public static RenderEffect blur(float blurPx) {
        return RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP);
    }

    @RequiresApi(31)
    static RenderEffect blurSaturation(Context context, float blurPx) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(glassSaturation(context));
        return RenderEffect.createChainEffect(
                RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(matrix)), blur(blurPx));
    }

    public static boolean isPowerSave(Context context) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        return powerManager != null && powerManager.isPowerSaveMode();
    }

    public static BroadcastReceiver watchPowerSave(Context context, Runnable onChange) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onChange.run();
            }
        };
        ContextCompat.registerReceiver(context, receiver,
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        return receiver;
    }

    public static void unwatchPowerSave(Context context, BroadcastReceiver receiver) {
        context.unregisterReceiver(receiver);
    }

    public static Paint createBorderPaint(int color, float width) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        return paint;
    }

    public static void roundRectPath(Path path, RectF rect, float topRadius, float bottomRadius) {
        path.reset();
        path.addRoundRect(rect, new float[] {topRadius, topRadius, topRadius, topRadius,
                bottomRadius, bottomRadius, bottomRadius, bottomRadius}, Path.Direction.CW);
    }

    public static void drawBorder(Canvas canvas, Paint paint, float width, float bottom, float radius) {
        float inset = paint.getStrokeWidth() / 2f;
        float cornerRadius = Math.max(0, radius - inset);
        canvas.drawRoundRect(inset, inset, width - inset, bottom, cornerRadius, cornerRadius, paint);
    }
}
