package com.hirayclay;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.support.annotation.FloatRange;
import android.support.v7.widget.ForwardingListener;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by CJJ on 2017/5/17.
 * 思路比较简单：假设初始状态下第一个item所在的位置是基准位置，那么在给定滚动距离的情况下，每个item应该
 * 在什么位置，其实就是计算一个对应关系的问题，类似f(x)=Nx，理清楚这个对应关系就很容易实现这个layoutManager
 *
 * @author CJJ
 */

public class StackLayoutManager extends RecyclerView.LayoutManager {

    private static final String TAG = "StackLayoutManager";


    int interval = 60;
    int unit;
    int totalOffset;
    private int itemUnit;
    ObjectAnimator animator;
    private int animateValue;
    private int duration = 400;
    private RecyclerView.Recycler recycler;
    private int lastAnimateValue;

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        detachAndScrapAttachedViews(recycler);
        //got the unit basing on the first child,of course we assume that  all the item has the same size
        View anchorView = recycler.getViewForPosition(0);
        measureChildWithMargins(anchorView, 0, 0);
        itemUnit = anchorView.getMeasuredWidth();
        unit = anchorView.getMeasuredWidth() + interval;
        fill(recycler, 0);

    }

    /**
     * @param recycler
     */
    private int fill(RecyclerView.Recycler recycler, int dy) {
        this.recycler = recycler;
        if (totalOffset + dy < 0 || (totalOffset + dy + 0f) / unit > getItemCount()-1)
            return 0;
        detachAndScrapAttachedViews(recycler);
        totalOffset += dy;
        int count = getChildCount();
        //removeAndRecycle  views
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child != null && shouldRecycle(child, dy))
                removeAndRecycleView(child, recycler);
        }


        int curPos = totalOffset / unit;
        float n = (totalOffset + 0f) / unit;
        float x = n % 1f;
        int start = curPos - 3 >= 0 ? curPos - 3 : 0;
        int end = curPos + 3 > getItemCount() ? getItemCount() : curPos + 3;

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
        //check when raise finger and settle to the target item
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN
                        && animator != null
                        && animator.isRunning())
                    animator.cancel();


                if (event.getAction() == MotionEvent.ACTION_UP) {
                    int o = totalOffset % unit;
                    int scrollX;
                    if (o != 0) {
                        if (o >= unit / 2)
                            scrollX = unit - o;
                        else scrollX = -o;
//                        int dur= (int) (Math.abs((scrollX+0f)/unit)*duration);
                        brewAndStartAnimator(duration, scrollX);
                    }
                }
                return false;
            }
        });
        view.setOnFlingListener(new RecyclerView.OnFlingListener() {
            @Override
            public boolean onFling(int velocityX, int velocityY) {
                int N = totalOffset / unit;
                float n = (totalOffset + 0f) / unit;
                int o = totalOffset % unit;
                int s = unit - o;
                int scrollX;
                if (velocityX > 0) {
                    scrollX = s;
                } else
                    scrollX = -o;
                if (BuildConfig.DEBUG)
                    Log.i(TAG, "onFling: ===res:===" + (1f - n + N) + "========scrollX=" + (scrollX + 0f) / unit);
                int dur = duration;
                brewAndStartAnimator(dur, scrollX);
                return true;
            }
        });
    }

    private void brewAndStartAnimator(int dur, int finalX) {
        animator = ObjectAnimator.ofInt(StackLayoutManager.this, "animateValue", 0, finalX);
        animator.setDuration(dur);
        animator.start();
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                lastAnimateValue = 0;
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    /******************************precise math method*******************************/
    public float alpha(int position) {
        float alpha;
        int curPos = totalOffset / unit;
        float n = (totalOffset + 0.0f) / unit;
        if (position > curPos)
            alpha = 1.0f;
        else {
            //temporary linear map,barely ok
            float o = 1 - (n - position) / 3;
            alpha = o;
        }
        //for precise checking
        return alpha <= 0.001f ? 0 : alpha;
    }

    public float scale(int position) {
        float scale;
        int curPos = this.totalOffset / unit;
        float n = (totalOffset + 0.0f) / unit;
        float x = n - curPos;
        // position >= curPos+1;
        if (position >= curPos) {
            if (position == curPos)
                scale = 1 - 0.3f * (n - curPos) / 3f;
            else if (position == curPos + 1)
                //让curPosition+1 位置的item在划过unit一半的距离就有scale =1，视觉效果好一些
                scale = 0.8f + (0.4f * x >= 0.2f ? 0.2f : 0.4f * x);
            else scale = 0.8f;
        } else {//position <= curPos
            if (position < curPos - 3)
                scale = 0f;
            else {
                scale = 1f - 0.3f * (n - curPos + curPos - position) / 3f;
            }
        }
        return scale;

    }

    /**
     * @param position the target position
     * @return
     */
    public int left(int position) {

        int left = 0;
        int curPos = totalOffset / unit;
        float n = (totalOffset + 0.0f) / unit;
        float x = n - curPos;
        if (position <= curPos) {

            if (position == curPos) {
                left = (int) (interval * (3 - x));
            } else {
                left = (int) (interval * (3 - x - (curPos - position)));

            }
        } else {
            left = interval * 3 + position * unit - totalOffset;
            left = left <= 0 ? 0 : left;
            if (position == 0)
                Log.i(TAG, "left: @@@@@@@@@@@@@@@@@@@@@" + left);
        }
        return left;
    }


    public void setAnimateValue(int animateValue) {
        this.animateValue = animateValue;
        Log.i("OFFSET", "setAnimateValue: " + animateValue);
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

    public float interpolator(@FloatRange(from = 0f, to = 1.0f) float input) {
        return (float) (Math.sqrt(input * input));
    }

    @Override
    public boolean canScrollHorizontally() {
//        Log.i(TAG, "canScrollHorizontally: ");
        return true;
    }

//    @Override
//    public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec, int heightSpec) {
//        super.onMeasure(recycler, state, widthSpec, heightSpec);
//        int count = getChildCount();
//        int height = 0;
//        for (int i = 0; i < count; i++) {
//            View child = getChildAt(i);
//            measureChildWithMargins(child, widthSpec, heightSpec);
//            height = Math.max(height, child.getHeight());
//        }
//        setMeasuredDimension(View.MeasureSpec.getSize(widthSpec), height);
//    }


    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }
}
