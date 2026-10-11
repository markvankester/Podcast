package de.danoeh.antennapod.ui.podcast;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PodcastSwipeHelper implements RecyclerView.OnItemTouchListener {
    public interface Callback {
        boolean canSwipe(RecyclerView.ViewHolder holder);

        Object getItem(RecyclerView.ViewHolder holder);

        boolean canDismissInDirection(Object item, boolean toRight);

        void onDismissed(Object item, boolean toRight);

        default void onSwipeStarted(RecyclerView.ViewHolder holder) {
        }

        default void onSwipedWithoutDismiss(Object item, boolean toRight) {
        }
    }

    private static final float ESCAPE_VELOCITY_DP = 500f;
    private static final float MAX_VELOCITY_DP = 4000f;
    private static final float MIN_FLING_VELOCITY_DP = 250f;
    private static final float HIGH_FLING_VELOCITY_DP = 3000f;
    private static final float MAX_FLING_SECONDS = 0.4f;
    private static final long DEFAULT_ESCAPE_DURATION = 200;
    private static final long MAX_ESCAPE_DURATION = 400;
    private static final long MIN_ESCAPE_DURATION = 16;
    private static final float FADE_END = 0.6f;
    private static final float MAX_SCROLL_FRACTION = 0.3f;
    private static final float ACTION_FRACTION = 0.15f;
    static final float ATTACHED_RATIO = 0.35f;
    static final float DETACH_FRACTION = 0.25f;
    static final float REATTACH_FRACTION = 0.18f;
    static final float CATCH_UP_DONE_PX = 4f;
    private static final float ATTACHED_STIFFNESS = 1500f;
    private static final float CATCH_UP_STIFFNESS = 3000f;
    private static final float TRACKING_STIFFNESS = 10000f;
    private static final float TRACKING_DAMPING = 1.0f;
    private static final float NEIGHBOUR_STIFFNESS = 800f;
    private static final float NEIGHBOUR_DAMPING = 0.9f;
    private static final float NEAR_NEIGHBOUR_RATIO = 0.5f;
    private static final float FAR_NEIGHBOUR_RATIO = 0.25f;
    private static final float SPRING_STIFFNESS = 200f;
    private static final float SPRING_DAMPING = 0.75f;
    private static final Interpolator LINEAR_OUT_SLOW_IN = new PathInterpolator(0f, 0f, 0.2f, 1f);
    private static final Interpolator FAST_OUT_LINEAR_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);

    private final Callback callback;
    private final Map<View, Runnable> running = new HashMap<>();
    private final Map<View, SpringAnimation> springs = new HashMap<>();
    private final Set<View> releasing = new HashSet<>();
    private final List<Neighbour> neighbours = new ArrayList<>();
    private float rowTarget;
    private boolean enabled = true;
    private VelocityTracker velocityTracker;
    private RecyclerView.ViewHolder holder;
    private Object item;
    private int pointerId;
    private float downX;
    private float downY;
    private boolean swiping = false;
    private boolean rejected = false;
    private boolean armed = false;
    private boolean detached = false;
    private boolean everDetached = false;
    private boolean catchingUp = false;

    private static class Neighbour {
        final RecyclerView.ViewHolder holder;
        final int position;
        final float factor;

        Neighbour(RecyclerView.ViewHolder holder, int position, float factor) {
            this.holder = holder;
            this.position = position;
            this.factor = factor;
        }
    }

    public PodcastSwipeHelper(Callback callback) {
        this.callback = callback;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static boolean isActive(View view) {
        return view.getTag(R.id.podcast_swipe_active) != null;
    }

    private static void setActive(View view, boolean active) {
        view.setTag(R.id.podcast_swipe_active, active ? Boolean.TRUE : null);
    }

    public static float resist(float delta, float size) {
        float sign = delta < 0 ? -1f : 1f;
        float abs = Math.abs(delta);
        if (abs >= size) {
            return sign * MAX_SCROLL_FRACTION * size;
        }
        return sign * MAX_SCROLL_FRACTION * size * (float) Math.sin((abs / size) * Math.PI / 2);
    }

    static float trackingStiffness(boolean detached, boolean catchingUp) {
        if (!detached) {
            return ATTACHED_STIFFNESS;
        }
        return catchingUp ? CATCH_UP_STIFFNESS : TRACKING_STIFFNESS;
    }

    static SpringAnimation trackSpring(SpringAnimation existing, View view, float target, float stiffness,
                                       float damping, Runnable onUpdate) {
        SpringAnimation spring = existing;
        if (spring == null) {
            spring = new SpringAnimation(view, DynamicAnimation.TRANSLATION_X, target);
            if (onUpdate != null) {
                spring.addUpdateListener((animation, value, v) -> onUpdate.run());
            }
        }
        spring.getSpring().setStiffness(stiffness).setDampingRatio(damping);
        spring.animateToFinalPosition(target);
        return spring;
    }

    public static float fadeAlpha(float translation, float width) {
        if (width <= 0) {
            return 1f;
        }
        return 1f - Math.min(1f, Math.abs(translation) / (FADE_END * width));
    }

    public static void applyFade(View view) {
        float width = view.getWidth();
        if (width <= 0) {
            return;
        }
        float progress = Math.min(1f, Math.abs(view.getTranslationX()) / width);
        view.setAlpha(fadeAlpha(view.getTranslationX(), width));
        view.setLayerType(progress > 0f && progress < 1f ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
    }

    public static float maxVelocity(View view) {
        return MAX_VELOCITY_DP * view.getResources().getDisplayMetrics().density;
    }

    public static Runnable release(View view, float velocity, boolean canDismiss, boolean detached,
                                   boolean everDetached, Runnable onUpdate, Runnable onDismissed,
                                   Runnable onSnapped) {
        float density = view.getResources().getDisplayMetrics().density;
        float translation = view.getTranslationX();
        float width = view.getWidth();
        boolean strong = Math.abs(velocity) > ESCAPE_VELOCITY_DP * density;
        boolean same = (velocity > 0) == (translation > 0);
        boolean fast = strong && same;
        boolean back = strong && !same;
        if (canDismiss && translation != 0f && (fast || (detached && !back))) {
            if (fast && !everDetached) {
                PodcastHaptics.confirm(view);
            }
            return flingOut(view, velocity, fast, translation < 0 ? -width : width, onUpdate, onDismissed);
        }
        return snapBack(view, velocity, onUpdate, onSnapped);
    }

    private static Runnable flingOut(View view, float velocity, boolean fast, float end, Runnable onUpdate,
                                     Runnable onDismissed) {
        float density = view.getResources().getDisplayMetrics().density;
        float translation = view.getTranslationX();
        float distance = Math.abs(end - translation);
        Interpolator interpolator;
        long duration;
        if (fast) {
            float velocityDp = Math.abs(velocity) / density;
            float maxSeconds = MAX_FLING_SECONDS * (float) Math.sqrt(distance / view.getWidth());
            float t = Math.max(0f, Math.min(1f, (velocityDp - MIN_FLING_VELOCITY_DP)
                    / (HIGH_FLING_VELOCITY_DP - MIN_FLING_VELOCITY_DP)));
            float y2 = 0.4f + (0.5f - 0.4f) * t;
            interpolator = new PathInterpolator(0f, 0f, 0.5f, y2);
            float seconds = (y2 / 0.5f) * distance / Math.abs(velocity);
            if (seconds > maxSeconds) {
                seconds = maxSeconds;
                interpolator = velocityDp >= MIN_FLING_VELOCITY_DP ? LINEAR_OUT_SLOW_IN : FAST_OUT_LINEAR_IN;
            }
            duration = Math.round(seconds * 1000f);
        } else {
            interpolator = FAST_OUT_LINEAR_IN;
            duration = velocity != 0f
                    ? Math.min(MAX_ESCAPE_DURATION, Math.round(distance * 1000f / Math.abs(velocity)))
                    : DEFAULT_ESCAPE_DURATION;
        }
        duration = Math.max(MIN_ESCAPE_DURATION, duration);
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, translation, end);
        animator.setInterpolator(interpolator);
        animator.setDuration(duration);
        animator.addUpdateListener(a -> onUpdate.run());
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    onUpdate.run();
                    onDismissed.run();
                }
            }
        });
        animator.start();
        return animator.isRunning() ? new FlingCanceller(animator) : null;
    }

    static class FlingCanceller implements Runnable {
        private final Animator animator;

        FlingCanceller(Animator animator) {
            this.animator = animator;
        }

        @Override
        public void run() {
            animator.cancel();
        }
    }

    public static Runnable snapBack(View view, float velocity, Runnable onUpdate, Runnable onSnapped) {
        SpringAnimation spring = new SpringAnimation(view, DynamicAnimation.TRANSLATION_X, 0f);
        spring.getSpring().setStiffness(SPRING_STIFFNESS).setDampingRatio(SPRING_DAMPING);
        spring.setStartVelocity(velocity);
        spring.addUpdateListener((animation, value, v) -> onUpdate.run());
        spring.addEndListener((animation, canceled, value, v) -> {
            if (!canceled) {
                view.setTranslationX(0f);
                onUpdate.run();
                onSnapped.run();
            }
        });
        spring.start();
        return spring::cancel;
    }

    private void stopRunning(View view) {
        releasing.remove(view);
        SpringAnimation spring = springs.remove(view);
        if (spring != null) {
            spring.cancel();
        }
        Runnable canceller = running.remove(view);
        if (canceller != null) {
            canceller.run();
        }
    }

    private void springBack(View view, float velocity) {
        stopRunning(view);
        setActive(view, true);
        running.put(view, snapBack(view, velocity, () -> applyFade(view), () -> {
            running.remove(view);
            setActive(view, false);
        }));
    }

    public void clear() {
        for (Map.Entry<View, Runnable> entry : new HashMap<>(running).entrySet()) {
            entry.getValue().run();
            resetView(entry.getKey());
        }
        running.clear();
        for (Map.Entry<View, SpringAnimation> entry : new HashMap<>(springs).entrySet()) {
            entry.getValue().cancel();
            resetView(entry.getKey());
        }
        springs.clear();
        releasing.clear();
        for (Neighbour neighbour : neighbours) {
            resetView(neighbour.holder.itemView);
        }
        neighbours.clear();
        if (swiping && holder != null) {
            resetView(holder.itemView);
        }
        reset();
    }

    private void resetView(View view) {
        view.setTranslationX(0f);
        view.setAlpha(1f);
        view.setLayerType(View.LAYER_TYPE_NONE, null);
        setActive(view, false);
    }

    private void resolveNeighbours(RecyclerView rv) {
        neighbours.clear();
        int position = holder.getBindingAdapterPosition();
        for (int offset = -2; offset <= 2; offset++) {
            if (offset == 0) {
                continue;
            }
            RecyclerView.ViewHolder neighbour = rv.findViewHolderForAdapterPosition(position + offset);
            if (neighbour == null || neighbour == holder || running.get(neighbour.itemView) instanceof FlingCanceller) {
                continue;
            }
            stopRunning(neighbour.itemView);
            setActive(neighbour.itemView, true);
            neighbours.add(new Neighbour(neighbour, position + offset,
                    Math.abs(offset) == 1 ? NEAR_NEIGHBOUR_RATIO : FAR_NEIGHBOUR_RATIO));
        }
    }

    private void trackNeighbour(View view, float target, float stiffness, float damping) {
        boolean created = !springs.containsKey(view);
        SpringAnimation spring = trackSpring(springs.get(view), view, target, stiffness, damping, null);
        if (created) {
            spring.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled && releasing.remove(view)) {
                    springs.remove(view);
                    view.setTranslationX(0f);
                    setActive(view, false);
                }
            });
            springs.put(view, spring);
        }
    }

    private void followNeighbours() {
        Iterator<Neighbour> iterator = neighbours.iterator();
        while (iterator.hasNext()) {
            Neighbour neighbour = iterator.next();
            View view = neighbour.holder.itemView;
            if (neighbour.holder.getBindingAdapterPosition() != neighbour.position) {
                stopRunning(view);
                resetView(view);
                iterator.remove();
                continue;
            }
            if (!detached) {
                trackNeighbour(view, neighbour.factor * rowTarget, NEIGHBOUR_STIFFNESS, NEIGHBOUR_DAMPING);
            }
        }
    }

    private void settleNeighbours() {
        for (Neighbour neighbour : neighbours) {
            trackNeighbour(neighbour.holder.itemView, 0f, SPRING_STIFFNESS, SPRING_DAMPING);
        }
    }

    private void releaseNeighbours() {
        for (Neighbour neighbour : neighbours) {
            View view = neighbour.holder.itemView;
            if (neighbour.holder.getBindingAdapterPosition() != neighbour.position) {
                stopRunning(view);
                resetView(view);
                continue;
            }
            releasing.add(view);
            trackNeighbour(view, 0f, SPRING_STIFFNESS, SPRING_DAMPING);
        }
        neighbours.clear();
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                reset();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(e);
                downX = e.getX();
                downY = e.getY();
                pointerId = e.getPointerId(0);
                View child = rv.findChildViewUnder(downX, downY);
                holder = child == null ? null : rv.getChildViewHolder(child);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (holder == null || rejected || !enabled || velocityTracker == null) {
                    return false;
                }
                velocityTracker.addMovement(e);
                int index = e.findPointerIndex(pointerId);
                if (index < 0) {
                    return false;
                }
                float dx = e.getX(index) - downX;
                float dy = e.getY(index) - downY;
                int slop = ViewConfiguration.get(rv.getContext()).getScaledTouchSlop();
                if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) {
                    rejected = true;
                    return false;
                }
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                    if (rv.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING
                            || holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION
                            || running.get(holder.itemView) instanceof FlingCanceller
                            || !callback.canSwipe(holder)) {
                        rejected = true;
                        return false;
                    }
                    item = callback.getItem(holder);
                    if (item == null) {
                        rejected = true;
                        return false;
                    }
                    stopRunning(holder.itemView);
                    setActive(holder.itemView, true);
                    catchingUp = holder.itemView.getTranslationX() != 0f;
                    rowTarget = holder.itemView.getTranslationX();
                    resolveNeighbours(rv);
                    swiping = true;
                    rv.requestDisallowInterceptTouchEvent(true);
                    callback.onSwipeStarted(holder);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reset();
                return false;
            default:
                return false;
        }
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (!swiping || velocityTracker == null) {
            return;
        }
        velocityTracker.addMovement(e);
        View view = holder.itemView;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                int index = e.findPointerIndex(pointerId);
                if (index < 0) {
                    return;
                }
                if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) {
                    cancelSwipe();
                    return;
                }
                float width = view.getWidth();
                float fingerDelta = e.getX(index) - downX;
                float abs = Math.abs(fingerDelta);
                boolean canDismiss = callback.canDismissInDirection(item, fingerDelta > 0);
                float target;
                if (!canDismiss) {
                    detached = false;
                    target = resist(fingerDelta, width);
                } else {
                    if (!detached && abs >= DETACH_FRACTION * width) {
                        detached = true;
                        everDetached = true;
                        catchingUp = true;
                        PodcastHaptics.thresholdActivate(view);
                        settleNeighbours();
                    } else if (detached && abs < REATTACH_FRACTION * width) {
                        detached = false;
                        catchingUp = true;
                        PodcastHaptics.thresholdDeactivate(view);
                    }
                    target = detached ? fingerDelta : ATTACHED_RATIO * fingerDelta;
                }
                rowTarget = target;
                float stiffness = trackingStiffness(detached, catchingUp);
                float damping = TRACKING_DAMPING;
                if (detached && catchingUp && Math.abs(view.getTranslationX() - target) < CATCH_UP_DONE_PX) {
                    catchingUp = false;
                    stiffness = trackingStiffness(detached, catchingUp);
                }
                SpringAnimation spring = trackSpring(springs.get(view), view, target, stiffness, damping,
                        () -> applyFade(view));
                springs.put(view, spring);
                followNeighbours();
                boolean nowArmed = !canDismiss && Math.abs(target) > ACTION_FRACTION * width;
                if (nowArmed != armed) {
                    armed = nowArmed;
                    if (armed) {
                        PodcastHaptics.thresholdActivate(view);
                    } else {
                        PodcastHaptics.thresholdDeactivate(view);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finish(e.getActionMasked() == MotionEvent.ACTION_UP);
                break;
            default:
                break;
        }
    }

    private void cancelSwipe() {
        View view = holder.itemView;
        reset();
        releaseNeighbours();
        springBack(view, 0f);
    }

    private void finish(boolean up) {
        View view = holder.itemView;
        if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) {
            cancelSwipe();
            return;
        }
        stopRunning(view);
        Object swipedItem = item;
        final RecyclerView.ViewHolder swiped = holder;
        velocityTracker.computeCurrentVelocity(1000, maxVelocity(view));
        final float velocity = up ? velocityTracker.getXVelocity() : 0f;
        boolean wasArmed = armed;
        final boolean wasDetached = detached;
        final boolean wasEverDetached = everDetached;
        float translation = view.getTranslationX();
        boolean toRight = translation > 0;
        boolean canDismiss = callback.canDismissInDirection(swipedItem, toRight);
        reset();
        releaseNeighbours();
        if (up && !canDismiss && wasArmed) {
            callback.onSwipedWithoutDismiss(swipedItem, toRight);
        }
        Runnable canceller = release(view, velocity, up && canDismiss, wasDetached, wasEverDetached,
                () -> applyFade(view), () -> {
                    running.remove(view);
                    setActive(view, false);
                    callback.onDismissed(swipedItem, toRight);
                    view.postDelayed(() -> {
                        if (Math.abs(view.getTranslationX()) >= view.getWidth()
                                && swiped.getBindingAdapterPosition() != RecyclerView.NO_POSITION
                                && !running.containsKey(view)) {
                            springBack(view, 0f);
                        }
                    }, 1500);
                }, () -> {
                    running.remove(view);
                    setActive(view, false);
                });
        if (canceller != null) {
            running.put(view, canceller);
        }
    }

    private void reset() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        holder = null;
        item = null;
        swiping = false;
        rejected = false;
        armed = false;
        detached = false;
        everDetached = false;
        catchingUp = false;
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }
}
