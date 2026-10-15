package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.common.ThemeUtils;

public class PodcastFooterHint extends RecyclerView.ItemDecoration {
    private static final int SIDE_PADDING_DP = 20;
    private static final int TOP_PADDING_DP = 14;
    private static final int BOTTOM_PADDING_DP = 8;

    private final String text;
    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final int sidePadding;
    private final int topPadding;
    private final int bottomPadding;
    private StaticLayout layout;
    private int layoutWidth = -1;

    public PodcastFooterHint(Context context, @StringRes int text) {
        this.text = context.getString(text);
        paint.setColor(ThemeUtils.getColorFromAttr(context, R.attr.podcast_text_secondary));
        paint.setTextSize(context.getResources().getDimension(R.dimen.podcast_text_m));
        paint.setTypeface(ResourcesCompat.getFont(context, R.font.manrope));
        sidePadding = PodcastMetrics.dp(context, SIDE_PADDING_DP);
        topPadding = PodcastMetrics.dp(context, TOP_PADDING_DP);
        bottomPadding = PodcastMetrics.dp(context, BOTTOM_PADDING_DP);
    }

    private StaticLayout getLayout(int parentWidth) {
        int width = Math.max(1, parentWidth - 2 * sidePadding);
        if (layout == null || layoutWidth != width) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build();
            layoutWidth = width;
        }
        return layout;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        if (parent.getChildAdapterPosition(view) == state.getItemCount() - 1) {
            outRect.bottom = getLayout(parent.getWidth()).getHeight() + topPadding + bottomPadding;
        }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (parent.getChildAdapterPosition(child) == state.getItemCount() - 1) {
                canvas.save();
                canvas.translate(parent.getPaddingLeft() + sidePadding,
                        child.getBottom() + child.getTranslationY() + topPadding);
                getLayout(parent.getWidth()).draw(canvas);
                canvas.restore();
            }
        }
    }
}
