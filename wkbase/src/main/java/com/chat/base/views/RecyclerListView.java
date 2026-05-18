/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.StateSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;

/**
 * A RecyclerView subclass that adds item click/long-click listeners,
 * empty-view management, scroll-enable toggling, and a selection highlight drawable.
 * <p>
 * This is a clean-room implementation — no code was copied from any GPL source.
 */
public class RecyclerListView extends RecyclerView {

    // ── Listener interfaces ──────────────────────────────────────────────

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public interface OnItemLongClickListener {
        boolean onItemClick(View view, int position);
    }

    public interface OnInterceptTouchListener {
        boolean onInterceptTouchEvent(MotionEvent event);
    }

    // ── Inner adapter / holder types ─────────────────────────────────────

    /**
     * Trivial ViewHolder wrapper kept for API compatibility.
     */
    public static class Holder extends ViewHolder {
        public Holder(@NonNull View itemView) {
            super(itemView);
        }
    }

    /**
     * Base adapter that adds an {@code isEnabled} query per holder.
     */
    public abstract static class SelectionAdapter extends Adapter<ViewHolder> {
        public abstract boolean isEnabled(ViewHolder holder);

        public int getSelectionBottomPadding(View view) {
            return 0;
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private OnScrollListener onScrollListener;
    private OnInterceptTouchListener onInterceptTouchListener;

    private View emptyView;
    private boolean hideIfEmpty = true;
    private boolean isHidden;
    private boolean hiddenByEmptyView;
    private int emptyViewAnimateToVisibility;

    private boolean scrollEnabled = true;

    /** Publicly accessible — used by {@link RecyclerAnimationScrollHelper}. */
    public boolean fastScrollAnimationRunning;

    // Selector highlight state
    protected Drawable selectorDrawable;
    protected int selectorPosition;
    protected Rect selectorRect = new Rect();
    private boolean drawSelectorBehind;

    // Touch / click handling
    private GestureDetector gestureDetector;
    private View currentChildView;
    private int currentChildPosition;
    private boolean interceptedByChild;
    private Runnable selectChildRunnable;
    private Runnable clickRunnable;

    // ── Data observer for empty-view management ──────────────────────────

    private final AdapterDataObserver emptyViewObserver = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            checkIfEmpty();
            selectorRect.setEmpty();
            invalidate();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            checkIfEmpty();
        }
    };

    // ── Constructors ─────────────────────────────────────────────────────

    public RecyclerListView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public RecyclerListView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RecyclerListView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        selectorDrawable = Theme.getBackground(Theme.colorAccount, 30f);
        if (selectorDrawable != null) {
            selectorDrawable.setCallback(this);
        }

        gestureDetector = new GestureDetector(context, new ItemClickGestureListener());
        gestureDetector.setIsLongpressEnabled(false);

