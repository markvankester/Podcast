package de.danoeh.antennapod.ui.podcast.player;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

public class PlayerContentLayout extends FrameLayout {
    public interface SwipeHost {
        boolean canSwipeUp();

        void onSwipeUpStarted();
    }

    private final int touchSlop;
    private final Rect hitRect = new Rect();
    private SwipeHost host;
    private PlayerPanelLayer panelLayer;
    private View excludedView;
    private float downX;
    private float downY;
    private boolean tracking = false;
    private boolean swiping = false;
    private VelocityTracker velocityTracker;

    public PlayerContentLayout(Context context) {
        this(context, null);
    }

    public PlayerContentLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerContentLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void bind(SwipeHost host, PlayerPanelLayer panelLayer, View excludedView) {
        this.host = host;
        this.panelLayer = panelLayer;
        this.excludedView = excludedView;
    }

    private boolean isInExcluded(MotionEvent event) {
        if (excludedView == null || excludedView.getVisibility() != VISIBLE) {
            return false;
        }
        excludedView.getGlobalVisibleRect(hitRect);
        int[] location = new int[2];
        getLocationOnScreen(location);
        float rawX = event.getX() + location[0];
        float rawY = event.getY() + location[1];
        return hitRect.contains((int) rawX, (int) rawY);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (host == null || panelLayer == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                tracking = panelLayer.getVisibility() != VISIBLE && host.canSwipeUp() && !isInExcluded(event);
                swiping = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) {
                    return false;
                }
                float dy = event.getY() - downY;
                float dx = event.getX() - downX;
                if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    startSwipe(event);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void startSwipe(MotionEvent event) {
        swiping = true;
        downY = event.getY();
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        host.onSwipeUpStarted();
        panelLayer.beginExternalDrag();
        if (velocityTracker != null) {
            velocityTracker.recycle();
        }
        velocityTracker = VelocityTracker.obtain();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!swiping) {
            if (!tracking || host == null) {
                return super.onTouchEvent(event);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dy = event.getY() - downY;
                float dx = event.getX() - downX;
                if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    startSwipe(event);
                }
            }
            return true;
        }
        velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                panelLayer.dragExternal(downY - event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.computeCurrentVelocity(1000);
                float velocity = velocityTracker.getYVelocity();
                velocityTracker.recycle();
                velocityTracker = null;
                swiping = false;
                tracking = false;
                panelLayer.endExternalDrag(velocity);
                return true;
            default:
                return true;
        }
    }
}
