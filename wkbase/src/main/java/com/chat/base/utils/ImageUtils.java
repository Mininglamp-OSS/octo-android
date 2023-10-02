package com.chat.base.utils;

import static com.bumptech.glide.request.target.Target.SIZE_ORIGINAL;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConstants;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 2019-12-07 17:24
 */
public class ImageUtils {
    private ImageUtils() {
    }

    private static class ImageUtilsBinder {
        private static final ImageUtils imageUtils = new ImageUtils();
    }

    public static ImageUtils getInstance() {
        return ImageUtilsBinder.imageUtils;
    }

    /**
     * 获取图片显示在聊天的高宽
     *
     * @param pic_width  图片宽度
     * @param pic_height 图片高度
     * @return 显示在UI上的高宽
     */
    public int[] getImageWidthAndHeightToTalk(int pic_width, int pic_height) {
        int w;
        int h;
        int[] w_h = new int[2];
        int screeWidth = AndroidUtilities.getScreenWidth() / 2;
        int screeHeight = AndroidUtilities.getScreenHeight() / 4;
        if (pic_width == pic_height) {
            w = screeWidth;
            h = screeWidth;
        } else {
            float rs;
            if (pic_width > pic_height && pic_width >= screeWidth) {
                w = screeWidth;
                rs = (float) pic_width / (float) screeWidth;
                h = (int) ((float) pic_height / rs);
                if (h < AndroidUtilities.getScreenWidth() / 6) {
                    h = AndroidUtilities.getScreenHeight() / 6;
                }
            } else if (pic_height > pic_width && pic_height > screeHeight) {
                h = screeHeight;
                rs = (float) pic_height / (float) screeHeight;
                w = (int) ((float) pic_width / rs);
                if (w < AndroidUtilities.getScreenWidth() / 6) {
                    w = AndroidUtilities.getScreenHeight() / 6;
                }
            } else {
                w = screeWidth;
                h = screeHeight;
            }
        }

        if (w < AndroidUtilities.dp(120)) {
            float dis = (float) w / AndroidUtilities.dp(120);
            h = (int) ((1 + (1 - dis)) * h);
            w = AndroidUtilities.dp(120);
        }
        w_h[0] = w;
        w_h[1] = h;
        if (w_h[0] < AndroidUtilities.dp(130f)) {
            w_h[0] = AndroidUtilities.dp(130f);
        }
        return w_h;
    }


    public int[] getImgWidthAndHeightToDynamic(Context context, String width, String height) {

        int h = Integer.parseInt(height);
        int w = Integer.parseInt(width);

        if (w > h) {
            //横图
            float p = (float) h / w;
            if (w > AndroidUtilities.dp(211)) {
                w = AndroidUtilities.dp(211);
            }

            h = (int) (w * p);
        } else {
            //竖图
            float p = (float) w / h;
            if (h > AndroidUtilities.dp(258)) {
                h = AndroidUtilities.dp(258);
            }
            w = (int) (h * p);
        }

        if (w < AndroidUtilities.getScreenWidth() / 5)
            w = AndroidUtilities.getScreenWidth() / 5;
        if (h < AndroidUtilities.getScreenHeight() / 6)
            h = AndroidUtilities.getScreenHeight() / 6;
        return new int[]{w, h};
    }

    public void downloadImg(Context context,String url, final IDownloadImgListener iDownloadImgListener) {
        Glide.with(context).asBitmap().load(url).into(new CustomTarget<Bitmap>(SIZE_ORIGINAL, SIZE_ORIGINAL) {

            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                iDownloadImgListener.onResult(resource);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {

            }
        });
    }

    public interface IDownloadImgListener {
        void onResult(Bitmap bitmap);
    }


    /**
     * 保存bitmap图片
     */
    public void saveBitmap(Context context, Bitmap bitmap, boolean isRefresh, ISave iSave) {

        String fileName = System.currentTimeMillis() + ".jpg";
        File file = new File(WKConstants.imageDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (iSave != null)
            iSave.onResult(file.getAbsolutePath());
        if (isRefresh) {
            try {
                MediaStore.Images.Media.insertImage(context.getContentResolver(),
                        file.getAbsolutePath(), fileName, null);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(file.getAbsolutePath())));

        }
    }

    public interface ISave {
        void onResult(String path);
    }


    /**
     * view 转bitmap
     *
     * @param view
     * @return
     */
    public Bitmap loadBitmapFromView(View view) {
        Bitmap shareBitmap = Bitmap.createBitmap(view.getMeasuredWidth(),
                view.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(shareBitmap);
        view.draw(c);
        return shareBitmap;

    }


    private void layoutView(View v, int width, int height) {
        v.layout(0, 0, width, height);
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST);/** 当然，measure完后，并不会实际改变View的尺寸，需要调用View.layout方法去进行布局。* 按示例调用layout函数后，View的大小将会变成你想要设置成的大小。*/
        v.measure(measuredWidth, height);
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
    }

    public Bitmap viewSaveToBitmap(View view, int height) {
        layoutView(view, AndroidUtilities.getScreenWidth(), height);
        // 把一个View转换成图片
        Bitmap cachebmp = loadBitmapFromView1(view, height);
        cachebmp.setHeight(height);
        view.destroyDrawingCache();
        return cachebmp;
    }

    private Bitmap loadBitmapFromView1(View v, int height) {
        int w = AndroidUtilities.getScreenWidth();
        int h = height;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        c.drawColor(Color.WHITE);
        /** 如果不设置canvas画布为白色，则生成透明 */

        v.layout(0, 0, w, h);
        v.draw(c);

        return bmp;
    }

    public String getNewestPhoto() {
        Cursor cursor = null;
        try {
            long currentTime = System.currentTimeMillis() / 1000 - 60;
            cursor = WKBaseApplication.getInstance().getContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA},
                    MediaStore.Images.Media.DATE_ADDED + " >= ?", new String[]{currentTime + ""}, MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

}
