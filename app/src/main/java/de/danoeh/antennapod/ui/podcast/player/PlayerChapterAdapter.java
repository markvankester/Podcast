package de.danoeh.antennapod.ui.podcast.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.podcast.PodcastHaptics;
import java.util.ArrayList;
import java.util.List;

class PlayerChapterAdapter extends RecyclerView.Adapter<PlayerChapterAdapter.Holder> {
    private final List<Chapter> chapters = new ArrayList<>();
    private final Consumer<Integer> clickListener;
    private int current = -1;

    PlayerChapterAdapter(Consumer<Integer> clickListener) {
        this.clickListener = clickListener;
    }

    void setChapters(List<Chapter> newChapters) {
        chapters.clear();
        if (newChapters != null) {
            chapters.addAll(newChapters);
        }
        notifyDataSetChanged();
    }

    int getCurrent() {
        return current;
    }

    void setCurrent(int index) {
        if (index == current) {
            return;
        }
        int old = current;
        current = index;
        if (old >= 0 && old < chapters.size()) {
            notifyItemChanged(old);
        }
        if (index >= 0 && index < chapters.size()) {
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.podcast_player_chapter_item, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Chapter chapter = chapters.get(position);
        boolean isCurrent = position == current;
        holder.time.setText(Converter.getDurationStringLong((int) chapter.getStart()));
        String title = chapter.getTitle();
        holder.name.setText(title == null || title.isEmpty()
                ? holder.itemView.getContext().getString(R.string.podcast_player_chapter_number, position + 1)
                : title);
        holder.name.setTypeface(ResourcesCompat.getFont(holder.name.getContext(),
                isCurrent ? R.font.manrope_extrabold : R.font.manrope_semibold));
        holder.indicator.setVisibility(isCurrent ? View.VISIBLE : View.INVISIBLE);
        holder.itemView.setBackgroundResource(isCurrent ? R.drawable.podcast_player_chapter_current : 0);
        holder.itemView.setOnClickListener(v -> {
            PodcastHaptics.tap(v);
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                clickListener.accept(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return chapters.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView name;
        final View indicator;

        Holder(View view) {
            super(view);
            time = view.findViewById(R.id.podcast_chapter_time);
            name = view.findViewById(R.id.podcast_chapter_name);
            indicator = view.findViewById(R.id.podcast_chapter_current);
        }
    }
}
