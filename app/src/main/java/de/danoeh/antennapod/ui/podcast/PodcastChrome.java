package de.danoeh.antennapod.ui.podcast;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;

public class PodcastChrome {
    private final MainActivity activity;
    private final PodcastDock dock;
    private Insets insets = Insets.NONE;
    private boolean playerVisible = false;

    private final FragmentManager.FragmentLifecycleCallbacks fragmentCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(FragmentManager fm, Fragment f, View v,
                                                  Bundle savedInstanceState) {
                    applyIfMainContent(f);
                }

                @Override
                public void onFragmentResumed(FragmentManager fm, Fragment f) {
                    applyIfMainContent(f);
                }
            };

    public PodcastChrome(MainActivity activity, PodcastDock.Listener listener) {
        this.activity = activity;
        this.dock = new PodcastDock(activity.findViewById(R.id.podcast_dock), listener);
        activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(fragmentCallbacks, true);
    }

    public PodcastDock getDock() {
        return dock;
    }

    public void onDestroy() {
        activity.getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(fragmentCallbacks);
    }

    public void onInsetsChanged(Insets systemBarInsets, boolean visible) {
        insets = systemBarInsets;
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
        View root = dock.getRoot();
        float travel = root.getHeight()
                + PodcastMetrics.dp(activity, PodcastMetrics.DOCK_BOTTOM_MARGIN_DP) + insets.bottom;
        root.setTranslationY(offset * travel);
        root.setAlpha(1f - Math.min(1f, offset * 2.5f));
        root.setVisibility(offset >= 0.99f ? View.INVISIBLE : View.VISIBLE);

        View sheet = activity.findViewById(R.id.audioplayerFragment);
        View content = sheet.findViewById(R.id.playerContent);
        float fade = Math.max(0f, Math.min(1f, (offset - 0.05f) / 0.3f));
        int background = MaterialColors.getColor(sheet, android.R.attr.colorBackground, Color.WHITE);
        sheet.setBackgroundColor(ColorUtils.setAlphaComponent(background, Math.round(fade * 255)));
        if (content != null) {
            content.setAlpha(fade);
            content.setVisibility(fade < 0.01f ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void applyIfMainContent(Fragment fragment) {
        Fragment top = fragment;
        while (top.getParentFragment() != null) {
            top = top.getParentFragment();
        }
        if (top.getId() == R.id.main_content_view && fragment.getView() != null) {
            applyToTree(fragment.getView());
        }
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
