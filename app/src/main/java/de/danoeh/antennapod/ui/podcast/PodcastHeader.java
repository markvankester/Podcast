package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.SelectableAdapter;
import de.danoeh.antennapod.ui.common.ThemeUtils;

public class PodcastHeader extends LinearLayout {
    private static final int BUTTON_SIZE_DP = 48;
    private static final int PILL_HEIGHT_DP = 48;
    private static final int ICON_PADDING_DP = 14;

    private final View topRow;
    private final TextView backLink;
    private final View titleRow;
    private final TextView title;
    private final TextView subtitle;
    private final LinearLayout actions;
    private final LinearLayout topRowContainer;
    private SelectableAdapter<?> selectAdapter;
    private CharSequence savedSubtitle;
    private int[] savedActionVisibility;
    private TextView selectAllPill;
    private TextView donePill;

    public PodcastHeader(Context context) {
        this(context, null);
    }

    public PodcastHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.podcast_header, this);
        topRow = findViewById(R.id.podcast_header_top);
        topRowContainer = (LinearLayout) topRow;
        backLink = findViewById(R.id.podcast_header_back);
        titleRow = findViewById(R.id.podcast_header_title_row);
        title = findViewById(R.id.podcast_header_title);
        subtitle = findViewById(R.id.podcast_header_subtitle);
        actions = findViewById(R.id.podcast_header_actions);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyStatusBarInset();
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            applyStatusBarInset();
            return insets;
        });
        ViewCompat.requestApplyInsets(this);
        post(this::applyStatusBarInset);
    }

    private void applyStatusBarInset() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets != null) {
            setTopInset(insets.getInsets(WindowInsetsCompat.Type.statusBars()).top);
        }
    }

    private void setTopInset(int top) {
        if (getPaddingTop() != top) {
            setPadding(getPaddingLeft(), top, getPaddingRight(), getPaddingBottom());
        }
    }

    public void setTitle(CharSequence text) {
        title.setText(text);
    }

    public void setSubtitle(@Nullable CharSequence text) {
        if (selectAdapter != null) {
            savedSubtitle = text;
            return;
        }
        subtitle.setText(text);
        boolean visible = text != null && text.length() > 0;
        subtitle.setVisibility(visible ? VISIBLE : GONE);
        titleRow.setPadding(titleRow.getPaddingLeft(), titleRow.getPaddingTop(), titleRow.getPaddingRight(),
                visible ? getResources().getDimensionPixelSize(R.dimen.podcast_header_subtitle_gap) : 0);
    }

    public void setBackLink(@Nullable CharSequence label, @Nullable OnClickListener listener) {
        boolean visible = label != null;
        backLink.setText(label);
        backLink.setOnClickListener(listener);
        topRow.setVisibility(visible ? VISIBLE : GONE);
        titleRow.setPadding(titleRow.getPaddingLeft(), visible ? 0 : PodcastMetrics.dp(getContext(), 32),
                titleRow.getPaddingRight(), titleRow.getPaddingBottom());
        ViewGroup current = (ViewGroup) actions.getParent();
        ViewGroup wanted = visible ? topRowContainer : (ViewGroup) titleRow;
        if (current != wanted) {
            current.removeView(actions);
            wanted.addView(actions);
        }
    }

    public void clearActions() {
        actions.removeAllViews();
    }

    public void addAction(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                view.getLayoutParams() != null ? view.getLayoutParams().width : LayoutParams.WRAP_CONTENT,
                view.getLayoutParams() != null ? view.getLayoutParams().height : LayoutParams.WRAP_CONTENT);
        if (actions.getChildCount() > 0) {
            params.setMarginStart(PodcastMetrics.dp(getContext(), 8));
        }
        actions.addView(view, params);
    }

    public View addIconButton(@DrawableRes int icon, @StringRes int description, boolean accent,
                              OnClickListener listener) {
        ImageView button = new ImageView(getContext());
        int size = PodcastMetrics.dp(getContext(), BUTTON_SIZE_DP);
        button.setLayoutParams(new LayoutParams(size, size));
        int padding = PodcastMetrics.dp(getContext(), ICON_PADDING_DP);
        button.setPadding(padding, padding, padding, padding);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setImageResource(icon);
        button.setBackgroundResource(R.drawable.podcast_bg_round);
        button.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColorFromAttr(getContext(),
                accent ? R.attr.podcast_accent_soft : R.attr.podcast_subtle_fill)));
        int tint = ThemeUtils.getColorFromAttr(getContext(),
                accent ? R.attr.podcast_accent : R.attr.podcast_text_primary);
        ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(tint));
        button.setContentDescription(getContext().getString(description));
        button.setOnClickListener(withHaptic(listener));
        addAction(button);
        return button;
    }

    public View addPillButton(@StringRes int text, OnClickListener listener) {
        TextView button = createPill(text, R.attr.podcast_accent, listener);
        addAction(button);
        return button;
    }

    private TextView createPill(@StringRes int text, @AttrRes int textColor, OnClickListener listener) {
        TextView button = new TextView(getContext());
        button.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                PodcastMetrics.dp(getContext(), PILL_HEIGHT_DP)));
        int padding = PodcastMetrics.dp(getContext(), 16);
        button.setPadding(padding, 0, padding, 0);
        button.setGravity(Gravity.CENTER);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.podcast_text_body));
        button.setTypeface(ResourcesCompat.getFont(getContext(), R.font.manrope_bold));
        button.setTextColor(ThemeUtils.getColorFromAttr(getContext(), textColor));
        button.setBackgroundResource(R.drawable.podcast_bg_pill);
        button.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColorFromAttr(getContext(),
                R.attr.podcast_subtle_fill)));
        button.setOnClickListener(withHaptic(listener));
        return button;
    }

    public void startSelectMode(SelectableAdapter<?> adapter) {
        if (selectAdapter != null) {
            endSelectMode();
        }
        selectAdapter = adapter;
        savedSubtitle = subtitle.getText();
        int count = actions.getChildCount();
        savedActionVisibility = new int[count];
        for (int i = 0; i < count; i++) {
            View child = actions.getChildAt(i);
            savedActionVisibility[i] = child.getVisibility();
            child.setVisibility(GONE);
        }
        selectAllPill = createPill(R.string.select_all_label, R.attr.podcast_text_primary,
                v -> adapter.toggleSelectAll());
        donePill = createPill(R.string.podcast_done, R.attr.podcast_accent, v -> adapter.endSelectMode());
        actions.addView(selectAllPill);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                PodcastMetrics.dp(getContext(), PILL_HEIGHT_DP));
        doneParams.setMarginStart(PodcastMetrics.dp(getContext(), 8));
        actions.addView(donePill, doneParams);
        adapter.setOnSelectionChangedListener(this::updateSelection);
        updateSelection();
    }

    public void endSelectMode() {
        if (selectAdapter == null) {
            return;
        }
        selectAdapter.setOnSelectionChangedListener(null);
        selectAdapter = null;
        actions.removeView(selectAllPill);
        actions.removeView(donePill);
        selectAllPill = null;
        donePill = null;
        for (int i = 0; i < savedActionVisibility.length && i < actions.getChildCount(); i++) {
            actions.getChildAt(i).setVisibility(savedActionVisibility[i]);
        }
        CharSequence restored = savedSubtitle;
        savedSubtitle = null;
        setSubtitle(restored);
    }

    private void updateSelection() {
        if (selectAdapter == null) {
            return;
        }
        int selected = selectAdapter.getDisplayedSelectedCount();
        subtitle.setText(selected == 0 ? getContext().getString(R.string.podcast_nothing_selected)
                : getResources().getQuantityString(R.plurals.podcast_selected_count, selected, selected));
        subtitle.setVisibility(VISIBLE);
        boolean allSelected = selectAdapter.getSelectedCount() > 0
                && selectAdapter.getSelectedCount() == selectAdapter.getItemCount();
        selectAllPill.setText(allSelected ? R.string.deselect_all_label : R.string.select_all_label);
    }

    public View addOverflowButton(Menu menu, Toolbar.OnMenuItemClickListener listener, int... excludedIds) {
        View[] holder = new View[1];
        holder[0] = addIconButton(R.drawable.podcast_ic_more, R.string.podcast_more_options, false,
                v -> PodcastMenus.showPopup(holder[0], menu, listener, excludedIds));
        return holder[0];
    }

    private static OnClickListener withHaptic(OnClickListener listener) {
        return v -> {
            PodcastHaptics.tap(v);
            listener.onClick(v);
        };
    }
}
