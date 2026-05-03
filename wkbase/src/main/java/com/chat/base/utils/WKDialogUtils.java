package com.chat.base.utils;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.chat.base.R;
import com.chat.base.foldable.PaneMetrics;
import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.EditMsgMenu;
import com.chat.base.endpoint.entity.ParseQrCodeMenu;
import com.chat.base.entity.AppVersion;
import com.chat.base.entity.BottomSheetItem;
import com.chat.base.entity.ImagePopupBottomSheetItem;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.ui.components.ActionBarMenuSubItem;
import com.chat.base.ui.components.ActionBarPopupWindow;
import com.chat.base.ui.components.AlertDialog;
import com.chat.base.ui.components.BottomSheet;
import com.chat.base.ui.components.CheckBoxSquare;
import com.chat.base.ui.components.FlatCheckBox;
import com.chat.base.views.CustomImageViewerPopup;
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

    public void showSingleBtnDialog(Context context, String title, String content, String btnStr, IClickListener iClickListener) {
        if (TextUtils.isEmpty(title)) {
            title = context.getString(R.string.str_base_tips);
        }
        if (TextUtils.isEmpty(btnStr)) {
            btnStr = context.getString(R.string.sure);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(content);
        builder.setPositiveButton(btnStr, (dialog, which) -> {
            if (iClickListener != null) {
                iClickListener.onClick(1);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setBlurParams(1f, true, true);
        builder.show();
        TextView positiveTv = (TextView) dialog.getButton(Dialog.BUTTON_POSITIVE);
        positiveTv.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
    }

    public void showDialog(Context context, String title, CharSequence msg, boolean isCancelable, String canStr, String sureStr, int canColor, int sureColor, final IClickListener iClickListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (TextUtils.isEmpty(title)) {
            builder.setTitle(context.getString(R.string.str_base_tips));
        } else {
            builder.setTitle(title);
        }
        builder.setMessage(msg);
        if (TextUtils.isEmpty(canStr)) {
            canStr = context.getString(R.string.cancel);
        }
        if (TextUtils.isEmpty(sureStr)) {
            sureStr = context.getString(R.string.sure);
        }
        builder.setNegativeButton(canStr, (dialog, which) -> {
                    if (iClickListener != null) {
                        iClickListener.onClick(0);
                    }
                }
        );
        builder.setPositiveButton(sureStr, (dialog, which) -> {
            if (iClickListener != null) {
                iClickListener.onClick(1);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setBlurParams(1f, true, true);
        dialog.setCanceledOnTouchOutside(isCancelable);
        dialog.show();
        TextView textView = (TextView) dialog.getButton(Dialog.BUTTON_NEGATIVE);
        if (canColor == 0) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.colorAccentUn));
        } else {
            textView.setTextColor(canColor);
        }
        TextView positiveTv = (TextView) dialog.getButton(Dialog.BUTTON_POSITIVE);
        if (sureColor == 0) {
            positiveTv.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
        } else {
            positiveTv.setTextColor(sureColor);
        }
    }

    public interface IClickListener {
        void onClick(int index);
    }

    public void showChatImageBottomViewDialog(@NonNull Context context, String path, IImageBottomClick iImageBottomClick) {
        final Bitmap[] qrBitmap = new Bitmap[1];
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false);
        builder.setApplyBottomPadding(false);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setGravity(Gravity.CENTER);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.screen_bg));
        TextView textView = new TextView(context);
        textView.setText(R.string.str_choose);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        textView.setTextColor(ContextCompat.getColor(context, R.color.popupTextColor));
        linearLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START | Gravity.TOP, 0, 8, 0, 0));
        // 转发
        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(context, 0);
        cell.setBackground(ContextCompat.getDrawable(context, R.drawable.layout_bg));
        cell.setTextAndIcon(context.getString(R.string.forward), R.mipmap.msg_forward, null, false);
        contentLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START | Gravity.TOP, 0, 0, 0, 0));
        cell.setOnClickListener(v -> {
            builder.getDismissRunnable().run();
            iImageBottomClick.onForward();
        });
        // 收藏
        BottomSheet.BottomSheetCell favoriteCell = new BottomSheet.BottomSheetCell(context, 0);
        favoriteCell.setBackground(ContextCompat.getDrawable(context, R.drawable.layout_bg));
        favoriteCell.setTextAndIcon(context.getString(R.string.favorite), R.mipmap.msg_fave, null, false);
        contentLayout.addView(favoriteCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START | Gravity.TOP, 0, 0, 0, 0));
        favoriteCell.setOnClickListener(v -> {
            builder.getDismissRunnable().run();
            iImageBottomClick.onFavorite();
        });
        // 下载
        BottomSheet.BottomSheetCell downloadCell = new BottomSheet.BottomSheetCell(context, 0);
        downloadCell.setBackground(ContextCompat.getDrawable(context, R.drawable.layout_bg));
        downloadCell.setTextAndIcon(context.getString(R.string.save_img), R.mipmap.msg_download, null, false);
        contentLayout.addView(downloadCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START | Gravity.TOP, 0, 0, 0, 0));
        downloadCell.setOnClickListener(v -> {
            builder.getDismissRunnable().run();
            iImageBottomClick.onDownload();
        });
        // 编辑
        BottomSheet.BottomSheetCell editCell = new BottomSheet.BottomSheetCell(context, 0);
        editCell.setBackground(ContextCompat.getDrawable(context, R.drawable.layout_bg));
        editCell.setTextAndIcon(context.getString(R.string.str_edit), R.mipmap.msg_edit, null, false);
        contentLayout.addView(editCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START | Gravity.TOP, 0, 0, 0, 0));
        editCell.setOnClickListener(v -> {
            EndpointManager.getInstance().invoke("editMsg", new EditMsgMenu(path, context));
            builder.getDismissRunnable().run();
        });
        // 编辑
        BottomSheet.BottomSheetCell showInChatCell = new BottomSheet.BottomSheetCell(context, 0);
        showInChatCell.setBackground(ContextCompat.getDrawable(context, R.drawable.layout_bg));
        showInChatCell.setTextAndIcon(context.getString(R.string.show_in_chat), R.mipmap.msg_message, null, false);
        contentLayout.addView(showInChatCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START | Gravity.TOP, 0, 0, 0, 0));
        showInChatCell.setOnClickListener(v -> {
            builder.getDismissRunnable().run();
            iImageBottomClick.onShowInChat();
        });
        // 识别图中二维码
        BottomSheet.BottomSheetCell qrCodeCell = new BottomSheet.BottomSheetCell(context, 0);
        qrCodeCell.setBackground(ContextCompat.getDrawable(context, R.drawable.layout_bg));
        qrCodeCell.setTextAndIcon(context.getString(R.string.scan_qr_code), R.mipmap.menu_scan, null, false);
        contentLayout.addView(qrCodeCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START | Gravity.TOP, 0, 0, 0, 0));
        qrCodeCell.setOnClickListener(v -> {
            builder.getDismissRunnable().run();
            EndpointManager.getInstance().invoke("parse_qrcode", new ParseQrCodeMenu((AppCompatActivity) context, qrBitmap[0], true, null));
        });
        qrCodeCell.setVisibility(GONE);
        if (!TextUtils.isEmpty(path)) {
            // YUJ-283 P-11: AppExecutors.io() 取代 new Thread()，Glide 解码属 I/O
            AppExecutors.io().execute(() -> Glide.with(context)
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
                                            qrCodeCell.setVisibility(VISIBLE);
                                        }
                                    });

                                }
                            }));
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    }));
//
        }


        linearLayout.addView(contentLayout);
        builder.setCustomView(linearLayout);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setBackgroundColor(ContextCompat.getColor(context, R.color.screen_bg));
    }

    public void showBottomSheet(Context context, CharSequence title, boolean bigTitle, List<BottomSheetItem> list) {
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false);
        builder.setDimBehind(true);
        builder.setTitle(title, bigTitle);
        CharSequence[] items = new CharSequence[list.size()];
        int[] icons = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            items[i] = list.get(i).getText();
            icons[i] = list.get(i).getIcon();
        }
        builder.setItems(items, icons, (dialogInterface, i) -> list.get(i).getIClick().onClick());
        BottomSheet bottomSheet = builder.create();
        bottomSheet.show();
        bottomSheet.setCanceledOnTouchOutside(true);
        bottomSheet.setBackgroundColor(ContextCompat.getColor(context, R.color.screen_bg));

    }

    public interface ICheckBoxDialog {
        void onClick(int index, boolean isChecked);
    }

    public void showCheckBoxDialog(@NonNull Context context, String title, CharSequence msg, CharSequence checkBoxText, boolean isCancelable, String canStr, String sureStr, int canColor, int sureColor, final ICheckBoxDialog iClickListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (TextUtils.isEmpty(title)) {
            builder.setTitle(context.getString(R.string.str_base_tips));
        } else {
            builder.setTitle(title);
        }
        builder.setMessage(msg);
        if (TextUtils.isEmpty(canStr)) {
            canStr = context.getString(R.string.cancel);
        }
        if (TextUtils.isEmpty(sureStr)) {
            sureStr = context.getString(R.string.sure);
        }
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER | Gravity.START);
        linearLayout.setBackground(ContextCompat.getDrawable(context, R.drawable.layout_bg));
        linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(15), AndroidUtilities.dp(24), AndroidUtilities.dp(15));
        CheckBoxSquare checkBox = new CheckBoxSquare(context, true);
        TextView cbTextView = new AppCompatTextView(context);
        cbTextView.setTextColor(ContextCompat.getColor(context, R.color.dialogText));
        cbTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        cbTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        cbTextView.setText(checkBoxText);
        linearLayout.addView(checkBox, LayoutHelper.createLinear(18, 18, Gravity.CENTER | Gravity.START, 0, 0, 10, 0));
        linearLayout.addView(cbTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        linearLayout.setOnClickListener(view -> checkBox.setChecked(!checkBox.isChecked(), true));
        builder.setNegativeButton(canStr, (dialog, which) -> {
                    if (iClickListener != null) {
                        iClickListener.onClick(0, checkBox.isChecked());
                    }
                }
        );
        builder.setView(linearLayout);
        builder.setPositiveButton(sureStr, (dialog, which) -> {
            if (iClickListener != null) {
                iClickListener.onClick(1, checkBox.isChecked());
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setBlurParams(1f, true, true);
        dialog.setCanceledOnTouchOutside(isCancelable);
        dialog.show();
        TextView textView = (TextView) dialog.getButton(Dialog.BUTTON_NEGATIVE);
        if (canColor == 0) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.colorAccentUn));
        } else {
            textView.setTextColor(canColor);
        }
        TextView positiveTv = (TextView) dialog.getButton(Dialog.BUTTON_POSITIVE);
        if (sureColor == 0) {
            positiveTv.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
        } else {
            positiveTv.setTextColor(sureColor);
        }
    }

    public interface IInputDialog {
        void onResult(String text);
    }

    public void showInputDialog(@NonNull Context context, String title, String message, String oldStr, String hideStr, int maxLength, final IInputDialog listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (!TextUtils.isEmpty(title)) {
            builder.setTitle(title);
        }
        if (!TextUtils.isEmpty(message)) {
            builder.setMessage(message);
        }
        LinearLayout linearLayout = new LinearLayout(context);
        EditText editText = new EditText(context);
        editText.setHint(hideStr);
        if (!TextUtils.isEmpty(oldStr)) {
            editText.setText(oldStr);
            editText.setSelection(oldStr.length());
        }
        editText.setFilters(new InputFilter[]{StringUtils.getInputFilter(maxLength)});
        SoftKeyboardUtils.getInstance().showSoftKeyBoard(context, editText);
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 24, 0, 24, 0));
        builder.setView(linearLayout);
        builder.setPositiveButton(context.getString(R.string.sure), (dialog, which) -> {
            String text = editText.getText().toString();
            listener.onResult(text);
        });
        AlertDialog dialog = builder.create();
        builder.setOnPreDismissListener(dialog1 -> SoftKeyboardUtils.getInstance().hideInput(context, editText));
        dialog.setBlurParams(1f, true, true);
        dialog.show();
        TextView sureTv = (TextView) dialog.getButton(Dialog.BUTTON_POSITIVE);
        sureTv.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
    }


    /**
     * 显示版本更新弹框
     *
     * @param context
     * @param versionEntity
     */
    public void showNewVersionDialog(Context context, AppVersion versionEntity) {
        versionEntity.notes = versionEntity.notes.replaceAll("\\n", "\n");
        View view = LayoutInflater.from(context).inflate(R.layout.act_new_version_layout, null);
        TextView contentTv = view.findViewById(R.id.contentTv);
        TextView versionTv = view.findViewById(R.id.versionTv);
        contentTv.setText(versionEntity.notes);
        versionTv.setText(String.format("%s：%s", context.getString(R.string.new_version), versionEntity.version));
        TextView cancelTv = view.findViewById(R.id.cancelTv);
        Button sureBtn = view.findViewById(R.id.sureBtn);
        ProgressBar progressBar = view.findViewById(R.id.progressBar);
        TextView progressTv = view.findViewById(R.id.progressTv);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context, R.style.AlertDialog);
        view.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));
        android.app.AlertDialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(versionEntity.is_force == 0);
        if (versionEntity.is_force == 1) {
            alertDialog.setCancelable(false);
        }
        alertDialog.show();
        alertDialog.setContentView(view);
        Window window = alertDialog.getWindow();
        assert window != null;
        WindowManager.LayoutParams param = window.getAttributes();
        param.width = (int) (PaneMetrics.widthPx(context) * 0.85f);
        if (versionEntity.is_force == 1) {
            cancelTv.setVisibility(GONE);
        }
        window.setAttributes(param);

        // YUJ-279 · 折叠屏 phone→unfold 右侧自适应修复：
        // 历史写法只在 show 时一次性读 PaneMetrics（show-at-use），已经在显的
        // 升级 Dialog 不会跟随 pane 变化。注册一个 ComponentCallbacks 让 dialog
        // 在 configuration change（unfold/fold/rotate）时重新按当前 pane 宽度
        // 重设 window.attributes，dismiss 时解注册避免泄漏。
        final android.content.ComponentCallbacks cfgListener = new android.content.ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
                try {
                    if (!alertDialog.isShowing()) return;
                    Window w = alertDialog.getWindow();
                    if (w == null) return;
                    WindowManager.LayoutParams p = w.getAttributes();
                    p.width = (int) (PaneMetrics.widthPx(context) * 0.85f);
                    w.setAttributes(p);
                } catch (Throwable ignored) {
                    // 配置变更路径上的 Dialog 竞态兜底
                }
            }

            @Override
            public void onLowMemory() {
            }
        };
        context.registerComponentCallbacks(cfgListener);

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] progressPoller = new Runnable[1];
        progressPoller[0] = () -> {
            int progress = DownloadApkUtils.getInstance().getDownloadProgress();
            if (progress < 0) {
                // 下载失败，恢复按钮，允许重试
                sureBtn.setVisibility(VISIBLE);
                if (versionEntity.is_force == 0) {
                    cancelTv.setVisibility(VISIBLE);
                    alertDialog.setCanceledOnTouchOutside(true);
                    alertDialog.setCancelable(true);
                }
                progressBar.setVisibility(GONE);
                progressTv.setVisibility(GONE);
                WKToastUtils.getInstance().showToastNormal(context.getString(R.string.str_file_download_fail));
                return;
            }
            progressBar.setProgress(progress);
            progressTv.setText(String.format("%d%%", progress));
            if (progress >= 100) {
                alertDialog.dismiss();
                return;
            }
            handler.postDelayed(progressPoller[0], 500);
        };

        alertDialog.setOnDismissListener(d -> {
            handler.removeCallbacks(progressPoller[0]);
            // YUJ-279 · 解注册 ComponentCallbacks 防止 leak
            try {
                context.unregisterComponentCallbacks(cfgListener);
            } catch (Throwable ignored) {
            }
        });

        cancelTv.setOnClickListener(view1 -> {
            if (versionEntity.is_force == 0) {
                alertDialog.dismiss();
            }
        });
        sureBtn.setOnClickListener(view1 -> {
            DownloadApkUtils.getInstance().downloadAPK(WKBaseApplication.getInstance().getContext(),
                    versionEntity.version,
                    WKApiConfig.getShowUrl(versionEntity.url),
                    new DownloadApkUtils.IDownloadCallback() {
                        @Override
                        public void onInstallDirectly() {
                            // APK 已存在，直接安装，关闭弹框
                            alertDialog.dismiss();
                        }

                        @Override
                        public void onDownloadStarted() {
                            // 开始下载，禁止点击外部关闭
                            alertDialog.setCanceledOnTouchOutside(false);
                            alertDialog.setCancelable(false);
                            sureBtn.setVisibility(GONE);
                            cancelTv.setVisibility(GONE);
                            progressBar.setProgress(0);
                            progressBar.setVisibility(VISIBLE);
                            progressTv.setText("0%");
                            progressTv.setVisibility(VISIBLE);
                            handler.postDelayed(progressPoller[0], 500);
                        }

                        @Override
                        public void onDownloadAlreadyRunning() {
                            // 下载已在进行中，禁止点击外部关闭
                            alertDialog.setCanceledOnTouchOutside(false);
                            alertDialog.setCancelable(false);
                            sureBtn.setVisibility(GONE);
                            cancelTv.setVisibility(GONE);
                            progressBar.setProgress(0);
                            progressBar.setVisibility(VISIBLE);
                            progressTv.setText("0%");
                            progressTv.setVisibility(VISIBLE);
                            handler.postDelayed(progressPoller[0], 500);
                        }

                        @Override
                        public void onError(String msg) {
                            WKToastUtils.getInstance().showToastNormal(msg);
                        }
                    });
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
    public void setViewLongClickPopup(View view, List<PopupMenuItem> list) {
        // YUJ-261 · 改用 GestureDetector 识别长按，OnTouchListener 返回 false 让 click 链路
        // 继续传递。之前同一 view 同时挂 OnTouchListener + OnLongClickListener，view 在
        // RecyclerView 刷新时重建会出现竞争，导致「长按被识别为点击被吞掉」或「点击落到 CANCEL」。
        //
        // YUJ-264 · Fix 5 加固：因为没有 setOnLongClickListener，框架的 long-click machinery
        // 不会 set mHasPerformedLongPress=true，ACTION_UP 仍会触发 performClick()。结果是
        // 长按 500ms 弹 popup + 用户松手同时跑 OnClickListener（同时进入会话 + 弹出菜单）。
        // 这里用 longPressFired 标记在长按触发后消费 ACTION_UP，阻止 performClick。
        final float[] downRaw = new float[2];
        final boolean[] longPressFired = {false};
        final GestureDetector detector = new GestureDetector(view.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        longPressFired[0] = false;
                        downRaw[0] = e.getRawX();
                        downRaw[1] = e.getRawY();
                        return false; // 不消费，click 继续
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        longPressFired[0] = true;
                        showScreenPopup(view, new float[]{downRaw[0], downRaw[1]}, list, null);
                    }
                });
        view.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            // YUJ-264: consume ACTION_UP after a long-press so the framework
            // does NOT also fire performClick() on release. Without this,
            // long-press → popup AND click → open chat both happen.
            int action = event.getActionMasked();
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    && longPressFired[0]) {
                longPressFired[0] = false;
                return true; // 消费 UP，阻止 performClick
            }
            return false; // 关键：不消费，click / RecyclerView scroll 继续链路
        });
    }

    public Rect getAtViewRect(View atView) {
        int[] locations = new int[2];
        atView.getLocationInWindow(locations);
        return new Rect(locations[0], locations[1], locations[0] - atView.getMeasuredWidth(),
                locations[1] + atView.getMeasuredHeight() / 4);
    }

    public synchronized void showScreenPopup(View view, List<PopupMenuItem> list) {
        Rect rect = getAtViewRect(view);
        float[] location = new float[]{rect.right, rect.bottom};
        showScreenPopup(view, location, list, null);
    }

    public interface IScreenPopupDismiss {
        void onDismiss();
    }

    @SuppressLint("ClickableViewAccessibility")
    public synchronized void showScreenPopup(View view, float[] location, List<PopupMenuItem> list, final IScreenPopupDismiss iScreenPopupDismiss) {
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
            if (item.getColor() != 0) {
                subItem.setTextColor(item.getColor());
                subItem.setIconColor(item.getColor());
            }
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
        scrimPopupWindow.setAnimationStyle(R.style.PopupAnimation);
        scrimPopupWindow.setFocusable(true);
        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);


        Runnable showMenu = () -> {
            if (scrimPopupWindow == null) {
                return;
            }
            scrimPopupWindow.startAnimation();
            scrimPopupWindow.showAtLocation(view, Gravity.TOP | Gravity.START, Math.round(location[0]), Math.round(location[1]));
//            scrimPopupWindow.dimBehind();
        };

        showMenu.run();
    }


    public interface IImagePopupListener {
        void onShow();

        void onDismiss();
    }

    public BasePopupView showImagePopup(Context context, List<Object> tempImgList, List<ImageView> imgList, ImageView imageView, int index, List<ImagePopupBottomSheetItem> list, final CustomImageViewerPopup.IImgPopupMenu iImgPopupMenu, final IImagePopupListener iImagePopupListener) {
        return showImagePopup(context, null, tempImgList, imgList, imageView, index, list, iImgPopupMenu, iImagePopupListener);
    }

    public BasePopupView showImagePopup(Context context, WKMsg msg, List<Object> tempImgList, List<ImageView> imgList, ImageView imageView, int index, List<ImagePopupBottomSheetItem> list, final CustomImageViewerPopup.IImgPopupMenu iImgPopupMenu, final IImagePopupListener iImagePopupListener) {
        Object o = imageView.getTag();
        int flame = 0;
        if (o != null) {
            flame = (int) o;
        }

        int finalFlame = flame;
        CustomImageViewerPopup viewerPopup = new CustomImageViewerPopup(context, flame, msg, list, iImgPopupMenu);
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
