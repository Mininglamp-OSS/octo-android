package com.chat.base.glide;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.chat.base.R;
import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.EditImgMenu;
import com.chat.base.utils.WKLogUtils;
import com.luck.picture.lib.animators.AnimationType;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.interfaces.OnResultCallbackListener;
import com.luck.picture.lib.style.BottomNavBarStyle;
import com.luck.picture.lib.style.PictureSelectorStyle;
import com.luck.picture.lib.style.PictureWindowAnimationStyle;
import com.luck.picture.lib.style.SelectMainStyle;
import com.luck.picture.lib.style.TitleBarStyle;
import com.luck.picture.lib.utils.DensityUtil;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;

/**
 * 2019-12-02 13:52
 * glide管理
 */
public class GlideUtils {
    private GlideUtils() {

    }

    private static class GlideUtilsBinder {
        private final static GlideUtils glideUtils = new GlideUtils();
    }

    public static GlideUtils getInstance() {
        return GlideUtilsBinder.glideUtils;
    }

    public void showImg(Context mContext, String url, ImageView imageView) {
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    Glide.with(context).load(url)
                            .apply(GlideRequestOptions.getInstance().normalRequestOption())
                            .into(imageView);
                }
            }
        }
    }

    public void showGif(Context mContext, String url, ImageView imageView, final ILoadGIFRequestListener iLoadGIFRequestListener) {
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    Glide.with(context).asGif().load(url).listener(new RequestListener<>() {
                                @Override
                                public boolean onLoadFailed(@Nullable @org.jetbrains.annotations.Nullable GlideException e, Object model, Target<GifDrawable> target, boolean isFirstResource) {
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(GifDrawable resource, Object model, Target<GifDrawable> target, DataSource dataSource, boolean isFirstResource) {
                                    if (iLoadGIFRequestListener != null) {
                                        iLoadGIFRequestListener.onSuccess();
                                    }
                                    return false;
                                }
                            })
                            .apply(GlideRequestOptions.getInstance().normalRequestOption())
                            .into(imageView);
                }
            }
        }
    }

    public interface ILoadGIFRequestListener {
        void onSuccess();
    }

    public void showImg(Context mContext, String url, int width, int height, ImageView imageView) {
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    Glide.with(context).load(url)
                            .apply(GlideRequestOptions.getInstance().normalRequestOption(width, height))
                            .into(imageView);
                }
            }
        }
    }

    public void showAvatarImg(Context mContext, String channelID, byte channelType, String key, ImageView imageView) {
        String url = WKApiConfig.getShowAvatar(channelID, channelType);
        showAvatarImg(mContext, url, key, imageView);
    }

    public void showAvatarImg(Context mContext, String url, String key, ImageView imageView) {
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    if (TextUtils.isEmpty(key)) {
                        Glide.with(context).load(url).dontAnimate()
                                .apply(GlideRequestOptions.getInstance().normalRequestOption())
                                .into(imageView);
                    } else {
                        Glide.with(context).load(new MyGlideUrlWithId(url,key)).dontAnimate()
                                .apply(GlideRequestOptions.getInstance().normalRequestOption())
                                .into(imageView);
                    }

                }
            }
        }
    }

    public interface ISelectBack {
        void onBack(List<ChooseResult> paths);

        void onCancel();
    }

    public void chooseIMG(Activity activity, int maxSelectNum, final ISelectBack iSelectBack) {
        chooseIMG(activity, maxSelectNum, false, ChooseMimeType.all, true, iSelectBack);
    }

    private SelectMainStyle getMainStyle(Context context) {
        SelectMainStyle numberSelectMainStyle = new SelectMainStyle();
        numberSelectMainStyle.setSelectNumberStyle(true);
        numberSelectMainStyle.setPreviewSelectNumberStyle(false);
        numberSelectMainStyle.setPreviewDisplaySelectGallery(true);
        numberSelectMainStyle.setSelectBackground(R.drawable.ps_default_num_selector);
        numberSelectMainStyle.setPreviewSelectBackground(R.drawable.ps_preview_checkbox_selector);
        numberSelectMainStyle.setSelectNormalBackgroundResources(R.drawable.ps_select_complete_normal_bg);
        numberSelectMainStyle.setSelectNormalTextColor(ContextCompat.getColor(context, R.color.ps_color_53575e));
        numberSelectMainStyle.setSelectNormalText(context.getString(R.string.ps_send));
        numberSelectMainStyle.setAdapterPreviewGalleryBackgroundResource(R.drawable.ps_preview_gallery_bg);
        numberSelectMainStyle.setAdapterPreviewGalleryItemSize(DensityUtil.dip2px(context, 52));
        numberSelectMainStyle.setPreviewSelectText(context.getString(R.string.ps_select));
        numberSelectMainStyle.setPreviewSelectTextSize(14);
        numberSelectMainStyle.setPreviewSelectTextColor(ContextCompat.getColor(context, R.color.ps_color_white));
        numberSelectMainStyle.setPreviewSelectMarginRight(DensityUtil.dip2px(context, 6));
        numberSelectMainStyle.setSelectBackgroundResources(R.drawable.ps_select_complete_bg);
        numberSelectMainStyle.setSelectText(context.getString(R.string.ps_send_num));
        numberSelectMainStyle.setSelectTextColor(ContextCompat.getColor(context, R.color.ps_color_white));
        numberSelectMainStyle.setMainListBackgroundColor(ContextCompat.getColor(context, R.color.ps_color_black));
        numberSelectMainStyle.setCompleteSelectRelativeTop(true);
        numberSelectMainStyle.setPreviewSelectRelativeBottom(true);
        numberSelectMainStyle.setAdapterItemIncludeEdge(false);
        return numberSelectMainStyle;
    }

    private TitleBarStyle getTitleBarStyle() {
        // 头部TitleBar 风格
        TitleBarStyle numberTitleBarStyle = new TitleBarStyle();
        numberTitleBarStyle.setHideCancelButton(true);
        numberTitleBarStyle.setAlbumTitleRelativeLeft(true);
        numberTitleBarStyle.setTitleAlbumBackgroundResource(R.drawable.ps_album_bg);
        numberTitleBarStyle.setTitleDrawableRightResource(R.drawable.ps_ic_grey_arrow);
        numberTitleBarStyle.setPreviewTitleLeftBackResource(R.drawable.ps_ic_normal_back);
        return numberTitleBarStyle;
    }

    private BottomNavBarStyle getBottomNavBarStyle(Context context) {
        // 底部NavBar 风格
        BottomNavBarStyle numberBottomNavBarStyle = new BottomNavBarStyle();
        numberBottomNavBarStyle.setBottomPreviewNarBarBackgroundColor(ContextCompat.getColor(context, R.color.ps_color_half_grey));
        numberBottomNavBarStyle.setBottomPreviewNormalText(context.getString(R.string.ps_preview));
        numberBottomNavBarStyle.setBottomPreviewNormalTextColor(ContextCompat.getColor(context, R.color.ps_color_9b));
        numberBottomNavBarStyle.setBottomPreviewNormalTextSize(16);
        numberBottomNavBarStyle.setCompleteCountTips(false);
        numberBottomNavBarStyle.setBottomPreviewSelectText(context.getString(R.string.ps_preview_num));
        numberBottomNavBarStyle.setBottomPreviewSelectTextColor(ContextCompat.getColor(context, R.color.ps_color_white));
        return numberBottomNavBarStyle;
    }

    public void chooseIMG(Activity activity, int maxSelectNum, boolean isCamera, ChooseMimeType mimeType, boolean isWithSelectVideoImage, final ISelectBack iSelectBack) {
        if (isCamera) WKBaseApplication.getInstance().disconnect = false;
        PictureSelectorStyle selectorStyle = new PictureSelectorStyle();
        selectorStyle.setTitleBarStyle(getTitleBarStyle());
        selectorStyle.setBottomBarStyle(getBottomNavBarStyle(activity));
        selectorStyle.setSelectMainStyle(getMainStyle(activity));
        PictureWindowAnimationStyle animationStyle = new PictureWindowAnimationStyle();
        animationStyle.setActivityEnterAnimation(R.anim.ps_anim_up_in);
        animationStyle.setActivityExitAnimation(R.anim.ps_anim_down_out);
        selectorStyle.setWindowAnimationStyle(animationStyle);
        PictureSelector.create(activity).openGallery(mimeType == ChooseMimeType.all ? SelectMimeType.ofAll() : SelectMimeType.ofImage()).setImageEngine(GlideEngine.createGlideEngine())
                .isPageStrategy(true, 20)
                .setSelectorUIStyle(selectorStyle)
                .setOfAllCameraType(mimeType == ChooseMimeType.all ? SelectMimeType.ofAll() : SelectMimeType.ofImage())
                .setRecyclerAnimationMode(AnimationType.SLIDE_IN_BOTTOM_ANIMATION)
//                .isWithVideoImage(false)
                .isMaxSelectEnabledMask(true)
                .isPreviewVideo(true)
                .setMaxSelectNum(maxSelectNum)
                .setMaxVideoSelectNum(maxSelectNum)
                .setImageSpanCount(3)
                .isWithSelectVideoImage(isWithSelectVideoImage)
//                .setReturnEmpty(true)
//                .DisplayOriginalSize(true)
//                .setEditorImage(false)
                .isDisplayCamera(isCamera)
                .isPreviewImage(true)
//                .isZoomAnim(true)
//                .isEnableCrop(false)
//                .isCompress(true)
                .isOriginalControl(true).setEditMediaInterceptListener((fragment, currentLocalMedia, requestCode) -> {
                    WKBaseApplication.getInstance().disconnect = true;
                    EndpointManager.getInstance().invoke("edit_img", new EditImgMenu(null, false, currentLocalMedia.getRealPath(), fragment, requestCode, (bitmap, path) -> {

                    }));
                })
                .isGif(true).forResult(new OnResultCallbackListener<>() {
                    @Override
                    public void onResult(ArrayList<LocalMedia> result) {
                        WKBaseApplication.getInstance().disconnect = true;
                        List<ChooseResult> list = new ArrayList<>();
                        for (LocalMedia media : result) {
                            String path;
                            ChooseResult chooseResult = new ChooseResult();
                            if (media.isCut() && !media.isCompressed()) {
                                // 裁剪过
                                path = media.getCutPath();
                            } else if (media.isCut() || media.isCompressed()) {
                                // 压缩过,或者裁剪同时压缩过,以最终压缩过图片为准
                                path = media.getCompressPath();
                            } else {
                                if (media.isToSandboxPath()) {
                                    path = media.getSandboxPath();
                                } else
                                    // 原图
                                    path = media.getRealPath();
                            }
                            // int mediaType = PictureMimeType.getMimeType(media.getMimeType());
                            if (PictureMimeType.isHasVideo(media.getMimeType())) {
                                chooseResult.model = ChooseResultModel.video;
                                chooseResult.path = media.getRealPath();
                                //  chooseResult.path = TextUtils.isEmpty(media.) ? media.getPath() : media.getAndroidQToPath();
                                if (PictureMimeType.isContent(chooseResult.path)) {
                                    chooseResult.path = getRealPathFromUri(activity, Uri.parse(chooseResult.path));
                                }
                            } else {
                                chooseResult.model = ChooseResultModel.image;
                                chooseResult.path = path;
                            }
                            WKLogUtils.e(path);
                            list.add(chooseResult);
                        }
                        iSelectBack.onBack(list);
                    }

                    @Override
                    public void onCancel() {
                        WKBaseApplication.getInstance().disconnect = true;
                        iSelectBack.onCancel();
                    }
                });
    }

    private String getRealPathFromUri(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void compressImg(Context context, String path, final ICompressListener icompressListener) {
        List<String> list = new ArrayList<>();
        list.add(path);
        compressImg(context, list, icompressListener);
    }

    private final List<File> files = new ArrayList<>();
    private int errCount = 0;

    /**
     * 压缩图片
     *
     * @param context context
     * @param paths   图片本地地址
     */
    public void compressImg(Context context, List<String> paths, final ICompressListener iCompressListener) {
        files.clear();
        errCount = 0;
        Luban.with(context)
                .load(paths)
                .ignoreBy(100)
                .setTargetDir(WKConstants.imageDir)
                .filter(path -> !(TextUtils.isEmpty(path) || path.toLowerCase().endsWith(".gif")))
                .setCompressListener(new OnCompressListener() {
                    @Override
                    public void onStart() {
                    }

                    @Override
                    public void onSuccess(int index, File file) {
                        if (file != null) files.add(file);
                        if ((files.size() + errCount) == paths.size()) {
                            iCompressListener.onResult(files);
                        }
                    }

                    @Override
                    public void onError(int index, Throwable e) {
                        errCount++;
                        if ((files.size() + errCount) == paths.size()) {
                            iCompressListener.onResult(files);
                        }
                    }
                }).launch();

    }

    public interface ICompressListener {
        void onResult(List<File> files);
    }
}
