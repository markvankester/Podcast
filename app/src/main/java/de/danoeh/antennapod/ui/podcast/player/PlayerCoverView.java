package de.danoeh.antennapod.ui.podcast.player;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class PlayerCoverView extends AppCompatImageView {
    public PlayerCoverView(Context context) {
        super(context);
    }

    public PlayerCoverView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PlayerCoverView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }
    }
}
