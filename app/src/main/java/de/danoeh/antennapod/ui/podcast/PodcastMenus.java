package de.danoeh.antennapod.ui.podcast;

import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import de.danoeh.antennapod.R;

import java.util.HashSet;
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
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor, Gravity.END);
        copy(source, popup.getMenu(), excluded);
        popup.setOnMenuItemClickListener(item -> {
            MenuItem original = source.findItem(item.getItemId());
            return listener.onMenuItemClick(original != null ? original : item);
        });
        popup.show();
    }

    private static void copy(Menu source, Menu target, Set<Integer> excluded) {
        Set<Integer> exclusiveGroups = new HashSet<>();
        for (int i = 0; i < source.size(); i++) {
            MenuItem item = source.getItem(i);
            if (!item.isVisible() || excluded.contains(item.getItemId()) || item.getTitle() == null) {
                continue;
            }
            if (item.hasSubMenu()) {
                SubMenu sub = target.addSubMenu(item.getGroupId(), item.getItemId(), Menu.NONE, item.getTitle());
                copy(item.getSubMenu(), sub, excluded);
                continue;
            }
            MenuItem copy = target.add(item.getGroupId(), item.getItemId(), Menu.NONE, item.getTitle());
            copy.setEnabled(item.isEnabled());
            if (item.isCheckable()) {
                copy.setCheckable(true);
                copy.setChecked(item.isChecked());
                if (item.getGroupId() != Menu.NONE) {
                    exclusiveGroups.add(item.getGroupId());
                }
            }
        }
        for (int group : exclusiveGroups) {
            target.setGroupCheckable(group, true, true);
        }
    }
}
