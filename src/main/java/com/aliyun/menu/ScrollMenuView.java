package com.aliyun.menu;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.Scroller;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author appmac
 */
public class ScrollMenuView extends LinearLayout {


    private static final String TAG = ScrollMenuView.class.getSimpleName();
    private int mTouchSlop, mMinimumFlingVelocity, mMaximumFlingVelocity;

    private int mLastTouchX, mLastTouchY, mLastScrollX;


    private static final int INVALID_POINTER = -1;
    //活动的手指id
    private int mScrollPointerId = INVALID_POINTER;

    private static final byte STATUS_CLOSE = 0;
    private static final byte STATUS_OPEN = 1;
    private static final byte STATUS_SCROLLING = 2;

    private int status = STATUS_CLOSE;

    private int preStatus = STATUS_CLOSE;

    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    private View mMenuView;


    private List<OnMenuListener> menuListeners = new ArrayList<>();


    public ScrollMenuView(Context context) {
        this(context, null);
    }

    public ScrollMenuView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScrollMenuView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ScrollMenuView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }


    private void init(Context context) {
        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mTouchSlop = viewConfiguration.getScaledTouchSlop();
        mMinimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = viewConfiguration.getScaledMaximumFlingVelocity();

        mScroller = new Scroller(context);

    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mVelocityTracker = VelocityTracker.obtain();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mMenuView = getChildAt(1);

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        Log.e(TAG, "onInterceptTouchEvent: " + MotionEvent.actionToString(action));
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mScrollPointerId = ev.getPointerId(0);
                mLastTouchX = (int) (ev.getX() + 0.5f);
                mLastTouchY = (int) (ev.getY() + 0.5f);
                break;
            case MotionEvent.ACTION_MOVE:
                final int index = ev.findPointerIndex(mScrollPointerId);
                if (index < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mScrollPointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }

                int diffX = mLastTouchX - (int) (ev.getX(index) + 0.5f);
                int diffY = mLastTouchY - (int) (ev.getY(index) + 0.5f);

                if (Math.abs(diffX) > mTouchSlop && diffX * diffX > 0.2f * diffY * diffY) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;

            default:

        }
        return super.onInterceptTouchEvent(ev);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        VelocityTracker velocityTracker = getVelocityTracker();
        velocityTracker.addMovement(event);

        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        Log.e(TAG, "onTouchEvent: " + MotionEvent.actionToString(action));
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchX = (int) (event.getX() + 0.5f);
                mLastTouchY = (int) (event.getY() + 0.5f);
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mScrollPointerId = event.getPointerId(actionIndex);
                mLastTouchX = (int) (event.getX(actionIndex) + 0.5f);
                mLastTouchY = (int) (event.getY(actionIndex) + 0.5f);
                break;
            case MotionEvent.ACTION_MOVE:
                final int index = event.findPointerIndex(mScrollPointerId);
                if (index < 0) {
                    return false;
                }

                int currentX = (int) (event.getX(index) + 0.5f);
                int diffX = mLastTouchX - currentX;
                mLastTouchX = currentX;
                int dx = computeMove(diffX);
                setStatus(STATUS_SCROLLING);
                if (dx != 0) {
                    onScroll(dx, 0);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(actionIndex) == mScrollPointerId) {
                    // Pick a new pointer to pick up the slack.
                    final int newIndex = actionIndex == 0 ? 1 : 0;
                    mScrollPointerId = event.getPointerId(newIndex);
                    mLastTouchX = (int) (event.getX(newIndex) + 0.5f);
                    mLastTouchY = (int) (event.getY(newIndex) + 0.5f);
                }

                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mScrollPointerId = INVALID_POINTER;
                velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                int xVelocity = (int) velocityTracker.getXVelocity();
                velocityTracker.clear();

                if (Math.abs(xVelocity) > mMinimumFlingVelocity) {
                    fling(xVelocity);
                    break;
                }

                int scrollX = getScrollX();
                if (getStatus() == STATUS_SCROLLING && scrollX < getMenuWidth() / 2) {
                    Log.e(TAG, "onTouchEvent: closeMenu");
                    closeMenu();
                } else if (getStatus() == STATUS_SCROLLING && scrollX >= getMenuWidth() / 2) {
                    Log.e(TAG, "onTouchEvent: openMenu");
                    openMenu();
                }
                break;
            default:
        }
        return true;
    }


    private int getMenuWidth() {
        return mMenuView.getMeasuredWidth();
    }


    @Override
    public void computeScroll() {
        super.computeScroll();
        Log.e(TAG, "computeScroll: ");
        if (mScroller.computeScrollOffset()) {
            int currX = mScroller.getCurrX();
            int diffX = mLastScrollX - currX;
            mLastScrollX = currX;
            int dx = computeMove(diffX);
            setStatus(STATUS_SCROLLING);
            if (dx != 0) {
                onScroll(dx, 0);
            }
            if (mScroller.isFinished()) {
                setStatus(preStatus);
            }
            postInvalidateOnAnimation();
        } else {
            mLastScrollX = 0;
        }
    }


    private int computeMove(int dx) {
        int scrollX = getScrollX();
        if (scrollX + dx <= 0) {
            return -scrollX;
        } else if (scrollX + dx > getMenuWidth()) {
            return getMenuWidth() - scrollX;
        }
        return dx;
    }


    public void onScroll(int dx, int dy) {
        scrollBy(dx, 0);
        Log.e(TAG, "onScroll: " + dx + "  " + dy);
        Iterator<OnMenuListener> iterator = menuListeners.iterator();
        while (iterator.hasNext()) {
            iterator.next().onScroll(dx, dy);
        }
    }

    public void onStatusChange(int oldStatus, int curStatus) {
        Log.e(TAG, "onStatusChange: " + oldStatus + "   " + curStatus);
        Iterator<OnMenuListener> iterator = menuListeners.iterator();
        while (iterator.hasNext()) {
            iterator.next().onStatusChange(oldStatus, curStatus);
        }
    }


    private void fling(int xVelocity) {
        Log.e(TAG, "fling: " + xVelocity);

        mScroller.fling(getScrollX(), getScrollY(), -xVelocity, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);

        // 计算惯性结束位置
        int offset = getScrollX() + mScroller.getFinalX();

        mScroller.abortAnimation();
        Log.e(TAG, "onTouchEvent: " + offset);

        if (offset >= getMenuWidth() / 2) {
            openMenu();
        } else {
            closeMenu();
        }
//        if (xVelocity < 0) {
//            openMenu();
//        } else {
//            closeMenu();
//        }
    }


    public void closeMenu() {
        preStatus = STATUS_CLOSE;
        mLastScrollX = getScrollX();
        mScroller.startScroll(mLastScrollX, 0, mLastScrollX, 0);
        postInvalidate();
    }


    public void openMenu() {
        preStatus = STATUS_OPEN;
        mLastScrollX = getScrollX();
        mScroller.startScroll(mLastScrollX, 0, getScrollX() - getMenuWidth(), 0);
        postInvalidate();
    }


    public VelocityTracker getVelocityTracker() {
        return mVelocityTracker;
    }


    public void registerListener(OnMenuListener onMenuListener) {
        if (!hasListener(onMenuListener)) {
            menuListeners.add(onMenuListener);
        }
    }


    public void unRegisterListener(OnMenuListener onMenuListener) {
        if (hasListener(onMenuListener)) {
            menuListeners.remove(onMenuListener);
        }
    }


    public boolean hasListener(OnMenuListener onMenuListener) {
        return menuListeners.contains(onMenuListener);
    }


    public int getStatus() {
        return status;
    }

    private void setStatus(int status) {
        if (this.status == status) {
            return;
        }
        int oldStatus = this.status;
        this.status = status;
        onStatusChange(oldStatus, status);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVelocityTracker.recycle();
        closeMenu();
        menuListeners.clear();
    }


}
