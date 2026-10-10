package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import de.danoeh.antennapod.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PodcastMenus {
    private static final int RANK_SORT = 0;
    private static final int RANK_FILTER = 1;
    private static final int RANK_VIEW = 2;
    private static final int RANK_NAVIGATE = 10;
    private static final int RANK_OTHER = 20;
    private static final int RANK_DESTRUCTIVE = 30;

    private PodcastMenus() {
    }

    public static void showPopup(View anchor, Menu source, Toolbar.OnMenuItemClickListener listener,
                                 int... excludedIds) {
        Set<Integer> excluded = new HashSet<>();
        excluded.add(R.id.action_search);
        for (int id : excludedIds) {
            excluded.add(id);
        }
        showLevel(anchor, source, listener, excluded);
    }

    private static void showLevel(View anchor, Menu menu, Toolbar.OnMenuItemClickListener listener,
                                  Set<Integer> excluded) {
        List<MenuItem> items = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible() || excluded.contains(item.getItemId()) || item.getTitle() == null) {
                continue;
            }
            items.add(item);
        }
        if (items.size() > 1) {
            items.removeIf(item -> item.getItemId() == R.id.refresh_item);
        }
        if (items.isEmpty()) {
            return;
        }
        Collections.sort(items, (a, b) -> Integer.compare(rank(a.getItemId()), rank(b.getItemId())));
        Context context = anchor.getContext();
        List<PodcastPopupMenu.Entry> entries = new ArrayList<>();
        int lastRank = -1;
        for (MenuItem item : items) {
            int rank = rank(item.getItemId());
            int iconRes = icon(item.getItemId());
            Drawable icon = iconRes != 0 ? AppCompatResources.getDrawable(context, iconRes) : item.getIcon();
            entries.add(new PodcastPopupMenu.Entry(item.getItemId(), icon, item.getTitle(), null,
                    lastRank != -1 && rank / 10 != lastRank / 10)
                    .setChecked(item.isCheckable() ? item.isChecked() : null)
                    .setOpensSubmenu(item.hasSubMenu())
                    .setEnabled(item.isEnabled()));
            lastRank = rank;
        }
        PodcastPopupMenu.show(anchor, PodcastPopupMenu.Colors.fromTheme(context), entries, id -> {
            MenuItem item = menu.findItem(id);
            if (item == null) {
                return;
            }
            if (item.hasSubMenu()) {
                showLevel(anchor, item.getSubMenu(), listener, excluded);
            } else {
                listener.onMenuItemClick(item);
            }
        });
    }

    private static int rank(int id) {
        if (id == R.id.inbox_sort || id == R.id.episodes_sort || id == R.id.downloads_sort
                || id == R.id.queue_sort || id == R.id.subscriptions_sort) {
            return RANK_SORT;
        } else if (id == R.id.filter_items || id == R.id.subscriptions_filter) {
            return RANK_FILTER;
        } else if (id == R.id.subscription_num_columns || id == R.id.subscriptions_counter
                || id == R.id.queue_lock) {
            return RANK_VIEW;
        } else if (id == R.id.podcast_all_episodes_item || id == R.id.show_archive
                || id == R.id.podcast_statistics_item || id == R.id.action_download_logs) {
            return RANK_NAVIGATE;
        } else if (id == R.id.clear_queue || id == R.id.action_delete_downloads_played
                || id == R.id.clear_history_item || id == R.id.remove_all_inbox_item) {
            return RANK_DESTRUCTIVE;
        }
        return RANK_OTHER;
    }

    @DrawableRes
    private static int icon(int id) {
        if (id == R.id.inbox_sort || id == R.id.episodes_sort || id == R.id.downloads_sort
                || id == R.id.queue_sort || id == R.id.subscriptions_sort) {
            return R.drawable.podcast_menu_sort;
        } else if (id == R.id.filter_items || id == R.id.subscriptions_filter) {
            return R.drawable.podcast_menu_filter;
        } else if (id == R.id.subscription_num_columns) {
            return R.drawable.podcast_menu_columns;
        } else if (id == R.id.subscriptions_counter) {
            return R.drawable.podcast_menu_counter;
        } else if (id == R.id.queue_lock) {
            return R.drawable.podcast_menu_lock;
        } else if (id == R.id.podcast_all_episodes_item) {
            return R.drawable.podcast_menu_episodes;
        } else if (id == R.id.show_archive) {
            return R.drawable.podcast_menu_archive;
        } else if (id == R.id.podcast_statistics_item) {
            return R.drawable.podcast_menu_statistics;
        } else if (id == R.id.action_download_logs) {
            return R.drawable.podcast_menu_log;
        } else if (id == R.id.refresh_item) {
            return R.drawable.podcast_menu_refresh;
        } else if (id == R.id.clear_queue || id == R.id.action_delete_downloads_played
                || id == R.id.clear_history_item || id == R.id.remove_all_inbox_item) {
            return R.drawable.podcast_menu_delete;
        }
        return 0;
    }
}
