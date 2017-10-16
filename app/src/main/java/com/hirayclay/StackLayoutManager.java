package com.hirayclay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import static com.hirayclay.Align.LEFT;
import static com.hirayclay.Align.RIGHT;

/**
 * Created by CJJ on 2017/5/17.
 * my thought is simple：we assume the first item in the initial state is the base position ，
 * we only need to calculate the appropriate position{@link #left(int index)}for the given item
 * index with the given offset{@link #mTotalOffset}.After solve this thinking confusion ,this
 * layoutManager is easy to implement
 *
 * @author CJJ
 */

public class StackLayoutManager extends RecyclerView.LayoutManager {

    private static final String TAG = "StackLayoutManager";


    //the space unit for the stacked item
    int mSpace = 60;
    //the offset unit,deciding current position(the sum of one child's width and one space)
    int mUnit;
    //the counting variable ,record the total offset
    int mTotalOffset;
    ObjectAnimator animator;
    private int animateValue;
    private int duration = 300;
    private RecyclerView.Recycler recycler;
    private int lastAnimateValue;
    private int maxStackCount = 4;//the max stacked item count;
    private int initialStackCount = 4;//initial stacked item
    private float secondaryScale = 0.8f;
    private float scaleRatio = 0.4f;
    private int initialOffset;
    private boolean initial;
    private int mMinVelocityX;
    private VelocityTracker mVelocityTracker = VelocityTracker.obtain();
    private int pointerId;
    private Align direction = LEFT;

    public StackLayoutManager(Config config) {
        this.maxStackCount = config.maxStackCount;
        this.mSpace = config.space;
        this.initialStackCount = config.initialStackCount;
        this.secondaryScale = config.secondaryScale;
        this.scaleRatio = config.scaleRatio;
    }


    public StackLayoutManager() {
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        this.recycler = recycler;
        detachAndScrapAttachedViews(recycler);
        //got the mUnit basing on the first child,of course we assume that  all the item has the same size
        View anchorView = recycler.getViewForPosition(0);
        measureChildWithMargins(anchorView, 0, 0);
        mUnit = anchorView.getMeasuredWidth() + mSpace;
        //because this method will be called twice
        initialOffset = initialStackCount * mUnit;
        mMinVelocityX = ViewConfiguration.get(anchorView.getContext()).getScaledMinimumFlingVelocity();
        fill(recycler, 0);

    }


