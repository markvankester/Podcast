package de.danoeh.antennapod.ui.swipeactions;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.ui.screen.AllEpisodesFragment;
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment;
import de.danoeh.antennapod.ui.screen.FavoritesFragment;
import de.danoeh.antennapod.ui.screen.InboxFragment;
import de.danoeh.antennapod.ui.screen.PlaybackHistoryFragment;
import de.danoeh.antennapod.ui.screen.queue.QueueFragment;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder;
import de.danoeh.antennapod.ui.podcast.PodcastSwipeHelper;

public class SwipeActions extends ItemTouchHelper.SimpleCallback implements LifecycleObserver {
    public static final String PREF_NAME = "SwipeActionsPrefs";
    public static final String KEY_PREFIX_SWIPEACTIONS = "PrefSwipeActions";
    public static final String KEY_PREFIX_NO_ACTION = "PrefNoSwipeAction";

    private static final List<SwipeAction> swipeActions = Collections.unmodifiableList(
            Arrays.asList(new AddToQueueSwipeAction(), new RemoveFromInboxSwipeAction(),
                    new StartDownloadSwipeAction(), new MarkFavoriteSwipeAction(),
                    new RemoveFromFavoritesSwipeAction(), new TogglePlaybackStateSwipeAction(),
                    new RemoveFromQueueSwipeAction(), new DeleteSwipeAction(),
                    new RemoveFromHistorySwipeAction(), new MoveToTopSwipeAction(),
                    new MoveToBottomSwipeAction(), new ShareSwipeAction()));

    private final Fragment fragment;
    private final String tag;
    private FeedItemFilter filter = null;

    Actions actions;
    private boolean dragging = false;
    private RecyclerView attachedRecyclerView;
    private PodcastSwipeHelper swipeHelper;
    private final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(this);

    public SwipeActions(int dragDirs, Fragment fragment, String tag) {
        super(dragDirs, 0);
        this.fragment = fragment;
        this.tag = tag;
        reloadPreference();
        fragment.getLifecycle().addObserver(this);
    }

    public SwipeActions(Fragment fragment, String tag) {
        this(0, fragment, tag);
    }

    public static SwipeAction getAction(String key) {
        for (SwipeAction action : swipeActions) {
            if (action.getId().equals(key)) {
                return action;
            }
        }
        return null;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void reloadPreference() {
        actions = getPrefsWithDefaults(fragment.requireContext(), tag);
    }

    public void setFilter(FeedItemFilter filter) {
        this.filter = filter;
    }

    public SwipeActions attachTo(RecyclerView recyclerView) {
        removeSwipeHelper();
        itemTouchHelper.attachToRecyclerView(recyclerView);
        attachedRecyclerView = recyclerView;
        swipeHelper = new PodcastSwipeHelper(
                new PodcastSwipeHelper.Callback() {
                    @Override
                    public boolean canSwipe(RecyclerView.ViewHolder holder) {
                        return !dragging && isSwipeActionEnabled() && holder instanceof EpisodeItemViewHolder
                                && ((EpisodeItemViewHolder) holder).getFeedItem() != null;
                    }

                    @Override
                    public Object getItem(RecyclerView.ViewHolder holder) {
                        return holder instanceof EpisodeItemViewHolder
                                ? ((EpisodeItemViewHolder) holder).getFeedItem() : null;
                    }

                    @Override
                    public boolean canDismissInDirection(Object item, boolean toRight) {
                        return item instanceof FeedItem
                                && getActionForDirection(toRight).willRemove(filter, (FeedItem) item);
                    }

                    @Override
                    public void onDismissed(Object item, boolean toRight) {
                        onItemSwiped((FeedItem) item, toRight ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT);
                    }

                    @Override
                    public void onSwipedWithoutDismiss(Object item, boolean toRight) {
                        onItemSwiped((FeedItem) item, toRight ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT);
                    }
                });
        recyclerView.addOnItemTouchListener(swipeHelper);
        return this;
    }

    public void detach() {
        itemTouchHelper.attachToRecyclerView(null);
        removeSwipeHelper();
    }

    private void removeSwipeHelper() {
        if (attachedRecyclerView != null && swipeHelper != null) {
            attachedRecyclerView.removeOnItemTouchListener(swipeHelper);
            swipeHelper.clear();
        }
        swipeHelper = null;
    }

    private SwipeAction getActionForDirection(boolean toRight) {
        if (!actions.hasActions()) {
            return new ShowFirstSwipeDialogAction();
        }
        return toRight ? actions.right : actions.left;
    }

    private static Actions getPrefs(Context context, String tag, String defaultActions) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String prefsString = prefs.getString(KEY_PREFIX_SWIPEACTIONS + tag, defaultActions);

        return new Actions(prefsString);
    }

