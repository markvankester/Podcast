package de.danoeh.antennapod.ui.podcast;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PodcastRootLayout extends LinearLayout {
    public PodcastRootLayout(Context context) {
        super(context);
    }

    public PodcastRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        return showAsSheet(originalView) || super.showContextMenuForChild(originalView);
    }

    @Override
    public boolean showContextMenuForChild(View originalView, float x, float y) {
        return showAsSheet(originalView) || super.showContextMenuForChild(originalView);
    }

    private boolean showAsSheet(View view) {
        RecyclerView recyclerView = findRecyclerView(view);
        Activity activity = findActivity(getContext());
        if (recyclerView == null || activity == null
                || !(recyclerView.getAdapter() instanceof OnCreateContextMenuListener)) {
            return false;
        }
        PopupMenu popup = new PopupMenu(getContext(), view);
        Menu menu = popup.getMenu();
        PodcastContextMenu contextMenu = new PodcastContextMenu(getContext(), menu);
        ((OnCreateContextMenuListener) recyclerView.getAdapter()).onCreateContextMenu(contextMenu, view, null);
        popup.setOnMenuItemClickListener(item -> activity.onMenuItemSelected(Window.FEATURE_CONTEXT_MENU, item));

        List<PodcastPopupMenu.Entry> entries = new ArrayList<>();
        Integer lastGroup = null;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible() || item.getTitle() == null) {
                continue;
            }
            boolean startsGroup = lastGroup != null && lastGroup != item.getGroupId();
            lastGroup = item.getGroupId();
            entries.add(new PodcastPopupMenu.Entry(item.getItemId(),
                    PodcastMenus.iconFor(getContext(), item), item.getTitle(), null, startsGroup));
        }
        if (!entries.isEmpty()) {
            PodcastPopupMenu.showSheet(getContext(), contextMenu.getHeaderTitle(), entries,
                    id -> menu.performIdentifierAction(id, 0));
        }
        return true;
    }

    @Nullable
    private RecyclerView findRecyclerView(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View && parent != this) {
            if (parent instanceof RecyclerView) {
                return (RecyclerView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Nullable
    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
