package de.danoeh.antennapod.ui.podcast.player;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class PlayerQueueAdapter extends RecyclerView.Adapter<PlayerQueueAdapter.Holder> {
    interface Listener {
        void onPlay(FeedItem item);

        void onRemove(FeedItem item);

        void onStartDrag(RecyclerView.ViewHolder holder);
    }

    private final List<FeedItem> items = new ArrayList<>();
    private final Listener listener;

    PlayerQueueAdapter(Listener listener) {
        this.listener = listener;
    }

    void setItems(List<FeedItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    List<FeedItem> getItems() {
        return items;
    }

    void move(int from, int to) {
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
    }

    void remove(FeedItem item) {
        int index = items.indexOf(item);
        if (index >= 0) {
            items.remove(index);
            notifyItemRemoved(index);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.podcast_player_queue_item, parent, false);
        return new Holder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FeedItem item = items.get(position);
        holder.title.setText(item.getTitle());
        FeedMedia media = item.getMedia();
        String show = item.getFeed() != null ? item.getFeed().getTitle() : "";
        String length = media != null && media.getDuration() > 0
                ? Converter.getDurationStringLocalized(holder.itemView.getContext(), media.getDuration()) : "";
        holder.subtitle.setText(length.isEmpty() ? show : show + " · " + length);
        Glide.with(holder.cover)
                .load(ImageResourceUtils.getEpisodeListImageLocation(item))
                .error(Glide.with(holder.cover).load(ImageResourceUtils.getFallbackImageLocation(item)))
                .centerCrop()
                .dontAnimate()
                .into(holder.cover);
        holder.itemView.setOnClickListener(v -> listener.onPlay(item));
        holder.remove.setOnClickListener(v -> listener.onRemove(item));
        holder.drag.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                listener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title;
        final TextView subtitle;
        final View remove;
        final View drag;

        Holder(View view) {
            super(view);
            cover = view.findViewById(R.id.podcast_queue_cover);
            title = view.findViewById(R.id.podcast_queue_title);
            subtitle = view.findViewById(R.id.podcast_queue_subtitle);
            remove = view.findViewById(R.id.podcast_queue_remove);
            drag = view.findViewById(R.id.podcast_queue_drag);
        }
    }
}