        // Wrap scroll listener so callers can set their own via setOnScrollListener
        super.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                // Cancel any pending child press when the user starts scrolling
                if (newState != SCROLL_STATE_IDLE && currentChildView != null) {
                    clearPendingPress();
                }
                if (onScrollListener != null) {
                    onScrollListener.onScrollStateChanged(recyclerView, newState);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (onScrollListener != null) {
                    onScrollListener.onScrolled(recyclerView, dx, dy);
                }
                if (selectorPosition != NO_POSITION) {
                    selectorRect.offset(-dx, -dy);
                    if (selectorDrawable != null) {
                        selectorDrawable.setBounds(selectorRect);
                    }
                    invalidate();
                } else {
                    selectorRect.setEmpty();
                }
            }
        });

        addOnItemTouchListener(new ItemClickTouchListener());
    }

    // ── Public API: listeners ────────────────────────────────────────────

    public void setOnItemClickListener(OnItemClickListener listener) {
        onItemClickListener = listener;
    }

    public OnItemClickListener getOnItemClickListener() {
        return onItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        onItemLongClickListener = listener;
        gestureDetector.setIsLongpressEnabled(listener != null);
    }

    @Override
    public void setOnScrollListener(OnScrollListener listener) {
        onScrollListener = listener;
    }

    public OnScrollListener getOnScrollListener() {
        return onScrollListener;
    }

    public void setOnInterceptTouchListener(OnInterceptTouchListener listener) {
        onInterceptTouchListener = listener;
    }

    // ── Public API: empty view ───────────────────────────────────────────

    public void setEmptyView(View view) {
        if (emptyView == view) {
            return;
        }
        emptyView = view;
        if (isHidden) {
            if (emptyView != null) {
                emptyViewAnimateToVisibility = GONE;
                emptyView.setVisibility(GONE);
            }
        } else {
            emptyViewAnimateToVisibility = -1;
            checkIfEmpty();
        }
    }

    public View getEmptyView() {
        return emptyView;
    }

    public void setHideIfEmpty(boolean value) {
        hideIfEmpty = value;
    }

    public void hide() {
        if (isHidden) return;
        isHidden = true;
        if (getVisibility() != GONE) setVisibility(GONE);
        if (emptyView != null && emptyView.getVisibility() != GONE) {
            emptyView.setVisibility(GONE);
        }
    }

    public void show() {
        if (!isHidden) return;
        isHidden = false;
        checkIfEmpty();
    }

    // ── Public API: scroll control ───────────────────────────────────────

    public void setScrollEnabled(boolean value) {
        scrollEnabled = value;
    }

    @Override
    public boolean canScrollVertically(int direction) {
        return scrollEnabled && super.canScrollVertically(direction);
    }

    // ── Public API: selector appearance ──────────────────────────────────

    public void setDrawSelectorBehind(boolean value) {
        drawSelectorBehind = value;
    }

    public void setSelectorDrawableColor(int color) {
        if (selectorDrawable != null) {
            selectorDrawable.setCallback(this);
        }
    }

    // ── Public API: misc helpers ─────────────────────────────────────────

    public void invalidateViews() {
        for (int i = 0, n = getChildCount(); i < n; i++) {
            getChildAt(i).invalidate();
        }
    }

    public boolean isFastScrollAnimationRunning() {
        return fastScrollAnimationRunning;
    }

    public void cancelClickRunnables(boolean uncheck) {
        if (selectChildRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
            selectChildRunnable = null;
        }
        if (currentChildView != null) {
            View child = currentChildView;
            if (uncheck) {
                child.setPressed(false);
            }
            currentChildView = null;
            selectorRect.setEmpty();
        }
        if (clickRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(clickRunnable);
            clickRunnable = null;
        }
        interceptedByChild = false;
    }

    public void setAnimateEmptyView(boolean animate, int emptyViewAnimationType) {
        // Kept for API compatibility; animation not re-implemented to keep the file lean.
    }

    // ── Adapter wiring with empty-view observer ──────────────────────────

    @Override
    public void setAdapter(Adapter adapter) {
        Adapter oldAdapter = getAdapter();
        if (oldAdapter != null) {
            oldAdapter.unregisterAdapterDataObserver(emptyViewObserver);
        }
        selectorPosition = NO_POSITION;
        selectorRect.setEmpty();
        super.setAdapter(adapter);
        if (adapter != null) {
            adapter.registerAdapterDataObserver(emptyViewObserver);
        }
        checkIfEmpty();
    }

    @Override
    public void stopScroll() {
        try {
            super.stopScroll();
        } catch (NullPointerException ignored) {
            // Guard against internal NPE in some RecyclerView versions.
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void requestLayout() {
        if (fastScrollAnimationRunning) {
            return;
        }
        super.requestLayout();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) {
            hiddenByEmptyView = false;
        }
    }

    // ── Touch interception ───────────────────────────────────────────────

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!isEnabled()) {
            return false;
        }
        if (onInterceptTouchListener != null && onInterceptTouchListener.onInterceptTouchEvent(e)) {
            return true;
        }
        return super.onInterceptTouchEvent(e);
    }

    // ── Child attached: enable/disable based on adapter ──────────────────

    @Override
    public void onChildAttachedToWindow(@NonNull View child) {
        if (getAdapter() instanceof SelectionAdapter) {
            ViewHolder holder = findContainingViewHolder(child);
            if (holder != null) {
                child.setEnabled(((SelectionAdapter) getAdapter()).isEnabled(holder));
            }
        }
        super.onChildAttachedToWindow(child);
    }

    // ── Selector drawing ─────────────────────────────────────────────────

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (drawSelectorBehind && !selectorRect.isEmpty() && selectorDrawable != null) {
            selectorDrawable.setBounds(selectorRect);
            selectorDrawable.draw(canvas);
        }
        super.dispatchDraw(canvas);
        if (!drawSelectorBehind && !selectorRect.isEmpty() && selectorDrawable != null) {
            selectorDrawable.setBounds(selectorRect);
            selectorDrawable.draw(canvas);
        }
    }

    @Override
    public boolean verifyDrawable(@NonNull Drawable drawable) {
        return selectorDrawable == drawable || super.verifyDrawable(drawable);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateSelectorState();
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (selectorDrawable != null) {
            selectorDrawable.jumpToCurrentState();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        selectorPosition = NO_POSITION;
        selectorRect.setEmpty();
    }

    // ── Protected hooks (override points for subclasses) ─────────────────

    protected boolean allowSelectChildAtPosition(float x, float y) {
        return true;
    }

    protected boolean allowSelectChildAtPosition(View child) {
        return true;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void checkIfEmpty() {
        if (isHidden || getAdapter() == null || emptyView == null) {
            if (hiddenByEmptyView && getVisibility() != VISIBLE) {
                setVisibility(VISIBLE);
                hiddenByEmptyView = false;
            }
            return;
        }
        boolean empty = getAdapter().getItemCount() == 0 && !isFastScrollAnimationRunning();
        int newVisibility = empty ? VISIBLE : GONE;
        emptyViewAnimateToVisibility = newVisibility;
        emptyView.setVisibility(newVisibility);

        if (hideIfEmpty) {
            int listVisibility = empty ? INVISIBLE : VISIBLE;
            if (getVisibility() != listVisibility) {
                setVisibility(listVisibility);
            }
            hiddenByEmptyView = true;
        }
    }

    private void positionSelector(int position, View sel) {
        if (selectorDrawable == null) return;
        int bottomPadding = 0;
        if (getAdapter() instanceof SelectionAdapter) {
            bottomPadding = ((SelectionAdapter) getAdapter()).getSelectionBottomPadding(sel);
        }
        if (position != NO_POSITION) {
            selectorPosition = position;
        }
        selectorRect.set(sel.getLeft(), sel.getTop(), sel.getRight(), sel.getBottom() - bottomPadding);
        selectorDrawable.setBounds(selectorRect);
    }

    private void updateSelectorState() {
        if (selectorDrawable != null && selectorDrawable.isStateful()) {
            if (currentChildView != null) {
                int[] state = onCreateDrawableState(1);
                state[state.length - 1] = android.R.attr.state_pressed;
                if (selectorDrawable.setState(state)) {
                    invalidateDrawable(selectorDrawable);
                }
            } else {
                selectorDrawable.setState(StateSet.NOTHING);
            }
        }
    }

    private void clearPendingPress() {
        if (selectChildRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
            selectChildRunnable = null;
        }
        if (currentChildView != null) {
            currentChildView.setPressed(false);
            currentChildView = null;
        }
        interceptedByChild = false;
        selectorRect.setEmpty();
    }

    // ── Gesture listener for item clicks ─────────────────────────────────

    private class ItemClickGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (currentChildView != null && onItemClickListener != null) {
                final View view = currentChildView;
                final int position = currentChildPosition;
                Runnable runnable = () -> {
                    clickRunnable = null;
                    if (view != null) {
                        view.setPressed(false);
                        view.playSoundEffect(SoundEffectConstants.CLICK);
                        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                        if (position != -1 && onItemClickListener != null) {
                            onItemClickListener.onItemClick(view, position);
                        }
                    }
                };
                clickRunnable = runnable;
                AndroidUtilities.runOnUIThread(runnable, ViewConfiguration.getPressedStateDuration());

                if (selectChildRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
                    selectChildRunnable = null;
                    currentChildView = null;
                    interceptedByChild = false;
                }
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            if (currentChildView == null || currentChildPosition == -1 || onItemLongClickListener == null) {
                return;
            }
            View child = currentChildView;
            if (onItemLongClickListener.onItemClick(child, currentChildPosition)) {
                child.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                child.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            }
        }
    }

    // ── OnItemTouchListener that resolves which child was tapped ─────────

    private class ItemClickTouchListener implements OnItemTouchListener {

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
            int action = event.getActionMasked();
            boolean isIdle = getScrollState() == SCROLL_STATE_IDLE;

            if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
                    && currentChildView == null && isIdle) {
                float ex = event.getX();
                float ey = event.getY();
                if (allowSelectChildAtPosition(ex, ey)) {
                    View v = findChildViewUnder(ex, ey);
                    if (v != null && allowSelectChildAtPosition(v)) {
                        currentChildView = v;
                    }
                }
                // If the direct child is a ViewGroup, check whether a clickable
                // descendant consumed the touch — if so, clear our selection.
                if (currentChildView instanceof ViewGroup) {
                    float rx = event.getX() - currentChildView.getLeft();
                    float ry = event.getY() - currentChildView.getTop();
                    ViewGroup vg = (ViewGroup) currentChildView;
                    for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                        View child = vg.getChildAt(i);
                        if (rx >= child.getLeft() && rx <= child.getRight()
                                && ry >= child.getTop() && ry <= child.getBottom()
                                && child.isClickable()) {
                            currentChildView = null;
                            break;
                        }
                    }
                }
                currentChildPosition = -1;
                if (currentChildView != null) {
                    currentChildPosition = getChildAdapterPosition(currentChildView);
                }
            }

            if (currentChildView != null && !interceptedByChild) {
                try {
                    gestureDetector.onTouchEvent(event);
                } catch (Exception ignored) {
                }
            }

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (!interceptedByChild && currentChildView != null) {
                    float x = event.getX();
                    float y = event.getY();
                    selectChildRunnable = () -> {
                        if (selectChildRunnable != null && currentChildView != null) {
                            currentChildView.setPressed(true);
                            selectChildRunnable = null;
                        }
                    };
                    AndroidUtilities.runOnUIThread(selectChildRunnable, ViewConfiguration.getTapTimeout());
                    if (currentChildView.isEnabled()) {
                        positionSelector(currentChildPosition, currentChildView);
                        if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                            selectorDrawable.setHotspot(event.getX(), event.getY());
                        }
                        updateSelectorState();
                    } else {
                        selectorRect.setEmpty();
                    }
                } else {
                    selectorRect.setEmpty();
                }
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_POINTER_UP
                    || action == MotionEvent.ACTION_CANCEL
                    || !isIdle) {
                if (currentChildView != null) {
                    clearPendingPress();
                }
            }
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            // no-op
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            cancelClickRunnables(true);
        }
    }
}
