package de.danoeh.antennapod.ui.podcast.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.cleaner.ShownotesCleaner;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.DateFormatter;
import de.danoeh.antennapod.ui.common.IntentUtils;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;
import de.danoeh.antennapod.ui.view.ShownotesWebView;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;

public class PlayerPanel {
    private static final String TAG = "PlayerPanel";
    public static final int TAB_QUEUE = 0;
    public static final int TAB_CHAPTERS = 1;
    public static final int TAB_NOTES = 2;
    private static final String NOTES_STYLE = "<style>* { color: rgba(255,255,255,0.94) !important; } "
            + "a { color: #FFFFFF !important; text-decoration: underline; } "
            + "body { padding: 0 !important; font-size: 15px; "
            + "line-height: 1.6; }</style>";

    public interface Callbacks {
        void onTogglePlayback();

        void onPlayItem(FeedItem item);

        void onSeekToChapter(Chapter chapter);

        void onTimecodeSelected(int time);

        void onOpenStateChanged(boolean open);

        void onUpNextChanged(@Nullable FeedItem next);
    }

    private final Fragment fragment;
    private final Callbacks callbacks;
    private final PlayerPanelLayer layer;
    private final PlayerGlassLayout panel;
    private final TextView[] tabs = new TextView[3];
    private final RecyclerView queueList;
    private final RecyclerView chapterList;
    private final TextView emptyView;
    private final View notesView;
    private final ShownotesWebView webView;
    private final TextView chipDate;
    private final TextView chipLength;
    private final TextView visitLink;
    private final ImageView cover;
    private final TextView title;
    private final TextView subtitle;
    private final ImageButton playButton;
    private final PlayerQueueAdapter queueAdapter;
    private final PlayerChapterAdapter chapterAdapter;
    private final ItemTouchHelper touchHelper;
    private final List<FeedItem> fullQueue = new ArrayList<>();
    private int[] realIndices = new int[0];
    private int dragFrom = -1;
    private int dragTo = -1;
    private int tab = TAB_QUEUE;
    private int color = PlayerPalette.FALLBACK_COLOR;
    private boolean open = false;
    private boolean playing = false;
    private FeedMedia media;
    private List<Chapter> chapters;
    private Disposable queueDisposable;
    private Disposable notesDisposable;
    private long loadedNotesMediaId = -1;
    private String elapsedText = "";
    private String chapterText = "";

