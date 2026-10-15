package de.danoeh.antennapod.ui.podcast;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;

public class PodcastChrome {
    private static final int DOCK_HIDE_SHIFT_DP = 16;
    private static final long DOCK_ANIMATION_MS = 150;
    private final MainActivity activity;
    private final PodcastDock dock;
    private Insets insets = Insets.NONE;
    private boolean playerVisible = false;
    private boolean dockHidden = false;
    private float lastSlideOffset = -1f;
    private View sheet;
    private View playerContent;
    private ColorDrawable sheetDrawable;
    private int sheetBackground;

    private final FragmentManager.FragmentLifecycleCallbacks fragmentCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(FragmentManager fm, Fragment f, View v,
                                                  Bundle savedInstanceState) {
                    linkRefreshToHeader(v);
                    applyIfMainContent(f);
                }

                @Override
                public void onFragmentResumed(FragmentManager fm, Fragment f) {
                    applyIfMainContent(f);
                }

                @Override
                public void onFragmentStarted(FragmentManager fm, Fragment f) {
                    if (f instanceof DialogFragment
                            && ((DialogFragment) f).getDialog() instanceof BottomSheetDialog) {
                        PodcastPopupMenu.applyGlass((BottomSheetDialog) ((DialogFragment) f).getDialog());
                    }
                }
            };

    public PodcastChrome(MainActivity activity, PodcastDock.Listener listener) {
        this.activity = activity;
        this.dock = new PodcastDock(activity.findViewById(R.id.podcast_dock), listener);
        activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(fragmentCallbacks, true);
    }

    private static void linkRefreshToHeader(View root) {
        AppBarLayout appBar = findView(root, AppBarLayout.class);
        SwipeRefreshLayout refresh = findView(root, SwipeRefreshLayout.class);
        if (appBar == null || refresh == null) {
            return;
        }
        appBar.addOnOffsetChangedListener((bar, offset) -> {
            boolean enabled = offset == 0 || refresh.isRefreshing();
            if (refresh.isEnabled() != enabled) {
                refresh.setEnabled(enabled);
            }
        });
    }

    @Nullable
    private static <T extends View> T findView(View view, Class<T> type) {
        if (type.isInstance(view)) {
            return type.cast(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findView(group.getChildAt(i), type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public PodcastDock getDock() {
        return dock;
    }

    public void setDockHidden(boolean hidden) {
        if (dockHidden == hidden) {
            return;
        }
        dockHidden = hidden;
        View root = dock.getRoot();
        root.animate().cancel();
        if (hidden) {
            root.animate().alpha(0f).translationY(PodcastMetrics.dp(activity, DOCK_HIDE_SHIFT_DP))
                    .setDuration(DOCK_ANIMATION_MS)
                    .withEndAction(() -> {
                        if (dockHidden) {
                            root.setVisibility(View.INVISIBLE);
                        }
                    }).start();
            return;
        }
        float offset = Math.max(0f, lastSlideOffset);
        lastSlideOffset = -1f;
        if (offset > 0f) {
            onSheetSlide(offset);
            return;
        }
        root.setVisibility(View.VISIBLE);
        root.animate().alpha(1f).translationY(0f).setDuration(DOCK_ANIMATION_MS).start();
    }

    public void onDestroy() {
        activity.getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(fragmentCallbacks);
    }

    public void onInsetsChanged(Insets systemBarInsets, boolean visible) {
        insets = systemBarInsets;
        lastSlideOffset = -1f;
        playerVisible = visible;
        View root = dock.getRoot();
        int side = PodcastMetrics.dp(activity, PodcastMetrics.SIDE_MARGIN_DP);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) root.getLayoutParams();
        params.setMargins(side + insets.left, 0, side + insets.right,
                PodcastMetrics.dp(activity, PodcastMetrics.DOCK_BOTTOM_MARGIN_DP) + insets.bottom);
        root.setLayoutParams(params);
        applyScrollPadding();
    }

    public int getPeekHeight() {
        return PodcastMetrics.dp(activity, PodcastMetrics.PILL_HEIGHT_DP + PodcastMetrics.ROW_GAP_DP
                + PodcastMetrics.TAB_ROW_HEIGHT_DP + PodcastMetrics.DOCK_BOTTOM_MARGIN_DP) + insets.bottom;
    }

    public int getClearance() {
        int clearance = PodcastMetrics.dp(activity, PodcastMetrics.TAB_ROW_HEIGHT_DP
                + PodcastMetrics.DOCK_BOTTOM_MARGIN_DP + PodcastMetrics.CONTENT_GAP_DP) + insets.bottom;
        if (playerVisible) {
            clearance += PodcastMetrics.dp(activity, PodcastMetrics.PILL_HEIGHT_DP + PodcastMetrics.ROW_GAP_DP);
        }
        return clearance;
    }

    public void onSheetSlide(float slideOffset) {
        float offset = Math.max(0f, Math.min(1f, slideOffset));
        if (offset == lastSlideOffset && playerContent != null) {
            return;
        }
        lastSlideOffset = offset;
        View root = dock.getRoot();
        if (!dockHidden) {
            float travel = root.getHeight()
                    + PodcastMetrics.dp(activity, PodcastMetrics.DOCK_BOTTOM_MARGIN_DP) + insets.bottom;
            root.setTranslationY(offset * travel);
            root.setAlpha(1f - Math.min(1f, offset * 2.5f));
            root.setVisibility(offset >= 0.99f ? View.INVISIBLE : View.VISIBLE);
        }

        if (sheet == null) {
            sheet = activity.findViewById(R.id.audioplayerFragment);
            if (sheet == null) {
                return;
            }
            sheetBackground = MaterialColors.getColor(sheet, android.R.attr.colorBackground, Color.WHITE);
        }
        if (sheetDrawable == null || sheet.getBackground() != sheetDrawable) {
            sheetDrawable = new ColorDrawable(sheetBackground);
            sheet.setBackground(sheetDrawable);
        }
        float fade = Math.max(0f, Math.min(1f, (offset - 0.05f) / 0.3f));
        sheetDrawable.setColor(ColorUtils.setAlphaComponent(sheetBackground, Math.round(fade * 255)));
        if (playerContent == null) {
            playerContent = sheet.findViewById(R.id.playerContent);
        }
        if (playerContent != null) {
            playerContent.setAlpha(fade);
            playerContent.setVisibility(fade < 0.01f ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void applyIfMainContent(Fragment fragment) {
        Fragment top = fragment;
        while (top.getParentFragment() != null) {
            top = top.getParentFragment();
        }
        if (top.getId() == R.id.main_content_view && fragment.getView() != null
                && !isInsideScrollContainer(fragment.getView())) {
            applyToTree(fragment.getView());
        }
    }

    private boolean isInsideScrollContainer(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent instanceof NestedScrollView || parent instanceof ScrollView
                    || (parent instanceof RecyclerView && !isHorizontal((RecyclerView) parent))) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    public void applyScrollPadding() {
        View content = activity.findViewById(R.id.main_content_view);
        if (content != null) {
            applyToTree(content);
        }
    }

    private void applyToTree(View view) {
        if (view instanceof RecyclerView) {
            if (!isHorizontal((RecyclerView) view)) {
                pad(view);
            }
        } else if (view instanceof NestedScrollView || view instanceof ScrollView) {
            pad(view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToTree(group.getChildAt(i));
            }
        }
    }

    private boolean isHorizontal(RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            return ((LinearLayoutManager) layoutManager).getOrientation() == RecyclerView.HORIZONTAL;
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            return ((StaggeredGridLayoutManager) layoutManager).getOrientation() == RecyclerView.HORIZONTAL;
        }
        return false;
    }

    private void pad(View view) {
        Object tag = view.getTag(R.id.podcast_original_bottom_padding);
        int original;
        if (tag instanceof Integer) {
            original = (Integer) tag;
        } else {
            original = view.getPaddingBottom();
            view.setTag(R.id.podcast_original_bottom_padding, original);
        }
        ((ViewGroup) view).setClipToPadding(false);
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                original + getClearance());
    }
}