    private static Actions getPrefs(Context context, String tag) {
        return getPrefs(context, tag, "");
    }

    public static Actions getPrefsWithDefaults(Context context, String tag) {
        String defaultActions;
        switch (tag) {
            case InboxFragment.TAG:
                defaultActions = SwipeAction.REMOVE_FROM_INBOX + "," + SwipeAction.REMOVE_FROM_INBOX;
                break;
            case QueueFragment.TAG:
                defaultActions = SwipeAction.REMOVE_FROM_QUEUE + "," + SwipeAction.REMOVE_FROM_QUEUE;
                break;
            case CompletedDownloadsFragment.TAG:
                defaultActions = SwipeAction.DELETE + "," + SwipeAction.DELETE;
                break;
            case PlaybackHistoryFragment.TAG:
                defaultActions = SwipeAction.REMOVE_FROM_HISTORY + "," + SwipeAction.REMOVE_FROM_HISTORY;
                break;
            case FavoritesFragment.TAG:
                defaultActions = SwipeAction.REMOVE_FROM_FAVORITES + "," + SwipeAction.REMOVE_FROM_FAVORITES;
                break;
            default:
            case AllEpisodesFragment.TAG:
                defaultActions = SwipeAction.MARK_FAV + "," + SwipeAction.START_DOWNLOAD;
                break;
        }

        return getPrefs(context, tag, defaultActions);
    }

    public static boolean isSwipeActionEnabled(Context context, String tag) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean defaultEnabled = QueueFragment.TAG.equals(tag) || InboxFragment.TAG.equals(tag)
                || CompletedDownloadsFragment.TAG.equals(tag) || PlaybackHistoryFragment.TAG.equals(tag)
                || FavoritesFragment.TAG.equals(tag);
        return prefs.getBoolean(KEY_PREFIX_NO_ACTION + tag, defaultEnabled);
    }

    private boolean isSwipeActionEnabled() {
        return isSwipeActionEnabled(fragment.requireContext(), tag);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int swipeDir) {
        onItemSwiped(((EpisodeItemViewHolder) viewHolder).getFeedItem(), swipeDir);
    }

    protected void onItemSwiped(FeedItem item, int swipeDir) {
        if (!actions.hasActions()) {
            //open settings dialog if no prefs are set
            new SwipeActionsDialog(fragment.requireContext(), tag).show(this::reloadPreference);
            return;
        }

        if (item == null) {
            return;
        }

        (swipeDir == ItemTouchHelper.RIGHT ? actions.right : actions.left)
                .performAction(item, fragment, filter);
    }

    @Override
    public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        dragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG;
    }

    public void startDrag(EpisodeItemViewHolder holder) {
        itemTouchHelper.startDrag(holder);
    }

    public static class Actions {
        public SwipeAction right = null;
        public SwipeAction left = null;

        public Actions(String prefs) {
            String[] actions = prefs.split(",");
            if (actions.length == 2) {
                right = getAction(actions[0]);
                left = getAction(actions[1]);
            }
        }

        public boolean hasActions() {
            return right != null && left != null;
        }
    }
}
