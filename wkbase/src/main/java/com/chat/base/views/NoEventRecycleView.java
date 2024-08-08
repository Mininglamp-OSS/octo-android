package com.chat.base.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.utils.AndroidUtilities;

public class NoEventRecycleView extends RecyclerView {
    private float headerViewY;
    private View bottomView, headerView;
    private int itemCount;

    public NoEventRecycleView(@NonNull Context context) {
        super(context);
    }

    public NoEventRecycleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NoEventRecycleView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setHeaderViewY(float headerViewY) {
        this.headerViewY = headerViewY;
    }

    public void setView(View bottomView, View headerView) {
        this.bottomView = bottomView;
        this.headerView = headerView;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            return super.dispatchTouchEvent(ev);
        } else {
            if (ev.getY() > headerViewY) {
                return super.dispatchTouchEvent(ev);
            } else return false;
        }
    }

    int firstVisibleItemPosition = 0;
    int lastVisibleItemPosition = 0;
    public OnScrollListener onScrollListener = new OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            int minCount = Math.min(itemCount, 3);

            if (recyclerView.getLayoutManager() instanceof FullyGridLayoutManager) {

                FullyGridLayoutManager fullyGridLayoutManager = (FullyGridLayoutManager) recyclerView.getLayoutManager();
                int firstIndex = fullyGridLayoutManager.findFirstVisibleItemPosition();
//                int endIndex = fullyGridLayoutManager.findLastVisibleItemPosition();
//                int spanCount = fullyGridLayoutManager.getSpanCount();
                if (firstIndex == 0) {
                    View childView = recyclerView.getChildAt(firstIndex);
                    if (childView == null) {
                        return;
                    }
                    headerView.getLayoutParams().height = childView.getTop();
                    setHeaderViewY(headerView.getLayoutParams().height);
                    if (dy < 0) {
                        int maxHeight = bottomView.getTop() - AndroidUtilities.dp(100);
                        headerView.getLayoutParams().height = childView.getBottom() - childView.getHeight();
                        if (headerView.getLayoutParams().height > maxHeight) {
                            headerView.getLayoutParams().height = maxHeight;
                        }
                        setHeaderViewY(headerView.getLayoutParams().height);
                    }
                } else {
                    headerView.getLayoutParams().height = bottomView.getTop() - AndroidUtilities.dp(100);
                    setHeaderViewY(0);
                }
            } else {
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    if (layoutManager.findLastVisibleItemPosition() != 0)
                        lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();
                    if (firstVisibleItemPosition == 0) {
                        View childView = recyclerView.getChildAt(firstVisibleItemPosition + 1);
                        if (childView == null) {
                            return;
                        }
                        headerView.getLayoutParams().height = childView.getTop();
                        setHeaderViewY(headerView.getLayoutParams().height);
                        if (dy < 0) {
                            int maxHeight = bottomView.getTop() - AndroidUtilities.dp(minCount * 40);
                            headerView.getLayoutParams().height = childView.getBottom() - childView.getHeight();
                            if (headerView.getLayoutParams().height > maxHeight) {
                                headerView.getLayoutParams().height = maxHeight;
                            }
                            setHeaderViewY(headerView.getLayoutParams().height);
                        }
                    } else {
                        headerView.getLayoutParams().height = bottomView.getTop() - AndroidUtilities.dp(minCount * 40);
                        setHeaderViewY(0);
                    }
                }

            }
        }

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (iScrollListener != null) {
                iScrollListener.onScrollStateChanged(recyclerView, newState);
            }
        }
    };

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount - 1;
        if (this.itemCount < 1) this.itemCount = 1;
    }

    public void resetHeight(boolean isOpen) {
        final int firstPosition = firstVisibleItemPosition;
        final int lastPosition = lastVisibleItemPosition;
        if (this.getVisibility() != VISIBLE) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (headerView.getHeight() == 0) {
                scrollToPosition(firstPosition);
                return;
            }

            headerView.getLayoutParams().height = bottomView.getTop() - AndroidUtilities.dp((lastPosition - firstPosition) * 40);
            setHeaderViewY(headerView.getLayoutParams().height);
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) NoEventRecycleView.this.getLayoutManager();
            if (linearLayoutManager != null) {
                int margin = (bottomView.getTop() / AndroidUtilities.dp(40)) - lastPosition + 1;
                linearLayoutManager.scrollToPositionWithOffset(firstPosition + 1, AndroidUtilities.dp(margin * 40));
            }
        }, 50);

    }

    IScrollListener iScrollListener;

    public void addIScrollListener(IScrollListener iScrollListener) {
        this.iScrollListener = iScrollListener;
    }

    public interface IScrollListener {
        void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState);
    }
}
