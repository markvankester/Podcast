package de.danoeh.antennapod.ui.podcast;

import android.content.Context;

public final class PodcastMetrics {
    public static final int SIDE_MARGIN_DP = 12;
    public static final int DOCK_BOTTOM_MARGIN_DP = 20;
    public static final int TAB_ROW_HEIGHT_DP = 66;
    public static final int PILL_HEIGHT_DP = 56;
    public static final int ROW_GAP_DP = 8;
    public static final int CONTENT_GAP_DP = 12;

    private PodcastMetrics() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
