package de.danoeh.antennapod.ui.podcast.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import de.danoeh.antennapod.ui.podcast.PodcastMetrics;

public class PlayerPanelLayer extends FrameLayout {
    private static final int PANEL_HEIGHT_DP = 620;
    private static final int TOP_GAP_DP = 24;
    private static final long SETTLE_MS = 260;

    public interface Listener {
        void onProgress(float progress);

        void onSettled(boolean open);
    }

    private final int touchSlop;
    private final Rect hitRect = new Rect();
    private Listener listener;
    private View scrim;
    private View panel;
    private float progress = 0f;
    private float downX;
    private float downY;
    private float dragStartProgress;
    private boolean dragging = false;
    private boolean moved = false;
    private int topInset = 0;
    private VelocityTracker velocityTracker;
    private ValueAnimator animator;

    public PlayerPanelLayer(Context context) {
        this(context, null);
    }

    public PlayerPanelLayer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerPanelLayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void bind(View scrim, View panel, Listener listener) {
        this.scrim = scrim;
        this.panel = panel;
        this.listener = listener;
        applyProgress(0f);
    }

    public void setTopInset(int inset) {
        topInset = inset;
        requestLayout();
    }

    public boolean isOpen() {
        return progress > 0.5f;
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (panel != null) {
            int available = MeasureSpec.getSize(heightMeasureSpec) - topInset
                    - PodcastMetrics.dp(getContext(), TOP_GAP_DP);
            int height = Math.min(PodcastMetrics.dp(getContext(), PANEL_HEIGHT_DP), Math.max(0, available));
            ViewGroup.LayoutParams params = panel.getLayoutParams();
            if (params.height != height && height > 0) {
                params.height = height;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        applyProgress(progress);
    }

    public void applyProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        if (panel == null || scrim == null) {
            return;
        }
        int height = panel.getHeight() > 0 ? panel.getHeight() : PodcastMetrics.dp(getContext(), PANEL_HEIGHT_DP);
        panel.setTranslationY((1f - progress) * height);
        scrim.setAlpha(progress);
        setVisibility(progress <= 0.001f && !dragging && !isAnimating() ? GONE : VISIBLE);
        if (listener != null) {
            listener.onProgress(progress);
        }
    }

    private boolean isAnimating() {
        return animator != null && animator.isRunning();
    }

    public void settle(boolean open, boolean animate) {
        if (animator != null) {
            animator.cancel();
        }
        setVisibility(VISIBLE);
        float target = open ? 1f : 0f;
        if (!animate) {
            dragging = false;
            applyProgress(target);
            if (listener != null) {
                listener.onSettled(open);
            }
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(SETTLE_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> applyProgress((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                dragging = false;
                applyProgress(target);
                if (listener != null) {
                    listener.onSettled(open);
                }
            }
        });
        animator.start();
    }

    public void beginExternalDrag() {
        if (animator != null) {
            animator.cancel();
        }
        dragging = true;
        dragStartProgress = progress;
        setVisibility(VISIBLE);
    }

    public void dragExternal(float panelDeltaUp) {
        int height = Math.max(1, panel.getHeight() > 0 ? panel.getHeight()
                : PodcastMetrics.dp(getContext(), PANEL_HEIGHT_DP));
        applyProgress(dragStartProgress + panelDeltaUp / height);
    }

    public void endExternalDrag(float velocityY) {
        dragging = false;
        boolean open;
        if (Math.abs(velocityY) > 900) {
            open = velocityY < 0;
        } else {
            open = progress > 0.5f;
        }
        settle(open, true);
    }

    private boolean canScrollUp(View view, float x, float y) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != VISIBLE) {
                    continue;
                }
                child.getHitRect(hitRect);
                float childX = x + view.getScrollX();
                float childY = y + view.getScrollY();
                if (hitRect.contains((int) childX, (int) childY)
                        && canScrollUp(child, childX - child.getLeft(), childY - child.getTop())) {
                    return true;
                }
            }
        }
        return view.canScrollVertically(-1);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (progress < 0.99f && !dragging) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                moved = false;
                if (animator != null) {
                    animator.cancel();
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getY() - downY;
                float dx = event.getX() - downX;
                if (dy > touchSlop && Math.abs(dy) > Math.abs(dx) * 1.2f
                        && !canScrollUp(panel, downX - panel.getLeft(),
                        downY - panel.getTop() - panel.getTranslationY())) {
                    startPanelDrag(event);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void startPanelDrag(MotionEvent event) {
        dragging = true;
        dragStartProgress = progress;
        downY = event.getY();
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (velocityTracker != null) {
            velocityTracker.recycle();
        }
        velocityTracker = VelocityTracker.obtain();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (progress < 0.01f && !dragging) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    velocityTracker.addMovement(event);
                    dragExternal(-(event.getY() - downY));
                    moved = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    velocityTracker.addMovement(event);
                    velocityTracker.computeCurrentVelocity(1000);
                    float velocity = velocityTracker.getYVelocity();
                    velocityTracker.recycle();
                    velocityTracker = null;
                    endExternalDrag(velocity);
                } else if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP
                        && event.getY() < panel.getTop() + panel.getTranslationY()) {
                    settle(false, true);
                }
                return true;
            default:
                return true;
        }
    }
}
