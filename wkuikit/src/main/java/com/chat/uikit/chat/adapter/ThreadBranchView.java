package com.chat.uikit.chat.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;

/**
 * 绘制从头像区域到子区行的弧形分支线（参考 iOS WKThreadBranchView）
 */
public class ThreadBranchView extends View {

    private final Paint paint;
    private final Path path;
    private int rowCount;
    private float[] rowCenterYs;

    // 分支线起始 X（头像中心）
    private final float startX;
    // 分支线终止 X（卡片左边缘）
    private final float endX;

    public ThreadBranchView(Context context, int rowCount) {
        super(context);
        this.rowCount = rowCount;
        this.rowCenterYs = new float[rowCount];

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        // 使用主题色，alpha 0.3
        int color = Theme.colorAccount;
        paint.setColor((color & 0x00FFFFFF) | 0x4D000000); // alpha ~0.3

        path = new Path();

        // 头像 60dp，中心 30dp；容器 paddingStart 对齐头像区域
        startX = AndroidUtilities.dp(30);
        endX = AndroidUtilities.dp(58);
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
            // 从顶部中心向下弯曲到目标行
            path.moveTo(startX, 0);
            path.quadTo(startX, targetY, endX, targetY);
        }
        canvas.drawPath(path, paint);
    }
}