    @Override
    public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);
        if (!initial) {
            fill(recycler, initialOffset);
            initial = true;
        }
    }

    /**
     * the magic function :).all the work including computing ,recycling,and layout is done here
     *
     * @param recycler
     */
    private int fill(RecyclerView.Recycler recycler, int dy) {
        if (direction == LEFT)
            return fillFromLeft(recycler, dy);
        if (direction == RIGHT)
            return fillFromRight(recycler, dy);
        else return dy;
    }

    private int fillFromRight(RecyclerView.Recycler recycler, int dy) {

        if (mTotalOffset + dy < 0 || (mTotalOffset + dy + 0f) / mUnit > getItemCount() - 1)
            return 0;
        detachAndScrapAttachedViews(recycler);
        mTotalOffset += direction.sign * dy;
        int count = getChildCount();
        //removeAndRecycle  views
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child != null && shouldRecycle(child, dy))
                removeAndRecycleView(child, recycler);
        }


        int curPos = mTotalOffset / mUnit;
        float n = (mTotalOffset + 0f) / mUnit;
        float x = n % 1f;
        int start = curPos - maxStackCount >= 0 ? curPos - maxStackCount : 0;
        int end = curPos + maxStackCount > getItemCount() ? getItemCount() : curPos + maxStackCount;

        //layout view
        for (int i = start; i < end; i++) {
            View view = recycler.getViewForPosition(i);

            float scale = scale(i);
            float alpha = alpha(i);

            addView(view);
            measureChildWithMargins(view, 0, 0);
            int left = (int) (left(i) - (1 - scale) * view.getMeasuredWidth() / 2);
            layoutDecoratedWithMargins(view, left, 0, left + view.getMeasuredWidth(), view.getMeasuredHeight());
            view.setAlpha(alpha);
            view.setScaleY(scale);
            view.setScaleX(scale);
        }

        return dy;
    }

    private int fillFromLeft(RecyclerView.Recycler recycler, int dy) {
        if (mTotalOffset + dy < 0 || (mTotalOffset + dy + 0f) / mUnit > getItemCount() - 1)
            return 0;
        detachAndScrapAttachedViews(recycler);
        mTotalOffset += direction.sign * dy;
        int count = getChildCount();
        //removeAndRecycle  views
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child != null && shouldRecycle(child, dy))
                removeAndRecycleView(child, recycler);
        }


        int curPos = mTotalOffset / mUnit;
        float n = (mTotalOffset + 0f) / mUnit;
        float x = n % 1f;
        int start = curPos - maxStackCount >= 0 ? curPos - maxStackCount : 0;
        int end = curPos + maxStackCount > getItemCount() ? getItemCount() : curPos + maxStackCount;

        //layout view
        for (int i = start; i < end; i++) {
            View view = recycler.getViewForPosition(i);

            float scale = scale(i);
            float alpha = alpha(i);

            addView(view);
            measureChildWithMargins(view, 0, 0);
            int left = (int) (left(i) - (1 - scale) * view.getMeasuredWidth() / 2);
            layoutDecoratedWithMargins(view, left, 0, left + view.getMeasuredWidth(), view.getMeasuredHeight());
            view.setAlpha(alpha);
            view.setScaleY(scale);
            view.setScaleX(scale);
        }

        return dy;
    }


    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
        //check when raise finger and settle to the appropriate item
        view.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mVelocityTracker.addMovement(event);
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (animator != null
                            && animator.isRunning())
                        animator.cancel();
                    pointerId = event.getPointerId(0);

                }


                if (event.getAction() == MotionEvent.ACTION_UP) {
                    mVelocityTracker.computeCurrentVelocity(1000, 14000);
                    float xVelocity = VelocityTrackerCompat.getXVelocity(mVelocityTracker, pointerId);

                    int o = mTotalOffset % mUnit;
                    int scrollX;
                    if (Math.abs(xVelocity) < mMinVelocityX)
                        if (o != 0) {
                            if (o >= mUnit / 2)
                                scrollX = mUnit - o;
                            else scrollX = -o;
                            int dur = (int) (Math.abs((scrollX + 0f) / mUnit) * duration);
                            brewAndStartAnimator(dur, scrollX);
                        }
                }
                return false;
            }

        });

        view.setOnFlingListener(new RecyclerView.OnFlingListener() {
            @Override
            public boolean onFling(int velocityX, int velocityY) {
                int o = mTotalOffset % mUnit;
                int s = mUnit - o;
                int scrollX;
                if (velocityX > 0) {
                    scrollX = s;
                } else
                    scrollX = -o;
                int dur = computeSettleDuration(Math.abs(scrollX), Math.abs(velocityX))/* (int) (3000f / Math.abs(velocityX) * duration)*/;
                brewAndStartAnimator(dur, scrollX);
                return true;
            }
        });
    }

    int computeSettleDuration(int distance, float xvel) {
        float sWeight = 0.5f * distance / mUnit;
        float velWeight = 0.5f * mMinVelocityX / xvel;

        return (int) ((sWeight + velWeight) * duration);
    }

    private void brewAndStartAnimator(int dur, int finalX) {
        animator = ObjectAnimator.ofInt(StackLayoutManager.this, "animateValue", 0, finalX);
        animator.setDuration(dur);
        animator.start();
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                lastAnimateValue = 0;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                lastAnimateValue = 0;
            }
        });
    }

    /******************************precise math method*******************************/
    private float alpha(int position) {
        float alpha;
        int curPos = mTotalOffset / mUnit;
        float n = (mTotalOffset + .0f) / mUnit;
        if (position > curPos)
            alpha = 1.0f;
        else {
            //temporary linear map,barely ok
            float o = 1 - (n - position) / maxStackCount;
            alpha = o;
        }
        //for precise checking,oh may be kind of dummy
        return alpha <= 0.001f ? 0 : alpha;
    }

    private float scale(int position) {
        float scale;
        int curPos = this.mTotalOffset / mUnit;
        float n = (mTotalOffset + .0f) / mUnit;
        float x = n - curPos;


        float tail;
        switch (direction) {
            default:
            case TOP:
            case LEFT:
                tail = x;
                break;
            case BOTTOM:
            case RIGHT:
                tail = 1 - x;
                break;

        }
        if (position > curPos) {
            //let the item's (index:position+1) scale be 1 when the item slide 1/2 mUnit,
            // this have better visual effect
            if (position == curPos + 1 && direction == LEFT) {
//                scale = 0.8f + (0.4f * x >= 0.2f ? 0.2f : 0.4f * x);
                scale = secondaryScale + (x > 0.5f ? 1 - secondaryScale : 2 * (1 - secondaryScale) * x);
            } else scale = secondaryScale;
        } else if (position == curPos) {
            scale = 1 - scaleRatio * tail / maxStackCount;
        } else {

            if (position == curPos - 1 && direction == RIGHT) {
//                scale = 0.8f + (0.4f * x >= 0.2f ? 0.2f : 0.4f * x);
                scale = secondaryScale + (x > 0.5f ? 1 - secondaryScale : 2 * (1 - secondaryScale) * x);
            } else if ((position < curPos - maxStackCount && direction == LEFT) || (position > curPos + maxStackCount && direction == RIGHT))
                scale = 0f;
            else {
                scale = 1f - scaleRatio * (/*n - curPos*/tail + curPos - position) / maxStackCount;
            }
        }
        return scale;
    }

    /**
     * @param position the index of the item in the adapter
     * @return the appropriate left for the given item
     */
    private int left(int position) {

        int left;
        int curPos = mTotalOffset / mUnit;
        int tail = mTotalOffset % mUnit;
        float n = (mTotalOffset + .0f) / mUnit;
        float x = n - curPos;

        if (position <= curPos) {

            if (position == curPos) {
                left = (int) (mSpace * (maxStackCount - x));
            } else {
                left = (int) (mSpace * (maxStackCount - x - (curPos - position)));

            }
        } else {
            if (position == curPos + 1)
                left = mSpace * maxStackCount + (position - curPos) * mUnit - tail;
            else {
                float closestBaseItemScale = scale(position - 1);

                left = (int) (mSpace * maxStackCount + (position - curPos) * mUnit - tail - (mUnit - mSpace) * (1 - closestBaseItemScale));
            }
            left = left <= 0 ? 0 : left;
        }
        return left;
    }


    public void setAnimateValue(int animateValue) {
        this.animateValue = animateValue;
        int dy = this.animateValue - lastAnimateValue;
        fill(recycler, dy);
        lastAnimateValue = animateValue;
    }

    public int getAnimateValue() {
        return animateValue;
    }

    /**
     * should recycle view with the given dy or say check if the
     * view is out of the bound after the dy is applied
     *
     * @param view ..
     * @param dy
     * @return
     */
    public boolean shouldRecycle(View view/*int position*/, int dy) {
        return view.getLeft() - dy < 0 || view.getRight() - dy > getWidth();
    }


    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        return fill(recycler, dx);
    }

    @Override
    public boolean canScrollHorizontally() {
        return true;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }
}
