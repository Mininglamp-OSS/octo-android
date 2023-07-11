package com.chat.base.utils;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.chat.base.WKBaseApplication;
import com.chat.base.R;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.EditMsgMenu;
import com.chat.base.endpoint.entity.ParseQrCodeMenu;
import com.chat.base.entity.AppVersion;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.ui.components.ActionBarMenuSubItem;
import com.chat.base.ui.components.ActionBarPopupWindow;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.base.views.CommonBottomView;
import com.chat.base.views.CustomImageViewerPopup;
import com.chat.base.views.InputDialogView;
import com.chat.base.views.NormalDialogView;
import com.chat.base.views.NormalSingleBtnDialogView;
import com.chat.base.views.BottomEntity;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.interfaces.XPopupCallback;
import com.lxj.xpopup.util.SmartGlideImageLoader;
import com.xinbida.wukongim.entity.WKMsg;

import java.util.List;

/**
 * 2019-11-08 11:40
 * 通用弹框
 */
public class WKDialogUtils {
    private WKDialogUtils() {
    }

    private static class DialogUtilsBinder {
        static WKDialogUtils utils = new WKDialogUtils();
    }

    public static WKDialogUtils getInstance() {
        return DialogUtilsBinder.utils;
    }

    public void showSystemDialog(Context context, String title, String content, final IClickListener iClickListener) {
        new AlertDialog.Builder(context).setTitle(title)
                .setMessage(content)
                .setPositiveButton(context.getString(R.string.sure), (dialog, which) -> iClickListener.onClick(1)).setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> iClickListener.onClick(0)).show();
    }

    public void showDialog(Context context, String msg, final IClickListener iClickListener) {
        showDialog(context, msg, true, iClickListener);
    }

    public void showSingleBtnDialog(Context context, String content, IClickListener iClickListener) {
        showSingleBtnDialog(context, "", content, "", iClickListener);
    }

    public void showSingleBtnDialog(Context context, String title, String content, String btnStr, IClickListener iClickListener) {
        new XPopup.Builder(context).dismissOnTouchOutside(true).asCustom(new NormalSingleBtnDialogView(context, title, content, btnStr, index -> {
            if (iClickListener != null)
                iClickListener.onClick(index);
        })).show();
    }

    public void showDialog(Context context, String msg, boolean isCancelable, final IClickListener iClickListener) {
        new XPopup.Builder(context).dismissOnTouchOutside(isCancelable).asCustom(new NormalDialogView(context, "", msg, "", "", index -> {
            if (iClickListener != null)
                iClickListener.onClick(index);
        })).show();

    }

    /**
     * 显示弹框
     *
     * @param context        上下文
     * @param title          标题
     * @param content        内容
     * @param cancelStr      取消按钮文字
     * @param sureStr        确定按钮文字
     * @param iClickListener 点击返回
     */
    public void showDialog(Context context, String title, String content, String cancelStr, String sureStr, final IClickListener iClickListener) {
        new XPopup.Builder(context).asCustom(new NormalDialogView(context, title, content, sureStr, cancelStr, iClickListener::onClick)).show();
    }

    public void showDialog(Context context, boolean isCancelable, String title, String content, String cancelStr, String sureStr, final IClickListener iClickListener) {
        new XPopup.Builder(context).dismissOnTouchOutside(isCancelable).asCustom(new NormalDialogView(context, title, content, sureStr, cancelStr, iClickListener::onClick)).show();
    }

    public interface IClickListener {
        void onClick(int index);
    }

    public void showImageBottomViewDialog(@NonNull Context context, String path, boolean isShowInChat, IImageBottomClick iImageBottomClick) {
        Dialog mDialog = new Dialog(context, R.style.pop_dialog);
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setCancelable(true);
        Window window = mDialog.getWindow();
        window.setGravity(Gravity.BOTTOM);
        window.setWindowAnimations(R.style.dialog_share);
        View inflate = View.inflate(context, R.layout.image_bottom_view, null);
//        Theme.setColorFilter(context, inflate.findViewById(R.id.forwardIv), R.color.popupTextColor);
//        Theme.setColorFilter(context, inflate.findViewById(R.id.favoriteIV), R.color.popupTextColor);
//        Theme.setColorFilter(context, inflate.findViewById(R.id.downloadIV), R.color.popupTextColor);
//        Theme.setColorFilter(context, inflate.findViewById(R.id.editIV), R.color.popupTextColor);
//        Theme.setColorFilter(context, inflate.findViewById(R.id.showInChatIV), R.color.popupTextColor);
//        Theme.setColorFilter(context, inflate.findViewById(R.id.qrIV), R.color.popupTextColor);
        View qrView = inflate.findViewById(R.id.qrView);
        View showInChatLayout = inflate.findViewById(R.id.showInChatLayout);
        showInChatLayout.setVisibility(isShowInChat ? VISIBLE : GONE);
        final Bitmap[] qrBitmap = new Bitmap[1];
        if (!TextUtils.isEmpty(path)) {
            new Thread(() -> Glide.with(context)
                    .asBitmap()
                    .load(path)
                    .into(new CustomTarget<Bitmap>(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            EndpointManager.getInstance().invoke("parse_qrcode", new ParseQrCodeMenu((AppCompatActivity) context, resource, false, new ParseQrCodeMenu.IResult() {
                                @Override
                                public void onResult(String codeContentStr) {
                                    ((AppCompatActivity) context).runOnUiThread(() -> {
                                        if (!TextUtils.isEmpty(codeContentStr)) {
                                            qrBitmap[0] = resource;
                                            qrView.setVisibility(VISIBLE);
                                        }
                                    });

                                }
                            }));
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    })).start();

        }
        SingleClickUtil.onSingleClick(inflate.findViewById(R.id.downloadView), view -> {
            mDialog.dismiss();
            iImageBottomClick.onDownload();
        });
        SingleClickUtil.onSingleClick(inflate.findViewById(R.id.favoriteView), view -> {
            mDialog.dismiss();
            iImageBottomClick.onFavorite();
        });
        SingleClickUtil.onSingleClick(inflate.findViewById(R.id.forwardView), view -> {
            mDialog.dismiss();
            iImageBottomClick.onForward();
        });
        SingleClickUtil.onSingleClick(inflate.findViewById(R.id.showInChatView), view -> {
            mDialog.dismiss();
            iImageBottomClick.onShowInChat();
        });
        SingleClickUtil.onSingleClick(inflate.findViewById(R.id.editView), view -> {
            EndpointManager.getInstance().invoke("editMsg", new EditMsgMenu(path, context));
            mDialog.dismiss();
        });
        SingleClickUtil.onSingleClick(qrView, view -> {
            mDialog.dismiss();
            EndpointManager.getInstance().invoke("parse_qrcode", new ParseQrCodeMenu((AppCompatActivity) context, qrBitmap[0], true, null));
        });
        inflate.findViewById(R.id.cancelTv).setOnClickListener(v -> {
            if (mDialog.isShowing()) {
                mDialog.dismiss();
            }
        });
        window.setContentView(inflate);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        mDialog.show();
    }


    /**
     * 显示通用的底部弹框
     *
     * @param context      上下文
     * @param list         显示内容
     * @param iBottomClick 点击返回
     */
    public void showCommonBottomViewDialog(@NonNull Context context, @NonNull List<BottomEntity> list, @NonNull CommonBottomView.IBottomClick iBottomClick) {
        new XPopup.Builder(context)
                .autoOpenSoftInput(false).hasShadowBg(true)
                .asCustom(new CommonBottomView(context, "", list, iBottomClick))
                .show();
    }

    public void showCommonBottomViewDialog(@NonNull Context context, String path, @NonNull List<BottomEntity> list, @NonNull CommonBottomView.IBottomClick iBottomClick) {
        new XPopup.Builder(context)
                .autoOpenSoftInput(false).hasShadowBg(true)
                .asCustom(new CommonBottomView(context, path, list, iBottomClick))
                .show();
    }

    public void showInputDialog(@NonNull Context context, String oldStr, String hideStr, int maxLength, InputDialogView.IClick iClick) {
        new XPopup.Builder(context)
                .autoOpenSoftInput(true).hasShadowBg(true)
                .asCustom(new InputDialogView(context, oldStr, hideStr, maxLength, iClick))
                .show();
    }

    /**
     * 显示版本更新弹框
     *
     * @param context
     * @param versionEntity
     */
    public void showNewVersionDialog(Context context, AppVersion versionEntity) {
        versionEntity.update_desc = versionEntity.update_desc.replaceAll("\\n", " \n ");
        View view = LayoutInflater.from(context).inflate(R.layout.act_new_version_layout, null);
        TextView contentTv = view.findViewById(R.id.contentTv);
        TextView versionTv = view.findViewById(R.id.versionTv);
        contentTv.setText(versionEntity.update_desc);
        versionTv.setText(String.format("%s：%s", context.getString(R.string.new_version), versionEntity.app_version));
        TextView cancelTv = view.findViewById(R.id.cancelTv);
        Button sureBtn = view.findViewById(R.id.sureBtn);
        //ProgressBar progressBar = view.findViewById(R.id.progressBar);
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialog);
        view.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));
        builder.setCancelable(versionEntity.is_force == 0);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        alertDialog.setContentView(view);
        Window window = alertDialog.getWindow();
        WindowManager.LayoutParams param = window.getAttributes();
        param.width = AndroidUtilities.getScreenWidth() / 5 * 4;
        if (versionEntity.is_force == 1) {
            cancelTv.setVisibility(GONE);
        }
