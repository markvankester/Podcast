package de.danoeh.antennapod.ui.podcast;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.core.view.ViewCompat;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.screen.InboxFragment;
import de.danoeh.antennapod.ui.screen.home.HomeFragment;
import de.danoeh.antennapod.ui.screen.queue.QueueFragment;
import de.danoeh.antennapod.ui.screen.subscriptions.SubscriptionFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class PodcastDock {
    private static final String TAG = "PodcastDock";
    private static final long ANIMATION_DURATION = 300;
    private static final String[] TAGS = {HomeFragment.TAG, QueueFragment.TAG, InboxFragment.TAG,
            SubscriptionFragment.TAG};

    public interface Listener {
        void onTabClicked(String tag);

        void onSearchClicked();
    }

    private final View root;
    private final View capsule;
    private final View highlight;
    private final LinearLayout tabsContainer;
    private final PodcastTabView[] tabs = new PodcastTabView[TAGS.length];
    private final float[] fractions = new float[TAGS.length];
    private int selectedIndex = 0;
    private float selectionScale = 1f;
    private boolean searchActive = false;
    private final ImageView searchIcon;
    private final View searchButton;
    private final int searchActiveColor;
    private final int searchInactiveColor;
    private ValueAnimator animator;
    private ValueAnimator searchAnimator;
    private Disposable badgeLoader;

    public PodcastDock(View root, Listener listener) {
        this.root = root;
        final Context context = root.getContext();
        capsule = root.findViewById(R.id.podcast_tab_capsule);
        highlight = root.findViewById(R.id.podcast_tab_highlight);
        tabsContainer = root.findViewById(R.id.podcast_tabs);
        searchButton = root.findViewById(R.id.podcast_search_button);
        searchIcon = root.findViewById(R.id.podcast_search_icon);
        searchActiveColor = MaterialColors.getColor(context, R.attr.podcast_accent, Color.RED);
        searchInactiveColor = MaterialColors.getColor(context, R.attr.podcast_text_primary, Color.BLACK);

        GradientDrawable highlightBackground = new GradientDrawable();
        highlightBackground.setColor(MaterialColors.getColor(context, R.attr.podcast_tab_highlight,
                Color.argb(15, 0, 0, 0)));
        highlightBackground.setCornerRadius(PodcastMetrics.dp(context, 100));
        highlight.setBackground(highlightBackground);

        tabs[0] = new PodcastTabView(context, R.drawable.podcast_ic_home_outline,
                R.drawable.podcast_ic_home_filled, R.string.podcast_tab_home);
        tabs[1] = new PodcastTabView(context, R.drawable.podcast_ic_queue_outline,
                R.drawable.podcast_ic_queue_filled, R.string.podcast_tab_queue);
        tabs[2] = new PodcastTabView(context, R.drawable.podcast_ic_new_outline,
                R.drawable.podcast_ic_new_filled, R.string.podcast_tab_new);
        tabs[3] = new PodcastTabView(context, R.drawable.podcast_ic_library_outline,
                R.drawable.podcast_ic_library_filled, R.string.podcast_tab_library);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabsContainer.addView(tabs[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            tabs[i].setOnClickListener(v -> {
                PodcastHaptics.tick(v);
                select(index, true);
                listener.onTabClicked(TAGS[index]);
            });
        }
        fractions[selectedIndex] = 1f;
        applyFractions();
        capsule.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (animator == null) {
                positionHighlight(selectedIndex);
            }
        });
        root.findViewById(R.id.podcast_search_button).setOnClickListener(v -> {
            PodcastHaptics.tap(v);
            listener.onSearchClicked();
        });
    }

    public View getRoot() {
        return root;
    }

    public void updateSelectedItem(String tag) {
        for (int i = 0; i < TAGS.length; i++) {
            if (TAGS[i].equals(tag)) {
                select(i, true);
                return;
            }
        }
    }

    private void select(int index, boolean animate) {
        if (index == selectedIndex) {
            return;
        }
        selectedIndex = index;
        if (animator != null) {
            animator.cancel();
        }
        final float[] startFractions = fractions.clone();
        final float startX = highlight.getTranslationX();
        final float targetX = tabWidth() * index;
        if (!animate || !ViewCompat.isLaidOut(capsule) || tabWidth() == 0) {
            for (int i = 0; i < fractions.length; i++) {
                fractions[i] = i == index ? 1f : 0f;
            }
            applyFractions();
            positionHighlight(index);
            return;
        }
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0f, 1f);
        valueAnimator.setDuration(ANIMATION_DURATION);
        valueAnimator.setInterpolator(new OvershootInterpolator(1.0f));
        valueAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float clamped = Math.max(0f, Math.min(1f, t));
            highlight.setTranslationX(startX + (targetX - startX) * t);
            for (int i = 0; i < fractions.length; i++) {
                float end = i == index ? 1f : 0f;
                fractions[i] = startFractions[i] + (end - startFractions[i]) * clamped;
            }
            applyFractions();
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    animator = null;
                    for (int i = 0; i < fractions.length; i++) {
                        fractions[i] = i == selectedIndex ? 1f : 0f;
                    }
                    applyFractions();
                    positionHighlight(selectedIndex);
                }
            }
        });
        animator = valueAnimator;
        valueAnimator.start();
    }

    private int tabWidth() {
        return tabsContainer.getWidth() / TAGS.length;
    }

    private void positionHighlight(int index) {
        int width = tabWidth();
        ViewGroup.LayoutParams params = highlight.getLayoutParams();
        if (params.width != width) {
            params.width = width;
            highlight.setLayoutParams(params);
        }
        highlight.setTranslationX(width * index);
    }

    public void setSearchActive(boolean active) {
        if (searchActive == active) {
            return;
        }
        searchActive = active;
        searchButton.setSelected(active);
        searchIcon.setImageTintList(ColorStateList.valueOf(active ? searchActiveColor : searchInactiveColor));
        if (searchAnimator != null) {
            searchAnimator.cancel();
        }
        searchAnimator = ValueAnimator.ofFloat(selectionScale, active ? 0f : 1f);
        searchAnimator.setDuration(ANIMATION_DURATION / 2);
        searchAnimator.addUpdateListener(a -> {
            selectionScale = (float) a.getAnimatedValue();
            highlight.setAlpha(selectionScale);
            applyFractions();
        });
        searchAnimator.start();
    }

    private void applyFractions() {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelectionFraction(fractions[i] * selectionScale);
        }
    }

    public void onCreateView() {
        EventBus.getDefault().register(this);
        updateBadge();
    }

    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        if (animator != null) {
            animator.cancel();
        }
        if (searchAnimator != null) {
            searchAnimator.cancel();
        }
        if (badgeLoader != null) {
            badgeLoader.dispose();
            badgeLoader = null;
        }
    }

    private void updateBadge() {
        if (badgeLoader != null) {
            badgeLoader.dispose();
        }
        badgeLoader = Observable.fromCallable(
                        () -> DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.NEW)))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> tabs[2].setBadgeCount(result),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(FeedItemEvent event) {
        if (event.unreadStatusChanged) {
            updateBadge();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        updateBadge();
    }
}
