package de.danoeh.antennapod.ui.podcast;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.dynamicanimation.animation.SpringAnimation;
import de.danoeh.antennapod.R;

public class PillDismissLayout extends FrameLayout {
    private final int touchSlop;
    private Runnable onDismissed;
    private VelocityTracker velocityTracker;
    private float downX;
    private float downY;
    private boolean dragging = false;
    private boolean detached = false;
    private boolean everDetached = false;
    private boolean catchingUp = false;
    private boolean dismissing = false;
    private Runnable canceller;
    private SpringAnimation tracking;
    private boolean blocked = false;

    public PillDismissLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnDismissed(Runnable onDismissed) {
        this.onDismissed = onDismissed;
    }

    public void dismiss() {
        if (dismissing) {
            return;
        }
        dismissing = true;
        animate().translationX(-getWidth()).setDuration(200)
                .setInterpolator(null).setUpdateListener(a -> applyFade()).withEndAction(this::finishDismiss).start();
    }

    private void applyFade() {
        View glass = findViewById(R.id.fragmentLayout);
        if (!(glass instanceof ViewGroup)) {
            return;
        }
        float alpha = PodcastSwipeHelper.fadeAlpha(getTranslationX(), getWidth());
        ViewGroup group = (ViewGroup) glass;
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setAlpha(alpha);
        }
    }

    private void finishDismiss() {
        canceller = null;
        dismissing = false;
        if (onDismissed != null) {
            onDismissed.run();
        }
        setTranslationX(0f);
        applyFade();
    }

    private void onSnapped() {
        canceller = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animate().cancel();
        cancelRunning();
        stopTracking();
        dismissing = false;
        dragging = false;
        setTranslationX(0f);
        applyFade();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (dismissing) {
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            blocked = canceller instanceof PodcastSwipeHelper.FlingCanceller;
            if (!blocked) {
                cancelRunning();
            }
            recycleTracker();
            velocityTracker = VelocityTracker.obtain();
            downX = event.getRawX();
            downY = event.getRawY();
            dragging = false;
        }
        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !dragging && !blocked) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                dragging = true;
                catchingUp = getTranslationX() != 0f;
                detached = false;
                everDetached = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (dismissing) {
            return true;
        }
        if (!dragging) {
            return super.onTouchEvent(event);
        }
        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float width = getWidth();
                float fingerDelta = event.getRawX() - downX;
                float abs = Math.abs(fingerDelta);
                if (!detached && abs >= PodcastSwipeHelper.DETACH_FRACTION * width) {
                    detached = true;
                    everDetached = true;
                    catchingUp = true;
                    PodcastHaptics.thresholdActivate(this);
                } else if (detached && abs < PodcastSwipeHelper.REATTACH_FRACTION * width) {
                    detached = false;
                    catchingUp = true;
                    PodcastHaptics.thresholdDeactivate(this);
                }
                float target = detached ? fingerDelta : PodcastSwipeHelper.ATTACHED_RATIO * fingerDelta;
                if (detached && catchingUp
                        && Math.abs(getTranslationX() - target) < PodcastSwipeHelper.CATCH_UP_DONE_PX) {
                    catchingUp = false;
                }
                tracking = PodcastSwipeHelper.trackSpring(tracking, this, target,
                        PodcastSwipeHelper.trackingStiffness(detached, catchingUp), 1.0f, this::applyFade);
                return true;
            case MotionEvent.ACTION_UP:
                velocityTracker.computeCurrentVelocity(1000, PodcastSwipeHelper.maxVelocity(this));
                finishGesture(true, velocityTracker.getXVelocity());
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishGesture(false, 0f);
                return true;
            default:
                return true;
        }
    }

    private void finishGesture(boolean up, float velocity) {
        dragging = false;
        final boolean wasDetached = detached;
        final boolean wasEverDetached = everDetached;
        detached = false;
        everDetached = false;
        catchingUp = false;
        stopTracking();
        recycleTracker();
        canceller = PodcastSwipeHelper.release(this, velocity, up, wasDetached, wasEverDetached,
                this::applyFade, this::finishDismiss, this::onSnapped);
    }

    private void cancelRunning() {
        if (canceller != null) {
            canceller.run();
            canceller = null;
        }
    }

    private void stopTracking() {
        if (tracking != null) {
            tracking.cancel();
            tracking = null;
        }
    }

    private void recycleTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }
}
