package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.SelectableAdapter;
import de.danoeh.antennapod.ui.view.FloatingSelectMenu;

public final class PodcastSelectMode {
    private PodcastSelectMode() {
    }

    public static void start(FragmentActivity activity, FloatingSelectMenu menu, View scrollView,
                             @Nullable PodcastHeader header, SelectableAdapter<?> adapter) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) menu.getLayoutParams();
        View dockRoot = activity.findViewById(R.id.podcast_dock);
        if (dockRoot != null) {
            ViewGroup.MarginLayoutParams dockParams = (ViewGroup.MarginLayoutParams) dockRoot.getLayoutParams();
            int side = PodcastMetrics.dp(activity, PodcastMetrics.SIDE_MARGIN_DP);
            params.bottomMargin = dockParams.bottomMargin;
            params.leftMargin = Math.max(0, dockParams.leftMargin - side);
            params.rightMargin = Math.max(0, dockParams.rightMargin - side);
        }
        menu.setLayoutParams(params);
        menu.setVisibility(View.VISIBLE);
        if (!(scrollView.getTag(R.id.podcast_original_bottom_padding) instanceof Integer)) {
            scrollView.setTag(R.id.podcast_original_bottom_padding, scrollView.getPaddingBottom());
        }
        scrollView.setPadding(scrollView.getPaddingLeft(), scrollView.getPaddingTop(),
                scrollView.getPaddingRight(),
                (Integer) scrollView.getTag(R.id.podcast_original_bottom_padding) + clearance(activity));
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).getPodcastChrome().setDockHidden(true);
        }
        if (header != null) {
            header.startSelectMode(adapter);
        }
        menu.post(() -> {
            View bar = activity.findViewById(R.id.action_mode_bar);
            if (bar != null) {
                bar.setPivotY(0f);
                bar.setScaleY(0f);
            }
        });
    }

    public static CharSequence shortLabel(Context context, MenuItem item) {
        int id = item.getItemId();
        int label;
        if (id == R.id.mark_read_item) {
            label = R.string.podcast_action_played;
        } else if (id == R.id.mark_unread_item) {
            label = R.string.podcast_action_unplayed;
        } else if (id == R.id.remove_from_queue_item || id == R.id.remove_inbox_item) {
            label = R.string.podcast_action_remove;
        } else if (id == R.id.add_to_queue_item) {
            label = R.string.podcast_action_queue;
        } else if (id == R.id.add_to_favorites_item) {
            label = R.string.podcast_action_favorite;
        } else if (id == R.id.remove_from_favorites_item) {
            label = R.string.podcast_action_unfavorite;
        } else if (id == R.id.reset_position) {
            label = R.string.podcast_action_reset;
        } else if (id == R.id.move_to_top_item) {
            label = R.string.podcast_action_to_top;
        } else if (id == R.id.move_to_bottom_item) {
            label = R.string.podcast_action_to_bottom;
        } else {
            return item.getTitle();
        }
        return context.getString(label);
    }

    public static void end(FragmentActivity activity, FloatingSelectMenu menu, View scrollView,
                           @Nullable PodcastHeader header) {
        menu.setVisibility(View.GONE);
        Object tag = scrollView.getTag(R.id.podcast_original_bottom_padding);
        int original = tag instanceof Integer ? (Integer) tag : 0;
        scrollView.setPadding(scrollView.getPaddingLeft(), scrollView.getPaddingTop(),
                scrollView.getPaddingRight(), original + clearance(activity));
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).getPodcastChrome().setDockHidden(false);
        }
        if (header != null) {
            header.endSelectMode();
        }
    }

    private static int clearance(FragmentActivity activity) {
        if (activity instanceof MainActivity) {
            return ((MainActivity) activity).getDockClearance();
        }
        return 0;
    }
}
