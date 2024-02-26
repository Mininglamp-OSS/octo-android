package com.chat.base.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.chat.base.R;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.entity.BottomSheetItem;
import com.chat.base.entity.ImagePopupBottomSheetItem;
import com.chat.base.ui.components.SecretDeleteTimer;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.lxj.xpopup.core.ImageViewerPopupView;
import com.lxj.xpopup.util.XPopupUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKMsg;

import java.util.ArrayList;
import java.util.List;

/**
 * Description:
 * Create by dance, at 2019/5/8
 */
@SuppressLint("ViewConstructor")
public class CustomImageViewerPopup extends ImageViewerPopupView {
    Context context;
    //唯一标记和图片数组长度必须一样
    private final IImgPopupMenu iImgPopupMenu;
    List<ImagePopupBottomSheetItem> list;
    private final WKMsg msg;
    private final int flame;

    public CustomImageViewerPopup(@NonNull Context context, int flame, WKMsg msg, List<ImagePopupBottomSheetItem> list, IImgPopupMenu iImgPopupMenu) {
        super(context);
        this.context = context;
        this.list = list;
        this.msg = msg;
        this.flame = flame;
        this.iImgPopupMenu = iImgPopupMenu;
        bgColor = ContextCompat.getColor(context, R.color.black);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.custom_image_viewer_popup;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        ImageView imgMoreIv = findViewById(R.id.imgMoreIv);
        imgMoreIv.setVisibility(flame == 0 ? VISIBLE : GONE);
        imgMoreIv.setOnClickListener(view -> showLongClickDialog(pager.getCurrentItem(), urls.get(pager.getCurrentItem())));
        if (list != null) {
            list.add(new ImagePopupBottomSheetItem(context.getString(R.string.save_img), R.mipmap.msg_download, new ImagePopupBottomSheetItem.IBottomSheetClick() {
                @Override
                public void onClick(int index) {
                    download(urls.get(pager.getCurrentItem()).toString());
                }
            }));
        }
        FrameLayout contentLayout = findViewById(R.id.contentLayout);
        SecretDeleteTimer deleteTimer = new SecretDeleteTimer(context);
        if (msg != null && msg.flame == 1) {
            ((Activity) context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            imgMoreIv.setVisibility(View.GONE);
            deleteTimer.setSize(25);
            deleteTimer.setDestroyTime(
                    msg.clientMsgNO,
                    msg.flameSecond,
                    msg.viewedAt,
                    false
            );
            contentLayout.addView(deleteTimer, LayoutHelper.createFrame(
                    25,
                    25,
                    Gravity.CENTER
            ));
        }
        WKIM.getInstance().getMsgManager().addOnDeleteMsgListener("view_img", deletedMsg -> {
            if (msg != null && deletedMsg != null && msg.clientMsgNO.equals(deletedMsg.clientMsgNO)) {
                dismiss();
            }
        });
    }
//    public class MyPhotoViewAdapter extends PagerAdapter {
//        @Override
//        public int getCount() {
//            return isInfinite ? Integer.MAX_VALUE / 2 : urls.size();
//        }
//
//        @Override
//        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
//            return o == view;
//        }
//
//        @NonNull
//        @Override
//        public Object instantiateItem(@NonNull ViewGroup container, int position) {
//            final PhotoView photoView = new PhotoView(container.getContext());
//            ProgressBar progressBar = buildProgressBar(container.getContext());
//            if (imageLoader != null)
//                imageLoader.loadImage(position, urls.get(isInfinite ? position % urls.size() : position), CustomImageViewerPopup.this,photoView,progressBar);
//            container.addView(photoView);
//            photoView.setOnClickListener(view -> dismiss());
//            photoView.setOnLongClickListener(v -> {
//                showLongClickDialog(position, urls.get(isInfinite ? position % urls.size() : position));
//                return true;
//            });
//            return photoView;
//        }
//
//        @Override
//        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
//            container.removeView((View) object);
//        }
//    }

    public void showLongClickDialog(int position, Object url) {

        if (list != null) {
            List<BottomSheetItem> sheetItemList = new ArrayList<>();
            for (ImagePopupBottomSheetItem item : list) {
                sheetItemList.add(new BottomSheetItem(item.getText(), item.getIcon(), () -> item.getIClick().onClick(position)));
            }
            WKDialogUtils.getInstance().showBottomSheet(getContext(), context.getString(R.string.str_choose), false, sheetItemList);
        } else {

            WKDialogUtils.getInstance().showChatImageBottomViewDialog(context, String.valueOf(url), new WKDialogUtils.IImageBottomClick() {
                @Override
                public void onForward() {
                    if (iImgPopupMenu != null)
                        iImgPopupMenu.onForward(position);
                }

                @Override
                public void onFavorite() {
                    if (iImgPopupMenu != null)
                        iImgPopupMenu.onFavorite(position);
                }

                @Override
                public void onShowInChat() {
                    if (iImgPopupMenu != null)
                        iImgPopupMenu.onShowInChat(position);
                    dismiss();
                }

                @Override
                public void onDownload() {
                    download(String.valueOf(url));
                }
            });
        }


    }

    private ProgressBar buildProgressBar(Context context) {
        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        int size = XPopupUtils.dp2px(container.getContext(), 40f);
        FrameLayout.LayoutParams params = new LayoutParams(size, size);
        params.gravity = Gravity.CENTER;
        progressBar.setLayoutParams(params);
        progressBar.setVisibility(GONE);
        return progressBar;
    }

    private void download(String url) {
        if (url.startsWith("http") || url.startsWith("HTTP")) {
            Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .into(new CustomTarget<Bitmap>(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            ImageUtils.getInstance().saveBitmap(context, resource, true, null);
                            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.saved_album));
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {

                        }
                    });
        } else {
            Bitmap bitmap = BitmapFactory.decodeFile(url);
            ImageUtils.getInstance().saveBitmap(context, bitmap, true, null);
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.saved_album));
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        WKIM.getInstance().getMsgManager().removeDeleteMsgListener("view_img");
        if (msg != null && msg.flame == 1) {
            boolean disable_screenshot;
            String uid = WKConfig.getInstance().getUid();
            if (!TextUtils.isEmpty(uid)) {
                disable_screenshot = WKSharedPreferencesUtil.getInstance().getBoolean(uid + "_disable_screenshot");
            } else {
                disable_screenshot = WKSharedPreferencesUtil.getInstance().getBoolean("disable_screenshot");
            }
            if (disable_screenshot)
                ((Activity) context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            else {
                ((Activity) context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    public interface IImgPopupMenu {
        void onForward(int position);

        void onFavorite(int position);

        void onShowInChat(int position);
    }
}
