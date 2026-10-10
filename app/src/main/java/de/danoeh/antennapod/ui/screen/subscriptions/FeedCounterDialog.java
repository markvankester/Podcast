package de.danoeh.antennapod.ui.screen.subscriptions;

import android.content.Context;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.ui.podcast.PodcastPopupMenu;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FeedCounterDialog {
    public static void showDialog(Context context) {
        int selected = UserPreferences.getFeedCounterSetting().id;
        List<String> entryValues =
                Arrays.asList(context.getResources().getStringArray(R.array.nav_drawer_feed_counter_values));
        final int selectedIndex = entryValues.indexOf("" + selected);

        String[] items = context.getResources().getStringArray(R.array.nav_drawer_feed_counter_options);
        PodcastPopupMenu.showChoiceSheet(context, context.getString(R.string.pref_nav_drawer_feed_counter_title),
                items, selectedIndex, which -> {
            if (selectedIndex != which) {
                UserPreferences.setFeedCounterSetting(
                        FeedCounter.fromOrdinal(Integer.parseInt(entryValues.get(which))));
                //Update subscriptions
                EventBus.getDefault().post(new FeedItemEvent(Collections.emptyList(), true));
            }
        });
    }
}
