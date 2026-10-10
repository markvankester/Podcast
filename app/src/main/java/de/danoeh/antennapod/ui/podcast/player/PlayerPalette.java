package de.danoeh.antennapod.ui.podcast.player;

import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

public final class PlayerPalette {
    public static final int FALLBACK_COLOR = Color.parseColor("#2B2B2B");
    private static final double MIN_CONTRAST = 4.5;
    private static final float MAX_LIGHTNESS = 0.24f;
    private static final float MAX_SATURATION = 0.65f;

    public interface Listener {
        void onColor(@ColorInt int color);
    }

    private PlayerPalette() {
    }

    public static void extract(@NonNull Bitmap bitmap, @NonNull Listener listener) {
        Palette.from(bitmap).maximumColorCount(16).generate(palette -> {
            if (palette == null) {
                listener.onColor(FALLBACK_COLOR);
                return;
            }
            int base = palette.getDarkVibrantColor(0);
            if (base == 0) {
                base = palette.getDominantColor(0);
            }
            if (base == 0) {
                base = palette.getDarkMutedColor(FALLBACK_COLOR);
            }
            listener.onColor(toDarkBackground(base));
        });
    }

    @ColorInt
    public static int toDarkBackground(@ColorInt int color) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[1] = Math.min(hsl[1], MAX_SATURATION);
        hsl[2] = Math.min(hsl[2], MAX_LIGHTNESS);
        int result = ColorUtils.HSLToColor(hsl);
        while (hsl[2] > 0.02f && ColorUtils.calculateContrast(Color.WHITE, result) < MIN_CONTRAST) {
            hsl[2] -= 0.02f;
            result = ColorUtils.HSLToColor(hsl);
        }
        return result;
    }
}
