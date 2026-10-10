package de.danoeh.antennapod.ui.screen.subscriptions;

import android.content.Context;


import de.danoeh.antennapod.model.feed.FeedOrder;
import org.greenrobot.eventbus.EventBus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.ui.podcast.PodcastPopupMenu;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public class FeedSortDialog {
    public static void showDialog(Context context) {
        int selected = UserPreferences.getFeedOrder().id;
        List<String> entryValues =
                Arrays.asList(context.getResources().getStringArray(R.array.nav_drawer_feed_order_values));
        final int selectedIndex = entryValues.indexOf("" + selected);

        String[] items = context.getResources().getStringArray(R.array.nav_drawer_feed_order_options);
        PodcastPopupMenu.showChoiceSheet(context, context.getString(R.string.sort), items, selectedIndex, which -> {
            if (selectedIndex != which) {
                UserPreferences.setFeedOrder(FeedOrder.fromOrdinal(Integer.parseInt(entryValues.get(which))));
                //Update subscriptions
                EventBus.getDefault().post(new FeedItemEvent(Collections.emptyList(), true));
            }
        });
    }
}
