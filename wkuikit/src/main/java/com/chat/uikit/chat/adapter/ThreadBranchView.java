package com.chat.uikit.chat.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;

/**
 * Discord 风格的子区连接线：左侧竖线 + 每行圆角分支
 * 非最后一行: ├ 形（竖线继续 + 圆角分支向右）
 * 最后一行:   └ 形（竖线结束 + 圆角分支向右）
 */
public class ThreadBranchView extends View {

    private final Paint paint;
    private final Path path;
    private int rowCount;
    private float[] rowCenterYs;

    private final float lineX;
    private final float endX;
    private final float radius;

    public ThreadBranchView(Context context, int rowCount) {
        super(context);
        this.rowCount = rowCount;
        this.rowCenterYs = new float[rowCount];

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        int color = Theme.colorAccount;
        paint.setColor((color & 0x00FFFFFF) | 0x4D000000);

        path = new Path();

        lineX = AndroidUtilities.dp(40);
        endX = AndroidUtilities.dp(58);
        radius = AndroidUtilities.dp(8);
    }

    public void setRowCenterYs(float[] centerYs) {
        this.rowCenterYs = centerYs;
        this.rowCount = centerYs.length;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (rowCount == 0 || rowCenterYs == null) return;

        path.reset();

        for (int i = 0; i < rowCount; i++) {
            float targetY = rowCenterYs[i];
            boolean isLast = (i == rowCount - 1);

            // 竖线起点
            float lineTop = (i == 0) ? 0 : rowCenterYs[i - 1];

            // 竖线：从起点画到弯角上方
            path.moveTo(lineX, lineTop);
            path.lineTo(lineX, targetY - radius);

            // 圆角弧形：└ 弯曲向右
            RectF arcRect = new RectF(lineX, targetY - radius * 2, lineX + radius * 2, targetY);
            path.arcTo(arcRect, 180, -90, false);

            // 水平线到卡片左边缘
            path.lineTo(endX, targetY);

            // 非最后一行：竖线继续穿过弯角区域（├ 效果）
            if (!isLast) {
                path.moveTo(lineX, targetY - radius);
                path.lineTo(lineX, targetY);
            }
        }

        canvas.drawPath(path, paint);
    }
}
