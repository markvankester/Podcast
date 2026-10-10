package de.danoeh.antennapod.ui.screen.subscriptions;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.ui.CoverLoader;

import java.lang.ref.WeakReference;

public class SubscriptionViewHolder extends RecyclerView.ViewHolder {
    public final TextView title;
    public final ImageView coverImage;
    public final TextView count;
    public final TextView fallbackTitle;
    public final ImageView gradient;
    public final ImageView selectIcon;
    public final CardView card;
    public final View errorIcon;
    public final WeakReference<Activity> mainActivityRef;

    public SubscriptionViewHolder(@NonNull View itemView, Activity mainActivity) {
        super(itemView);
        title = itemView.findViewById(R.id.titleLabel);
        coverImage = itemView.findViewById(R.id.coverImage);
        count = itemView.findViewById(R.id.countViewPill);
        fallbackTitle = itemView.findViewById(R.id.fallbackTitleLabel);
        gradient = itemView.findViewById(R.id.gradientOverlay);
        selectIcon = itemView.findViewById(R.id.selectedIcon);
        card = itemView.findViewById(R.id.outerContainer);
        errorIcon = itemView.findViewById(R.id.errorIcon);
        this.mainActivityRef = new WeakReference<>(mainActivity);
    }

    public void bind(Feed feed, int columnCount, int counter) {
        title.setText(feed.getTitle());
        fallbackTitle.setText(feed.getTitle());
        coverImage.setContentDescription(feed.getTitle());
        if (counter > 0) {
            count.setText(count.getContext().getString(R.string.podcast_new_count, counter));
            count.setVisibility(View.VISIBLE);
        } else {
            count.setVisibility(View.GONE);
        }

        CoverLoader coverLoader = new CoverLoader();
        boolean textAndImageCombined = feed.isLocalFeed() && feed.getImageUrl() != null
                && feed.getImageUrl().startsWith(Feed.PREFIX_GENERATIVE_COVER);
        coverLoader.withUri(feed.getImageUrl());
        errorIcon.setVisibility(feed.hasLastUpdateFailed() ? View.VISIBLE : View.GONE);

        if (columnCount == 1) {
            // No need for fallback title when already showing title
            fallbackTitle.setVisibility(View.GONE);
        } else {
            coverLoader.withPlaceholderView(fallbackTitle, textAndImageCombined);
        }
        coverLoader.withCoverView(coverImage);
        coverLoader.load();

        if (card != null) {
            card.setCardBackgroundColor(Color.TRANSPARENT);
        }

        int textPadding = columnCount <= 3 ? 16 : 8;
        fallbackTitle.setPadding(textPadding, textPadding, textPadding, textPadding);
        if (selectIcon == null) {
            title.setPadding(textPadding, textPadding, textPadding, textPadding);
        }

        int textSize = 14;
        if (columnCount == 3) {
            textSize = 15;
        } else if (columnCount == 2) {
            textSize = 16;
        }
        if (selectIcon == null) {
            title.setTextSize(textSize);
        }
        fallbackTitle.setTextSize(textSize);
    }
}