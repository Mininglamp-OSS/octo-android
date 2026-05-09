package com.chat.uikit.chat.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;

/**
 * Discord 风格的子区连接线：左侧竖线 + 每行圆角分支
 * 自动从父容器的兄弟 View（contentWrapper → cardContainer）中计算行位置，
 * 不需要外部手动 setRowCenterYs，首帧即可正确绘制。
 */
public class ThreadBranchView extends View {

    private final Paint paint;
    private final Path path;
    private final RectF arcRect;
    private int expectedRowCount;

    private final float lineX;
    private final float endX;
    private final float radius;

    private float[] cachedCenterYs;
    private int cachedParentHeight;
    private int cachedParentWidth;
    private boolean pathDirty = true;

    public ThreadBranchView(Context context, int rowCount) {
        super(context);
        this.expectedRowCount = rowCount;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        int color = Theme.colorAccount;
        paint.setColor((color & 0x00FFFFFF) | 0x4D000000);

        path = new Path();
        arcRect = new RectF();

        lineX = AndroidUtilities.dp(40);
        endX = AndroidUtilities.dp(58);
        radius = AndroidUtilities.dp(8);
    }

    public void setRowCount(int rowCount) {
        if (this.expectedRowCount != rowCount) {
            this.expectedRowCount = rowCount;
            pathDirty = true;
            invalidateCenterYsCache();
            invalidate();
        }
    }

    public int getRowCount() {
        return expectedRowCount;
    }

    private void invalidateCenterYsCache() {
        cachedCenterYs = null;
        cachedParentHeight = 0;
        cachedParentWidth = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (expectedRowCount == 0) return;

        float[] centerYs = getCenterYs();
        if (centerYs == null || centerYs.length == 0) return;

        if (pathDirty) {
            rebuildPath(centerYs);
            pathDirty = false;
        }

        canvas.drawPath(path, paint);
    }

    private void rebuildPath(float[] centerYs) {
        path.reset();

        for (int i = 0; i < centerYs.length; i++) {
            float targetY = centerYs[i];
            float lineTop = (i == 0) ? 0 : centerYs[i - 1];

            path.moveTo(lineX, lineTop);
            path.lineTo(lineX, targetY - radius);

            arcRect.set(lineX, targetY - radius * 2, lineX + radius * 2, targetY);
            path.arcTo(arcRect, 180, -90, false);

            path.lineTo(endX, targetY);

            if (i < centerYs.length - 1) {
                path.moveTo(lineX, targetY - radius);
                path.lineTo(lineX, targetY);
            }
        }
    }

    private float[] getCenterYs() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return cachedCenterYs;
        int ph = parent.getHeight();
        int pw = parent.getWidth();
        if (ph == cachedParentHeight && pw == cachedParentWidth && cachedCenterYs != null) {
            return cachedCenterYs;
        }
        float[] newYs = computeRowCenterYs(parent);
        if (newYs != null) {
            if (!java.util.Arrays.equals(newYs, cachedCenterYs)) {
                pathDirty = true;
            }
            cachedCenterYs = newYs;
            cachedParentHeight = ph;
            cachedParentWidth = pw;
        }
        return cachedCenterYs;
    }

    private float[] computeRowCenterYs(ViewGroup parent) {
        if (parent.getChildCount() < 2) return null;

        View contentWrapperView = parent.getChildAt(1);
        if (!(contentWrapperView instanceof LinearLayout)) return null;
        LinearLayout contentWrapper = (LinearLayout) contentWrapperView;
        if (contentWrapper.getChildCount() == 0) return null;

        View cardContainerView = contentWrapper.getChildAt(0);
        if (!(cardContainerView instanceof LinearLayout)) return null;
        LinearLayout cardContainer = (LinearLayout) cardContainerView;

        java.util.List<View> rows = new java.util.ArrayList<>();
        for (int i = 0; i < cardContainer.getChildCount(); i++) {
            View child = cardContainer.getChildAt(i);
            if (child.getHeight() > AndroidUtilities.dp(5)) {
                rows.add(child);
            }
        }

        if (rows.isEmpty()) return null;

        float[] centerYs = new float[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            centerYs[i] = row.getTop() + cardContainer.getTop()
                    + contentWrapper.getTop() + row.getHeight() / 2f;
        }
        return centerYs;
    }
}
