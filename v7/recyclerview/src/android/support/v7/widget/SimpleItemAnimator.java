package android.support.v7.widget;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.RecyclerView.ItemAnimator.ItemHolderInfo;
import android.util.Log;
import android.view.View;

/**
 * A wrapper class for ItemAnimator that records View bounds and decides whether it should run
 * move, change, add or remove animations. This class also replicates the original ItemAnimator
 * API.
 * <p>
 * It uses {@link ItemHolderInfo} to track the bounds information of the Views. If you would like
 * to
 * extend this class, you can override {@link #obtainHolderInfo()} method to provide your own info
 * class that extends {@link ItemHolderInfo}.
 */
abstract public class
        SimpleItemAnimator extends RecyclerView.ItemAnimator {

    private static final boolean DEBUG = false;

    private static final String TAG = "SimpleItemAnimator";

    boolean mSupportsChangeAnimations = true;

    /**
     * Returns whether this ItemAnimator supports animations of change events.
     *
     * @return true if change animations are supported, false otherwise
     */
    @SuppressWarnings("unused")
    public boolean getSupportsChangeAnimations() {
        return mSupportsChangeAnimations;
    }

    /**
     * Sets whether this ItemAnimator supports animations of item change events.
     * If you set this property to false, actions on the data set which change the
     * contents of items will not be animated. What those animations do is left
     * up to the discretion of the ItemAnimator subclass, in its
     * {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)} implementation.
     * The value of this property is true by default.
     *
     * @param supportsChangeAnimations true if change animations are supported by
     *                                 this ItemAnimator, false otherwise. If the property is false,
     *                                 the ItemAnimator
     *                                 will not receive a call to
     *                                 {@link #animateChange(ViewHolder, ViewHolder, int, int, int,
     *                                 int)} when changes occur.
     * @see Adapter#notifyItemChanged(int)
     * @see Adapter#notifyItemRangeChanged(int, int)
     */
    public void setSupportsChangeAnimations(boolean supportsChangeAnimations) {
        mSupportsChangeAnimations = supportsChangeAnimations;
    }

    @Override
    public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder) {
        return !mSupportsChangeAnimations || viewHolder.isInvalid();
    }

    @Override
    public boolean animateDisappearance(@NonNull ViewHolder viewHolder,
            @NonNull ItemHolderInfo preLayoutInfo, @Nullable ItemHolderInfo postLayoutInfo) {
        int oldLeft = preLayoutInfo.left;
        int oldTop = preLayoutInfo.top;
        View disappearingItemView = viewHolder.itemView;
        int newLeft = postLayoutInfo == null ? disappearingItemView.getLeft() : postLayoutInfo.left;
        int newTop = postLayoutInfo == null ? disappearingItemView.getTop() : postLayoutInfo.top;
        if (!viewHolder.isRemoved() && (oldLeft != newLeft || oldTop != newTop)) {
            disappearingItemView.layout(newLeft, newTop,
                    newLeft + disappearingItemView.getWidth(),
                    newTop + disappearingItemView.getHeight());
            if (DEBUG) {
                Log.d(TAG, "DISAPPEARING: " + viewHolder + " with view " + disappearingItemView);
            }
            return animateMove(viewHolder, oldLeft, oldTop, newLeft, newTop);
        } else {
            if (DEBUG) {
                Log.d(TAG, "REMOVED: " + viewHolder + " with view " + disappearingItemView);
            }
            return animateRemove(viewHolder);
        }
    }

    @Override
    public boolean animateAppearance(@NonNull ViewHolder viewHolder,
            @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
        if (preLayoutInfo != null && (preLayoutInfo.left != postLayoutInfo.left
                || preLayoutInfo.top != postLayoutInfo.top)) {
            // slide items in if before/after locations differ
            if (DEBUG) {
                Log.d(TAG, "APPEARING: " + viewHolder + " with view " + viewHolder);
            }
            return animateMove(viewHolder, preLayoutInfo.left, preLayoutInfo.top,
                    postLayoutInfo.left, postLayoutInfo.top);
        } else {
            if (DEBUG) {
                Log.d(TAG, "ADDED: " + viewHolder + " with view " + viewHolder);
            }
            return animateAdd(viewHolder);
        }
    }

    @Override
    public boolean animatePersistence(@NonNull ViewHolder viewHolder,
            @NonNull ItemHolderInfo preInfo, @NonNull ItemHolderInfo postInfo) {
        if (preInfo.left != postInfo.left || preInfo.top != postInfo.top) {
            if (DEBUG) {
                Log.d(TAG, "PERSISTENT: " + viewHolder +
                        " with view " + viewHolder.itemView);
            }
            return animateMove(viewHolder,
                    preInfo.left, preInfo.top, postInfo.left, postInfo.top);
        }
        dispatchMoveFinished(viewHolder);
        return false;
    }

    @Override
    public boolean animateChange(@NonNull ViewHolder oldHolder, @NonNull ViewHolder newHolder,
            @NonNull ItemHolderInfo preInfo, @NonNull ItemHolderInfo postInfo) {
        if (DEBUG) {
            Log.d(TAG, "CHANGED: " + oldHolder + " with view " + oldHolder.itemView);
        }
        final int fromLeft = preInfo.left;
        final int fromTop = preInfo.top;
        final int toLeft, toTop;
        if (newHolder.shouldIgnore()) {
            toLeft = preInfo.left;
            toTop = preInfo.top;
        } else {
            toLeft = postInfo.left;
            toTop = postInfo.top;
        }
        return animateChange(oldHolder, newHolder, fromLeft, fromTop, toLeft, toTop);
    }

    /**
     * Called when an item is removed from the RecyclerView. Implementors can choose
     * whether and how to animate that change, but must always call
     * {@link #dispatchRemoveFinished(ViewHolder)} when done, either
     * immediately (if no animation will occur) or after the animation actually finishes.
     * The return value indicates whether an animation has been set up and whether the
     * ItemAnimator's {@link #runPendingAnimations()} method should be called at the
     * next opportunity. This mechanism allows ItemAnimator to set up individual animations
     * as separate calls to {@link #animateAdd(ViewHolder) animateAdd()},
     * {@link #animateMove(ViewHolder, int, int, int, int) animateMove()},
     * {@link #animateRemove(ViewHolder) animateRemove()}, and
     * {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)} come in one by one,
     * then start the animations together in the later call to {@link #runPendingAnimations()}.
     *
     * <p>This method may also be called for disappearing items which continue to exist in the
     * RecyclerView, but for which the system does not have enough information to animate
     * them out of view. In that case, the default animation for removing items is run
     * on those items as well.</p>
     *
     * @param holder The item that is being removed.
     * @return true if a later call to {@link #runPendingAnimations()} is requested,
     * false otherwise.
     */
    abstract public boolean animateRemove(ViewHolder holder);

    /**
     * Called when an item is added to the RecyclerView. Implementors can choose
     * whether and how to animate that change, but must always call
     * {@link #dispatchAddFinished(ViewHolder)} when done, either
     * immediately (if no animation will occur) or after the animation actually finishes.
     * The return value indicates whether an animation has been set up and whether the
     * ItemAnimator's {@link #runPendingAnimations()} method should be called at the
     * next opportunity. This mechanism allows ItemAnimator to set up individual animations
     * as separate calls to {@link #animateAdd(ViewHolder) animateAdd()},
     * {@link #animateMove(ViewHolder, int, int, int, int) animateMove()},
     * {@link #animateRemove(ViewHolder) animateRemove()}, and
     * {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)} come in one by one,
     * then start the animations together in the later call to {@link #runPendingAnimations()}.
     *
     * <p>This method may also be called for appearing items which were already in the
     * RecyclerView, but for which the system does not have enough information to animate
     * them into view. In that case, the default animation for adding items is run
     * on those items as well.</p>
     *
     * @param holder The item that is being added.
     * @return true if a later call to {@link #runPendingAnimations()} is requested,
     * false otherwise.
     */
    abstract public boolean animateAdd(ViewHolder holder);

    /**
     * Called when an item is moved in the RecyclerView. Implementors can choose
     * whether and how to animate that change, but must always call
     * {@link #dispatchMoveFinished(ViewHolder)} when done, either
     * immediately (if no animation will occur) or after the animation actually finishes.
     * The return value indicates whether an animation has been set up and whether the
     * ItemAnimator's {@link #runPendingAnimations()} method should be called at the
     * next opportunity. This mechanism allows ItemAnimator to set up individual animations
     * as separate calls to {@link #animateAdd(ViewHolder) animateAdd()},
     * {@link #animateMove(ViewHolder, int, int, int, int) animateMove()},
     * {@link #animateRemove(ViewHolder) animateRemove()}, and
     * {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)} come in one by one,
     * then start the animations together in the later call to {@link #runPendingAnimations()}.
     *
     * @param holder The item that is being moved.
     * @return true if a later call to {@link #runPendingAnimations()} is requested,
     * false otherwise.
     */
    abstract public boolean animateMove(ViewHolder holder, int fromX, int fromY,
            int toX, int toY);

    /**
     * Called when an item is changed in the RecyclerView, as indicated by a call to
     * {@link Adapter#notifyItemChanged(int)} or
     * {@link Adapter#notifyItemRangeChanged(int, int)}.
     * <p>
     * Implementers can choose whether and how to animate changes, but must always call
     * {@link #dispatchChangeFinished(ViewHolder, boolean)} for each non-null ViewHolder,
     * either immediately (if no animation will occur) or after the animation actually finishes.
     * The return value indicates whether an animation has been set up and whether the
     * ItemAnimator's {@link #runPendingAnimations()} method should be called at the
     * next opportunity. This mechanism allows ItemAnimator to set up individual animations
     * as separate calls to {@link #animateAdd(ViewHolder) animateAdd()},
     * {@link #animateMove(ViewHolder, int, int, int, int) animateMove()},
     * {@link #animateRemove(ViewHolder) animateRemove()}, and
     * {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)} come in one by one,
     * then start the animations together in the later call to {@link #runPendingAnimations()}.
     *
     * @param oldHolder The original item that changed.
     * @param newHolder The new item that was created with the changed content. Might be null
     * @param fromLeft  Left of the old view holder
     * @param fromTop   Top of the old view holder
     * @param toLeft    Left of the new view holder
     * @param toTop     Top of the new view holder
     * @return true if a later call to {@link #runPendingAnimations()} is requested,
     * false otherwise.
     */
    abstract public boolean animateChange(ViewHolder oldHolder,
            ViewHolder newHolder, int fromLeft, int fromTop, int toLeft, int toTop);

    /**
     * Method to be called by subclasses when a remove animation is done.
     *
     * @param item The item which has been removed
     * @see RecyclerView.ItemAnimator#animateDisappearance(ViewHolder, ItemHolderInfo,
     * ItemHolderInfo)
     */
    public final void dispatchRemoveFinished(ViewHolder item) {
        onRemoveFinished(item);
        dispatchAnimationFinished(item);
    }

    /**
     * Method to be called by subclasses when a move animation is done.
     *
     * @param item The item which has been moved
     * @see RecyclerView.ItemAnimator#animateDisappearance(ViewHolder, ItemHolderInfo,
     * ItemHolderInfo)
     * @see RecyclerView.ItemAnimator#animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * @see RecyclerView.ItemAnimator#animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     */
    public final void dispatchMoveFinished(ViewHolder item) {
        onMoveFinished(item);
        dispatchAnimationFinished(item);
    }

    /**
     * Method to be called by subclasses when an add animation is done.
     *
     * @param item The item which has been added
     */
    public final void dispatchAddFinished(ViewHolder item) {
        onAddFinished(item);
        dispatchAnimationFinished(item);
    }

    /**
     * Method to be called by subclasses when a change animation is done.
     *
     * @param item    The item which has been changed (this method must be called for
     *                each non-null ViewHolder passed into
     *                {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)}).
     * @param oldItem true if this is the old item that was changed, false if
     *                it is the new item that replaced the old item.
     * @see #animateChange(ViewHolder, ViewHolder, int, int, int, int)
     */
    public final void dispatchChangeFinished(ViewHolder item, boolean oldItem) {
        onChangeFinished(item, oldItem);
        dispatchAnimationFinished(item);
    }

    /**
     * Method to be called by subclasses when a remove animation is being started.
     *
     * @param item The item being removed
     */
    public final void dispatchRemoveStarting(ViewHolder item) {
        onRemoveStarting(item);
    }

    /**
     * Method to be called by subclasses when a move animation is being started.
     *
     * @param item The item being moved
     */
    public final void dispatchMoveStarting(ViewHolder item) {
        onMoveStarting(item);
    }

    /**
     * Method to be called by subclasses when an add animation is being started.
     *
     * @param item The item being added
     */
    public final void dispatchAddStarting(ViewHolder item) {
        onAddStarting(item);
    }

    /**
     * Method to be called by subclasses when a change animation is being started.
     *
     * @param item    The item which has been changed (this method must be called for
     *                each non-null ViewHolder passed into
     *                {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)}).
     * @param oldItem true if this is the old item that was changed, false if
     *                it is the new item that replaced the old item.
     */
    public final void dispatchChangeStarting(ViewHolder item, boolean oldItem) {
        onChangeStarting(item, oldItem);
    }

    /**
     * Called when a remove animation is being started on the given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     *
     * @param item The ViewHolder being animated.
     */
    @SuppressWarnings("UnusedParameters")
    public void onRemoveStarting(ViewHolder item) {
    }

    /**
     * Called when a remove animation has ended on the given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     *
     * @param item The ViewHolder being animated.
     */
    public void onRemoveFinished(ViewHolder item) {
    }

    /**
     * Called when an add animation is being started on the given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     *
     * @param item The ViewHolder being animated.
     */
    @SuppressWarnings("UnusedParameters")
    public void onAddStarting(ViewHolder item) {
    }

    /**
     * Called when an add animation has ended on the given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     *
     * @param item The ViewHolder being animated.
     */
    public void onAddFinished(ViewHolder item) {
    }

    /**
     * Called when a move animation is being started on the given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     *
     * @param item The ViewHolder being animated.
     */
    @SuppressWarnings("UnusedParameters")
    public void onMoveStarting(ViewHolder item) {
    }

    /**
     * Called when a move animation has ended on the given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     *
     * @param item The ViewHolder being animated.
     */
    public void onMoveFinished(ViewHolder item) {
    }

    /**
     * Called when a change animation is being started on the given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     *
     * @param item    The ViewHolder being animated.
     * @param oldItem true if this is the old item that was changed, false if
     *                it is the new item that replaced the old item.
     */
    @SuppressWarnings("UnusedParameters")
    public void onChangeStarting(ViewHolder item, boolean oldItem) {
    }

    /**
     * Called when a change animation has ended on the given ViewHolder.
     * The default implementation does nothing. Subclasses may wish to override
     * this method to handle any ViewHolder-specific operations linked to animation
     * lifecycles.
     *
     * @param item    The ViewHolder being animated.
     * @param oldItem true if this is the old item that was changed, false if
     *                it is the new item that replaced the old item.
     */
    public void onChangeFinished(ViewHolder item, boolean oldItem) {
    }
}