//        param.height = WindowUtil.getInstance().getScreenHeight()/3;
        window.setAttributes(param);
//            window.decorView.setPadding(0,0,0,0)
//            window.decorView.setBackgroundColor(Color.RED)


        cancelTv.setOnClickListener(view1 -> {
            if (versionEntity.is_force == 0) {
                //非强制更新
                alertDialog.dismiss();
            }
        });
        sureBtn.setOnClickListener(view1 -> {
            DownloadApkUtils.getInstance().downloadAPK(WKBaseApplication.getInstance().getContext(),
                    versionEntity.app_version,
                    WKApiConfig.getShowUrl(versionEntity.download_url));
            alertDialog.dismiss();

        });

    }


    public interface IImageBottomClick {
        void onForward();

        void onFavorite();

        void onShowInChat();

        void onDownload();
    }

    ActionBarPopupWindow scrimPopupWindow = null;

    @SuppressLint("ClickableViewAccessibility")
    public void showPopup(float[] coordinate, View view, List<PopupMenuItem> list) {
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(view.getContext(), R.mipmap.popup_fixed_alert, 0) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                    scrimPopupWindow.dismiss(true);
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                boolean b = super.dispatchTouchEvent(ev);
                if (ev.getAction() == MotionEvent.ACTION_DOWN && !b) {
                    scrimPopupWindow.dismiss(true);
                }
                return b;
            }
        };
        final RectF rect = new RectF();
        popupLayout.setOnTouchListener(new View.OnTouchListener() {
            private final int[] pos = new int[2];

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                        View contentView = scrimPopupWindow.getContentView();
                        contentView.getLocationInWindow(pos);
                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            scrimPopupWindow.dismiss(true);
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    scrimPopupWindow.dismiss(true);
                }
                return false;
            }
        });
        scrimPopupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (scrimPopupWindow != this) {
                    return;
                }
                scrimPopupWindow = null;
            }
        };
        for (int i = 0; i < list.size(); i++) {
            PopupMenuItem item = list.get(i);
            ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(view.getContext(), false, i == 0, i == list.size() - 1);
            if (item.getIconResourceID() != 0) {
                subItem.setTextAndIcon(item.getText(), item.getIconResourceID());
            } else
                subItem.setText(item.getText());
            subItem.setTag(R.id.width_tag, 240);
            subItem.setTag(R.id.min_width_tag, 150);
            subItem.setMultiline();
            subItem.setOnClickListener(view1 -> {
                scrimPopupWindow.dismiss(true);
                item.getIClick().onClick();
            });
            popupLayout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        popupLayout.setMinimumWidth(AndroidUtilities.dp(150));
        popupLayout.setFitItems(false);

        scrimPopupWindow.setPauseNotifications(true);
        scrimPopupWindow.setDismissAnimationDuration(220);
        scrimPopupWindow.setOutsideTouchable(true);
        scrimPopupWindow.setClippingEnabled(true);
        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow.setFocusable(true);
        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);


        Runnable showMenu = () -> {
            if (scrimPopupWindow == null) {
                return;
            }
            scrimPopupWindow.showAtLocation(view, Gravity.START | Gravity.TOP, Math.round(coordinate[0]), Math.round(coordinate[1]));
        };
        showMenu.run();
    }

    public synchronized void showScreenPopup(View view, float[] coordinate, List<PopupMenuItem> list) {
        showScreenPopup(view, coordinate, list, null);
    }

    public interface IScreenPopupDismiss {
        void onDismiss();
    }

    @SuppressLint("ClickableViewAccessibility")
    public synchronized void showScreenPopup(View view, float[] coordinate, List<PopupMenuItem> list, final IScreenPopupDismiss iScreenPopupDismiss) {
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(view.getContext(), R.mipmap.popup_fixed_alert, 0) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                    scrimPopupWindow.dismiss(true);
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                boolean b = super.dispatchTouchEvent(ev);
                if (ev.getAction() == MotionEvent.ACTION_DOWN && !b && scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss(true);
                }
                return b;
            }
        };
        final RectF rect = new RectF();
        popupLayout.setOnTouchListener(new View.OnTouchListener() {
            private final int[] pos = new int[2];

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                        View contentView = scrimPopupWindow.getContentView();
                        contentView.getLocationInWindow(pos);
                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            scrimPopupWindow.dismiss(true);
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    scrimPopupWindow.dismiss(true);
                }
                return false;
            }
        });


        scrimPopupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (iScreenPopupDismiss != null) {
                    iScreenPopupDismiss.onDismiss();
                }
                if (scrimPopupWindow != this) {
                    return;
                }
                scrimPopupWindow = null;
            }
        };
        for (int i = 0; i < list.size(); i++) {
            PopupMenuItem item = list.get(i);
            ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(view.getContext(), false, i == 0, i == list.size() - 1);
            subItem.setTextAndIcon(item.getText(), item.getIconResourceID());
            subItem.setTag(R.id.width_tag, 240);
            subItem.setTag(R.id.min_width_tag, 150);
            subItem.setMultiline();
            subItem.setOnClickListener(view1 -> {
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss(true);
                    item.getIClick().onClick();
                }
            });
            popupLayout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        popupLayout.setMinimumWidth(AndroidUtilities.dp(150));
        popupLayout.setFitItems(false);

        scrimPopupWindow.setPauseNotifications(true);
        scrimPopupWindow.setDismissAnimationDuration(220);
        scrimPopupWindow.setOutsideTouchable(true);
        scrimPopupWindow.setClippingEnabled(true);
        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow.setFocusable(true);
        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);


        Runnable showMenu = () -> {
            if (scrimPopupWindow == null) {
                return;
            }
            scrimPopupWindow.showAtLocation(view, Gravity.TOP | Gravity.START, Math.round(coordinate[0]), Math.round(coordinate[1]));
        };
        showMenu.run();
    }

    public interface IImagePopupListener {
        void onShow();

        void onDismiss();
    }

    public BasePopupView showImagePopup(Context context, List<Object> tempImgList, List<ImageView> imgList, ImageView imageView, int index, List<BottomEntity> list, final CustomImageViewerPopup.IImgPopupMenu iImgPopupMenu, final IImagePopupListener iImagePopupListener) {
        return showImagePopup(context, null, tempImgList, imgList, imageView, index, list, iImgPopupMenu, iImagePopupListener);
    }

    public BasePopupView showImagePopup(Context context, WKMsg msg, List<Object> tempImgList, List<ImageView> imgList, ImageView imageView, int index, List<BottomEntity> list, final CustomImageViewerPopup.IImgPopupMenu iImgPopupMenu, final IImagePopupListener iImagePopupListener) {
        Object o = imageView.getTag();
        int flame = 0;
        if (o != null) {
            flame = (int) o;
        }

        int finalFlame = flame;
        CustomImageViewerPopup viewerPopup = new CustomImageViewerPopup(context,flame, msg, true, list, iImgPopupMenu);
        //自定义的ImageViewer弹窗需要自己手动设置相应的属性，必须设置的有srcView，url和imageLoader。
        viewerPopup.setSrcView(imageView, index);
        viewerPopup.setImageUrls(tempImgList);
        viewerPopup.setXPopupImageLoader(new SmartGlideImageLoader());
        viewerPopup.isShowIndicator(true);//是否显示页码指示器
        viewerPopup.isShowPlaceholder(true);//是否显示白色占位块
        viewerPopup.isShowSaveButton(false);//是否显示保存按钮


        viewerPopup.setLongPressListener((popupView, position) -> {
            if (finalFlame == 1) return;
            viewerPopup.showLongClickDialog(position, tempImgList.get(position));
        });
        viewerPopup.setSrcViewUpdateListener((popupView, position) -> popupView.updateSrcView(imgList.get(position)));
        new XPopup.Builder(context).setPopupCallback(new XPopupCallback() {
            @Override
            public void onCreated(BasePopupView popupView) {

            }

            @Override
            public void beforeShow(BasePopupView popupView) {

            }

            @Override
            public void onShow(BasePopupView popupView) {
                if (iImagePopupListener != null)
                    iImagePopupListener.onShow();
            }

            @Override
            public void onDismiss(BasePopupView popupView) {
                if (iImagePopupListener != null)
                    iImagePopupListener.onDismiss();
            }

            @Override
            public void beforeDismiss(BasePopupView popupView) {

            }

            @Override
            public boolean onBackPressed(BasePopupView popupView) {
                return false;
            }

            @Override
            public void onKeyBoardStateChanged(BasePopupView popupView, int height) {

            }

            @Override
            public void onDrag(BasePopupView popupView, int value, float percent, boolean upOrLeft) {

            }

            @Override
            public void onClickOutside(BasePopupView popupView) {

            }
        }).asCustom(viewerPopup).show();
        return viewerPopup;
    }
}
