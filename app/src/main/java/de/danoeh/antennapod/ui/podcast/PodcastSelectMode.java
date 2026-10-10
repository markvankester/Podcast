package de.danoeh.antennapod.ui.podcast;

import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.FragmentActivity;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.view.FloatingSelectMenu;

public final class PodcastSelectMode {
    private PodcastSelectMode() {
    }

    public static void start(FragmentActivity activity, FloatingSelectMenu menu, View scrollView) {
        int clearance = clearance(activity);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) menu.getLayoutParams();
        params.bottomMargin = clearance;
        menu.setLayoutParams(params);
        menu.setVisibility(View.VISIBLE);
        if (!(scrollView.getTag(R.id.podcast_original_bottom_padding) instanceof Integer)) {
            scrollView.setTag(R.id.podcast_original_bottom_padding, scrollView.getPaddingBottom());
        }
        int menuHeight = (int) activity.getResources().getDimension(R.dimen.floating_select_menu_height);
        scrollView.setPadding(scrollView.getPaddingLeft(), scrollView.getPaddingTop(),
                scrollView.getPaddingRight(),
                (Integer) scrollView.getTag(R.id.podcast_original_bottom_padding) + menuHeight + clearance);
    }

    public static void end(FragmentActivity activity, FloatingSelectMenu menu, View scrollView) {
        menu.setVisibility(View.GONE);
        Object tag = scrollView.getTag(R.id.podcast_original_bottom_padding);
        int original = tag instanceof Integer ? (Integer) tag : 0;
        scrollView.setPadding(scrollView.getPaddingLeft(), scrollView.getPaddingTop(),
                scrollView.getPaddingRight(), original + clearance(activity));
    }

    private static int clearance(FragmentActivity activity) {
        if (activity instanceof MainActivity) {
            return ((MainActivity) activity).getDockClearance();
        }
        return 0;
    }
}