    public PlayerPanel(Fragment fragment, View root, Callbacks callbacks) {
        this.fragment = fragment;
        this.callbacks = callbacks;
        layer = root.findViewById(R.id.podcast_panel_layer);
        panel = root.findViewById(R.id.podcast_panel);
        tabs[TAB_QUEUE] = root.findViewById(R.id.podcast_panel_tab_queue);
        tabs[TAB_CHAPTERS] = root.findViewById(R.id.podcast_panel_tab_chapters);
        tabs[TAB_NOTES] = root.findViewById(R.id.podcast_panel_tab_notes);
        queueList = root.findViewById(R.id.podcast_panel_queue_list);
        chapterList = root.findViewById(R.id.podcast_panel_chapter_list);
        emptyView = root.findViewById(R.id.podcast_panel_empty);
        notesView = root.findViewById(R.id.podcast_panel_notes);
        webView = root.findViewById(R.id.podcast_panel_webview);
        chipDate = root.findViewById(R.id.podcast_panel_chip_date);
        chipLength = root.findViewById(R.id.podcast_panel_chip_length);
        visitLink = root.findViewById(R.id.podcast_panel_visit);
        cover = root.findViewById(R.id.podcast_panel_cover);
        title = root.findViewById(R.id.podcast_panel_title);
        subtitle = root.findViewById(R.id.podcast_panel_subtitle);
        playButton = root.findViewById(R.id.podcast_panel_play);

        layer.bind(root.findViewById(R.id.podcast_panel_scrim), panel, new PlayerPanelLayer.Listener() {
            @Override
            public void onProgress(float progress) {
            }

            @Override
            public void onSettled(boolean isOpen) {
                setOpenState(isOpen);
            }
        });
        root.findViewById(R.id.podcast_panel_handle).setOnClickListener(v -> close(true));
        playButton.setOnClickListener(v -> callbacks.onTogglePlayback());
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> selectTab(index));
        }

        queueAdapter = new PlayerQueueAdapter(new PlayerQueueAdapter.Listener() {
            @Override
            public void onPlay(FeedItem item) {
                callbacks.onPlayItem(item);
            }

            @Override
            public void onRemove(FeedItem item) {
                queueAdapter.remove(item);
                DBWriter.removeQueueItem(fragment.requireContext(), true, item);
                updateEmptyState();
            }

            @Override
            public void onStartDrag(RecyclerView.ViewHolder holder) {
                touchHelper.startDrag(holder);
            }
        });
        queueList.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        queueList.setAdapter(queueAdapter);
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (dragFrom == -1) {
                    dragFrom = from;
                }
                dragTo = to;
                queueAdapter.move(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo && dragFrom < realIndices.length
                        && dragTo < realIndices.length) {
                    DBWriter.moveQueueItem(realIndices[dragFrom], realIndices[dragTo], true);
                }
                dragFrom = -1;
                dragTo = -1;
            }
        });
        touchHelper.attachToRecyclerView(queueList);

        chapterAdapter = new PlayerChapterAdapter(position -> {
            if (chapters != null && position < chapters.size()) {
                callbacks.onSeekToChapter(chapters.get(position));
            }
        });
        chapterList.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        chapterList.setAdapter(chapterAdapter);

        webView.setTimecodeSelectedListener(callbacks::onTimecodeSelected);
        visitLink.setOnClickListener(v -> {
            if (media != null && media.getItem() != null) {
                IntentUtils.openInBrowser(fragment.requireContext(), media.getItem().getLinkWithFallback());
            }
        });
        applyTabState();
    }

    public boolean isOpen() {
        return open;
    }

    public void open(int tabIndex) {
        selectTab(tabIndex);
        layer.settle(true, true);
    }

    public void close(boolean animate) {
        layer.settle(false, animate);
    }

    public void closeImmediately() {
        if (layer.getVisibility() == View.VISIBLE || open) {
            layer.settle(false, false);
            setOpenState(false);
        }
    }

    public void setBottomInset(int inset) {
        View content = panel.findViewById(R.id.podcast_panel_content);
        content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(),
                dp(20) + inset);
    }

    public PlayerPanelLayer getLayer() {
        return layer;
    }

    private void setOpenState(boolean isOpen) {
        if (open == isOpen) {
            return;
        }
        open = isOpen;
        callbacks.onOpenStateChanged(isOpen);
    }

    public void onSwipeStarted() {
        setOpenState(true);
    }

    public void selectTab(int index) {
        tab = index;
        applyTabState();
        if (index == TAB_NOTES) {
            loadNotes();
        }
    }

    private void applyTabState() {
        queueList.setVisibility(tab == TAB_QUEUE ? View.VISIBLE : View.GONE);
        chapterList.setVisibility(tab == TAB_CHAPTERS ? View.VISIBLE : View.GONE);
        notesView.setVisibility(tab == TAB_NOTES ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == tab;
            tabs[i].setBackgroundResource(selected ? R.drawable.podcast_player_segment_selected : 0);
            tabs[i].setTextColor(selected ? color : Color.WHITE);
            tabs[i].setSelected(selected);
        }
        updateEmptyState();
        if (tab == TAB_CHAPTERS && chapterAdapter.getCurrent() >= 0) {
            ((LinearLayoutManager) chapterList.getLayoutManager())
                    .scrollToPositionWithOffset(chapterAdapter.getCurrent(), 0);
        }
    }

    private void updateEmptyState() {
        String message = null;
        if (tab == TAB_QUEUE && queueAdapter.getItemCount() == 0) {
            message = fragment.getString(R.string.podcast_player_queue_empty);
        } else if (tab == TAB_CHAPTERS && chapterAdapter.getItemCount() == 0) {
            message = fragment.getString(R.string.podcast_player_no_chapters);
        }
        emptyView.setVisibility(message == null ? View.GONE : View.VISIBLE);
        emptyView.setText(message);
    }

    public void setColor(@ColorInt int newColor) {
        color = newColor;
        panel.setBaseColor(newColor);
        playButton.setImageTintList(ColorStateList.valueOf(newColor));
        tabs[tab].setTextColor(newColor);
    }

    public void setBlurBitmap(Bitmap bitmap) {
        panel.setBlurBitmap(bitmap);
    }

    public void setPlaying(boolean isPlaying) {
        playing = isPlaying;
        playButton.setImageResource(playing ? R.drawable.podcast_player_pause : R.drawable.podcast_player_play);
        playButton.setContentDescription(fragment.getString(playing ? R.string.pause_label : R.string.play_label));
        playButton.setPadding(playing ? dp(13) : dp(14), dp(13), dp(13), dp(13));
    }

    private int dp(int value) {
        return Math.round(value * fragment.getResources().getDisplayMetrics().density);
    }

    public void bindMedia(@NonNull FeedMedia newMedia) {
        boolean changed = media == null || media.getId() != newMedia.getId();
        media = newMedia;
        title.setText(newMedia.getEpisodeTitle());
        if (changed) {
            Glide.with(cover)
                    .load(ImageResourceUtils.getEpisodeListImageLocation(newMedia))
                    .error(Glide.with(cover).load(ImageResourceUtils.getFallbackImageLocation(newMedia)))
                    .centerCrop()
                    .dontAnimate()
                    .into(cover);
            loadedNotesMediaId = -1;
            chipDate.setText(newMedia.getItem() != null && newMedia.getItem().getPubDate() != null
                    ? DateFormatter.formatAbbrev(fragment.requireContext(), newMedia.getItem().getPubDate()) : "");
            chipDate.setVisibility(TextUtils.isEmpty(chipDate.getText()) ? View.GONE : View.VISIBLE);
            reloadQueue();
            if (tab == TAB_NOTES && isOpen()) {
                loadNotes();
            }
        }
        chipLength.setText(newMedia.getDuration() > 0
                ? Converter.getDurationStringLocalized(fragment.requireContext(), newMedia.getDuration()) : "");
        chipLength.setVisibility(TextUtils.isEmpty(chipLength.getText()) ? View.GONE : View.VISIBLE);
        chapters = newMedia.getChapters();
        if (chapters == null || chapters.size() != chapterAdapter.getItemCount()) {
            chapterAdapter.setChapters(chapters);
            updateEmptyState();
        }
    }

    public void updateProgress(String elapsed, @Nullable String chapterName, int chapterIndex) {
        elapsedText = elapsed;
        chapterText = chapterName == null ? "" : chapterName;
        subtitle.setText(chapterText.isEmpty() ? elapsedText
                : fragment.getString(R.string.podcast_player_elapsed_chapter, elapsedText, chapterText));
        if (chapterIndex != chapterAdapter.getCurrent()) {
            chapterAdapter.setCurrent(chapterIndex);
            if (tab == TAB_CHAPTERS && isOpen() && chapterIndex >= 0
                    && chapterList.getLayoutManager() instanceof LinearLayoutManager) {
                ((LinearLayoutManager) chapterList.getLayoutManager()).scrollToPositionWithOffset(chapterIndex, 0);
            }
        }
    }

    public void reloadQueue() {
        if (queueDisposable != null) {
            queueDisposable.dispose();
        }
        queueDisposable = Maybe.<List<FeedItem>>fromCallable(DBReader::getQueue)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onQueueLoaded, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void onQueueLoaded(List<FeedItem> queue) {
        fullQueue.clear();
        fullQueue.addAll(queue);
        long currentId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        List<FeedItem> display = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            FeedItem item = queue.get(i);
            if (item.getMedia() != null && item.getMedia().getId() == currentId) {
                continue;
            }
            display.add(item);
            indices.add(i);
        }
        realIndices = new int[indices.size()];
        for (int i = 0; i < realIndices.length; i++) {
            realIndices[i] = indices.get(i);
        }
        if (dragFrom == -1) {
            queueAdapter.setItems(display);
        }
        updateEmptyState();
        callbacks.onUpNextChanged(display.isEmpty() ? null : display.get(0));
    }

    private void loadNotes() {
        if (media == null || loadedNotesMediaId == media.getId()) {
            return;
        }
        final long mediaId = media.getId();
        loadedNotesMediaId = mediaId;
        if (notesDisposable != null) {
            notesDisposable.dispose();
        }
        final Context darkContext = new ContextThemeWrapper(fragment.requireContext(), R.style.Theme_AntennaPod_Dark);
        notesDisposable = Maybe.<String>fromCallable(() -> {
            FeedMedia loaded = DBReader.getFeedMedia(mediaId);
            if (loaded == null) {
                return "";
            }
            if (loaded.getItem() == null) {
                loaded.setItem(DBReader.getFeedItem(loaded.getItemId()));
            }
            DBReader.loadDescriptionOfFeedItem(loaded.getItem());
            ShownotesCleaner cleaner = new ShownotesCleaner(darkContext, loaded.getDescription(),
                    loaded.getDuration());
            return cleaner.processShownotes() + NOTES_STYLE;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> webView.loadDataWithBaseURL("https://127.0.0.1", data, "text/html", "utf-8",
                        "about:blank"), error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    public void onDestroyView() {
        if (queueDisposable != null) {
            queueDisposable.dispose();
        }
        if (notesDisposable != null) {
            notesDisposable.dispose();
        }
        webView.removeAllViews();
        webView.destroy();
    }
}
