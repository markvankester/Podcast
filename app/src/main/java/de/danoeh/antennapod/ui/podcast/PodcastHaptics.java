package de.danoeh.antennapod.ui.podcast;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class PodcastHaptics {
    private PodcastHaptics() {
    }

    public static void tap(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static void tick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    public static void confirm(View view) {
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        } else {
            tap(view);
        }
    }

    public static void toggle(View view, boolean on) {
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(on ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.TOGGLE_OFF);
        } else {
            tap(view);
        }
    }

    public static void thresholdActivate(View view) {
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }

    public static void thresholdDeactivate(View view) {
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE);
        } else {
            tick(view);
        }
    }

    public static void gestureStart(View view) {
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START);
        } else {
            tick(view);
        }
    }

    public static void gestureEnd(View view) {
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END);
        } else {
            tick(view);
        }
    }
}
