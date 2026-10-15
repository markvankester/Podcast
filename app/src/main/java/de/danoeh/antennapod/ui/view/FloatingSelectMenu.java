package de.danoeh.antennapod.ui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.widget.ImageViewCompat;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.podcast.PodcastSelectMode;
import de.danoeh.antennapod.databinding.FloatingSelectMenuBinding;
import de.danoeh.antennapod.databinding.FloatingSelectMenuItemBinding;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.ui.podcast.PodcastMetrics;

public class FloatingSelectMenu extends FrameLayout {
    private FloatingSelectMenuBinding viewBinding;
    private Menu menu;
    private MenuItem.OnMenuItemClickListener menuItemClickListener;

    public FloatingSelectMenu(@NonNull Context context) {
        super(context);
        setup();
    }

    public FloatingSelectMenu(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public FloatingSelectMenu(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    private void setup() {
        viewBinding = FloatingSelectMenuBinding.bind(
                View.inflate(getContext(), R.layout.floating_select_menu, null));
        viewBinding.scrollView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> equalizeItems());
        addView(viewBinding.getRoot());
        setVisibility(View.GONE);
    }

    public void inflate(@MenuRes int menuRes) {
        PopupMenu popupMenu = new PopupMenu(getContext(), new View(getContext()));
        popupMenu.inflate(menuRes);
        menu = popupMenu.getMenu();
        updateItemVisibility();
    }

    public void updateItemVisibility() {
        viewBinding.selectContainer.removeAllViews();
        if (menu == null) {
            return;
        }
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) {
                continue;
            }
            FloatingSelectMenuItemBinding itemBinding = FloatingSelectMenuItemBinding.bind(
                    View.inflate(getContext(), R.layout.floating_select_menu_item, null));
            itemBinding.titleLabel.setText(PodcastSelectMode.shortLabel(getContext(), item));
            itemBinding.getRoot().setContentDescription(item.getTitle());
            itemBinding.icon.setImageDrawable(item.getIcon());
            if (item.getItemId() == R.id.remove_item) {
                int accent = ThemeUtils.getColorFromAttr(getContext(), R.attr.podcast_accent);
                itemBinding.titleLabel.setTextColor(accent);
                ImageViewCompat.setImageTintList(itemBinding.icon, ColorStateList.valueOf(accent));
            }
            itemBinding.getRoot().setOnClickListener(view -> menuItemClickListener.onMenuItemClick(item));
            viewBinding.selectContainer.addView(itemBinding.getRoot());
        }
        equalizeItems();
    }

    private void equalizeItems() {
        int count = viewBinding.selectContainer.getChildCount();
        if (count == 0) {
            return;
        }
        int available = viewBinding.scrollView.getWidth() - viewBinding.scrollView.getPaddingLeft()
                - viewBinding.scrollView.getPaddingRight();
        int minWidth = Math.max(PodcastMetrics.dp(getContext(), 64), available / count);
        for (int i = 0; i < count; i++) {
            View child = viewBinding.selectContainer.getChildAt(i);
            if (child.getMinimumWidth() != minWidth) {
                child.setMinimumWidth(minWidth);
            }
        }
    }

    public Menu getMenu() {
        return menu;
    }

    public void setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener listener) {
        this.menuItemClickListener = listener;
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility == View.VISIBLE && getVisibility() != View.VISIBLE) {
            announceForAccessibility(getContext().getString(R.string.multi_select_started_talkback));
            viewBinding.scrollView.scrollTo(0, 0);
            updateItemVisibility();
            setAlpha(0f);
            super.setVisibility(View.VISIBLE);
            animate().alpha(1f).setDuration(100).start();
        } else if (visibility != View.VISIBLE && getVisibility() == View.VISIBLE && isAttachedToWindow()) {
            animate().alpha(0f).setDuration(100).withEndAction(() -> super.setVisibility(visibility)).start();
        } else {
            super.setVisibility(visibility);
            viewBinding.scrollView.scrollTo(0, 0);
            updateItemVisibility();
        }
    }
}
