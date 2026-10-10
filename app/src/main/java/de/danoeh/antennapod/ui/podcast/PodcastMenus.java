package de.danoeh.antennapod.ui.podcast;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.widget.Toolbar;
import de.danoeh.antennapod.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PodcastMenus {
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
        List<PodcastPopupMenu.Entry> entries = new ArrayList<>();
        Integer lastGroup = null;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible() || excluded.contains(item.getItemId()) || item.getTitle() == null) {
                continue;
            }
            boolean startsGroup = lastGroup != null && lastGroup != item.getGroupId();
            lastGroup = item.getGroupId();
            entries.add(new PodcastPopupMenu.Entry(item.getItemId(), item.getIcon(), item.getTitle(), null,
                    startsGroup)
                    .setChecked(item.isCheckable() ? item.isChecked() : null)
                    .setOpensSubmenu(item.hasSubMenu())
                    .setEnabled(item.isEnabled()));
        }
        if (entries.isEmpty()) {
            return;
        }
        PodcastPopupMenu.show(anchor, PodcastPopupMenu.Colors.fromTheme(anchor.getContext()), entries, id -> {
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
}
