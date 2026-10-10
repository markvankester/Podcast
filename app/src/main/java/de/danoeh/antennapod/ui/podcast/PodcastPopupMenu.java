package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Consumer;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;
import java.util.List;

public final class PodcastPopupMenu {
    private static final int WIDTH_DP = 268;

    public static final class Entry {
        final int id;
        @Nullable
        final Drawable icon;
        final CharSequence label;
        @Nullable
        final CharSequence value;
        final boolean startsGroup;
        @Nullable
        Boolean checked;
        boolean opensSubmenu;
        boolean enabled = true;

        public Entry(int id, @Nullable Drawable icon, CharSequence label,
                     @Nullable CharSequence value, boolean startsGroup) {
            this.id = id;
            this.icon = icon;
            this.label = label;
            this.value = value;
            this.startsGroup = startsGroup;
        }

        public Entry setChecked(@Nullable Boolean checked) {
            this.checked = checked;
            return this;
        }

        public Entry setOpensSubmenu(boolean opensSubmenu) {
            this.opensSubmenu = opensSubmenu;
            return this;
        }

        public Entry setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }

    public static final class Colors {
        @ColorInt
        final int background;
        @ColorInt
        final int border;
        @ColorInt
        final int text;
        @ColorInt
        final int secondary;
        @ColorInt
        final int divider;
        @ColorInt
        final int ripple;
        @ColorInt
        final int accent;

        public Colors(@ColorInt int background, @ColorInt int border, @ColorInt int text,
                      @ColorInt int secondary, @ColorInt int divider, @ColorInt int ripple, @ColorInt int accent) {
            this.background = background;
            this.border = border;
            this.text = text;
            this.secondary = secondary;
            this.divider = divider;
            this.ripple = ripple;
            this.accent = accent;
        }

        public static Colors forPlayer(@ColorInt int baseColor) {
            return new Colors(ColorUtils.blendARGB(baseColor, Color.BLACK, 0.45f) | 0xFF000000,
                    Color.argb(46, 255, 255, 255), Color.WHITE, Color.argb(179, 255, 255, 255),
                    Color.argb(31, 255, 255, 255), Color.argb(51, 255, 255, 255), Color.WHITE);
        }

        public static Colors fromTheme(Context context) {
            int text = MaterialColors.getColor(context, R.attr.podcast_text_primary, Color.BLACK);
            return new Colors(MaterialColors.getColor(context, R.attr.podcast_menu_surface, Color.WHITE),
                    MaterialColors.getColor(context, R.attr.podcast_glass_border, Color.argb(15, 0, 0, 0)),
                    text,
                    MaterialColors.getColor(context, R.attr.podcast_text_secondary, Color.GRAY),
                    MaterialColors.getColor(context, R.attr.podcast_divider, Color.LTGRAY),
                    ColorUtils.setAlphaComponent(text, 31),
                    MaterialColors.getColor(context, R.attr.podcast_accent, Color.RED));
        }
    }

    private PodcastPopupMenu() {
    }

    public static PopupWindow show(View anchor, Colors colors, List<Entry> entries, Consumer<Integer> onClick) {
        Context context = anchor.getContext();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = PodcastMetrics.dp(context, 6);
        list.setPadding(0, padding, 0, padding);
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);

        boolean anyIcon = false;
        for (Entry entry : entries) {
            anyIcon |= entry.icon != null;
        }

        PopupWindow popup = new PopupWindow(scroll, PodcastMetrics.dp(context, WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.startsGroup && i > 0) {
                list.addView(createDivider(context, colors));
            }
            View row = createRow(context, colors, entry, anyIcon);
            if (entry.enabled) {
                row.setOnClickListener(v -> {
                    PodcastHaptics.tap(v);
                    popup.dismiss();
                    onClick.accept(entry.id);
                });
            } else {
                row.setAlpha(0.4f);
            }
            list.addView(row);
        }

        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.background);
        background.setCornerRadius(PodcastMetrics.dp(context, 22));
        background.setStroke(PodcastMetrics.dp(context, 1), colors.border);
        popup.setBackgroundDrawable(background);
        popup.setElevation(PodcastMetrics.dp(context, 16));
        popup.setAnimationStyle(0);
        popup.showAsDropDown(anchor, 0, PodcastMetrics.dp(context, 8), Gravity.END);

        View window = scroll.getRootView();
        window.setAlpha(0f);
        window.setScaleX(0.92f);
        window.setScaleY(0.92f);
        window.setPivotY(0);
        window.setPivotX(PodcastMetrics.dp(context, WIDTH_DP));
        window.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
        return popup;
    }

    private static View createRow(Context context, Colors colors, Entry entry, boolean iconColumn) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = PodcastMetrics.dp(context, 18);
        row.setPadding(horizontal, 0, horizontal, 0);
        row.setMinimumHeight(PodcastMetrics.dp(context, 50));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.ripple),
                null, new ColorDrawable(Color.WHITE)));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int iconSize = PodcastMetrics.dp(context, 20);
        if (iconColumn) {
            ImageView icon = new ImageView(context);
            if (entry.icon != null) {
                icon.setImageDrawable(entry.icon.mutate());
                icon.setImageTintList(ColorStateList.valueOf(colors.text));
                icon.setAlpha(0.9f);
            }
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMarginEnd(PodcastMetrics.dp(context, 14));
            row.addView(icon, iconParams);
        }

        TextView label = new TextView(context);
        label.setText(entry.label);
        label.setTextColor(colors.text);
        label.setTextSize(15);
        label.setTypeface(ResourcesCompat.getFont(context, R.font.manrope_semibold));
        label.setMaxLines(2);
        int vertical = PodcastMetrics.dp(context, 12);
        label.setPadding(0, vertical, 0, vertical);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (entry.value != null) {
            TextView value = new TextView(context);
            value.setText(entry.value);
            value.setTextColor(colors.secondary);
            value.setTextSize(13);
            value.setTypeface(ResourcesCompat.getFont(context, R.font.manrope_bold));
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valueParams.setMarginStart(PodcastMetrics.dp(context, 8));
            row.addView(value, valueParams);
        }
        int trailing = 0;
        int trailingTint = colors.secondary;
        if (entry.opensSubmenu) {
            trailing = R.drawable.podcast_ic_chevron_right;
        } else if (Boolean.TRUE.equals(entry.checked)) {
            trailing = R.drawable.podcast_ic_check;
            trailingTint = colors.accent;
        }
        if (trailing != 0) {
            ImageView end = new ImageView(context);
            end.setImageResource(trailing);
            end.setImageTintList(ColorStateList.valueOf(trailingTint));
            LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            endParams.setMarginStart(PodcastMetrics.dp(context, 8));
            row.addView(end, endParams);
        }
        return row;
    }

    private static View createDivider(Context context, Colors colors) {
        View divider = new View(context);
        divider.setBackgroundColor(colors.divider);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                PodcastMetrics.dp(context, 1));
        int margin = PodcastMetrics.dp(context, 6);
        params.setMargins(PodcastMetrics.dp(context, 18), margin, PodcastMetrics.dp(context, 18), margin);
        divider.setLayoutParams(params);
        return divider;
    }
}
