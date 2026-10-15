package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.widget.ImageViewCompat;
import com.google.android.material.color.MaterialColors;
import de.danoeh.antennapod.R;

import java.util.Locale;

public class PodcastTabView extends FrameLayout {
    private final ImageView outlineIcon;
    private final ImageView filledIcon;
    private final TextView label;
    private final TextView badge;
    private final int activeColor;
    private final int inactiveColor;
    private final Typeface regularTypeface;
    private final Typeface selectedTypeface;
    private final CharSequence labelText;
    private boolean boldApplied = false;

    public PodcastTabView(Context context, @DrawableRes int outlineRes, @DrawableRes int filledRes,
                          @StringRes int labelRes) {
        super(context);
        setClipChildren(false);
        activeColor = MaterialColors.getColor(context, R.attr.podcast_accent, Color.RED);
        inactiveColor = MaterialColors.getColor(context, R.attr.podcast_tab_inactive, Color.DKGRAY);
        regularTypeface = ResourcesCompat.getFont(context, R.font.manrope_semibold);
        selectedTypeface = ResourcesCompat.getFont(context, R.font.manrope_bold);
        setClickable(true);
        setFocusable(true);
        labelText = context.getString(labelRes);
        setContentDescription(labelText);
        ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setRoleDescription(host.getContext().getString(R.string.podcast_tab_role));
                info.setSelected(host.isSelected());
                if (host.isSelected()) {
                    info.setStateDescription(host.getContext().getString(R.string.podcast_tab_selected));
                }
            }
        });

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        FrameLayout iconFrame = new FrameLayout(context);
        int iconSize = PodcastMetrics.dp(context, 24);
        column.addView(iconFrame, new LinearLayout.LayoutParams(iconSize, iconSize));
        outlineIcon = createIcon(context, outlineRes);
        filledIcon = createIcon(context, filledRes);
        iconFrame.addView(outlineIcon, new LayoutParams(iconSize, iconSize));
        iconFrame.addView(filledIcon, new LayoutParams(iconSize, iconSize));

        label = new TextView(context);
        label.setText(labelRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.podcast_text_xxs));
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = PodcastMetrics.dp(context, 2);
        column.addView(label, labelParams);

        badge = new TextView(context);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.podcast_text_xxs));
        badge.setTypeface(selectedTypeface);
        badge.setTextColor(MaterialColors.getColor(context, R.attr.podcast_on_accent, Color.WHITE));
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        int badgePadding = PodcastMetrics.dp(context, 4);
        badge.setPadding(badgePadding, 0, badgePadding, 0);
        badge.setMinWidth(PodcastMetrics.dp(context, 16));
        badge.setMinHeight(PodcastMetrics.dp(context, 16));
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(activeColor);
        badgeBackground.setCornerRadius(context.getResources().getDimension(R.dimen.podcast_radius_pill));
        badge.setBackground(badgeBackground);
        badge.setVisibility(GONE);
        LayoutParams badgeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        badgeParams.topMargin = PodcastMetrics.dp(context, 4);
        badgeParams.leftMargin = PodcastMetrics.dp(context, 26);
        addView(badge, badgeParams);

        setSelectionFraction(0f);
    }

    private ImageView createIcon(Context context, @DrawableRes int res) {
        ImageView imageView = new ImageView(context);
        imageView.setImageResource(res);
        imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return imageView;
    }

    public void setSelectionFraction(float fraction) {
        float clamped = Math.max(0f, Math.min(1f, fraction));
        int color = ColorUtils.blendARGB(inactiveColor, activeColor, clamped);
        ColorStateList tint = ColorStateList.valueOf(color);
        ImageViewCompat.setImageTintList(outlineIcon, tint);
        ImageViewCompat.setImageTintList(filledIcon, tint);
        outlineIcon.setAlpha(1f - clamped);
        filledIcon.setAlpha(clamped);
        label.setTextColor(color);
        boolean bold = clamped >= 0.5f;
        if (bold != boldApplied || label.getTypeface() == null) {
            boldApplied = bold;
            label.setTypeface(bold ? selectedTypeface : regularTypeface);
        }
        setSelected(clamped >= 0.5f);
    }

    public void setBadgeCount(int count) {
        badge.setVisibility(count > 0 ? VISIBLE : GONE);
        badge.setText(String.format(Locale.getDefault(), "%d", count));
        if (count > 0) {
            setContentDescription(getContext().getString(R.string.podcast_tab_with_badge, labelText,
                    getResources().getQuantityString(R.plurals.podcast_new_episodes_count, count, count)));
        } else {
            setContentDescription(labelText);
        }
    }
}
