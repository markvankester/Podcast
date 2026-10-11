package de.danoeh.antennapod.ui.swipeactions;

import android.content.Context;

import androidx.fragment.app.Fragment;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import java.util.Collections;
import org.greenrobot.eventbus.EventBus;

public class RemoveFromFavoritesSwipeAction implements SwipeAction {

    @Override
    public String getId() {
        return REMOVE_FROM_FAVORITES;
    }

    @Override
    public int getActionIcon() {
        return R.drawable.ic_star_border;
    }

    @Override
    public int getActionColor() {
        return R.attr.icon_yellow;
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.remove_from_favorite_label);
    }

    @Override
    public void performAction(FeedItem item, Fragment fragment, FeedItemFilter filter) {
        DBWriter.toggleFavoriteItem(item);
        EventBus.getDefault().post(new MessageEvent(
                fragment.getResources().getQuantityString(R.plurals.removed_from_favorites_message, 1, 1),
                context -> DBWriter.addFavoriteItems(Collections.singletonList(item)),
                fragment.getString(R.string.undo)));
    }

    @Override
    public boolean willRemove(FeedItemFilter filter, FeedItem item) {
        return filter.showIsFavorite;
    }
}
