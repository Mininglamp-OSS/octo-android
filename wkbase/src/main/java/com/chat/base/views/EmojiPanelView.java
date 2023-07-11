package com.chat.base.views;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.chat.base.R;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.emoji.EmojiFragment;
import com.chat.base.emoji.MoonUtil;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.KeyboardOnGlobalChangeListener;
import com.chat.base.utils.StringUtils;

import java.util.Objects;

/**
 * 2020-11-06 12:02
 */
public class EmojiPanelView extends LinearLayout implements OnKeyBoardStateListener {

    private boolean isInitComplete;
    private LinearLayout mLayoutPanel;
    private Button sendBtn;
    private EditText mEditText;
    private EditText outsideEt;
    private FrameLayout mLayoutNull;
    private FrameLayout emojiLayout;

    private ImageView mImageSwitch;

    public EmojiPanelView(Context context) {
        super(context);
        init();
    }

    public EmojiPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EmojiPanelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        if (event.getY() < WindowUtil.getInstance().getScreenWidth() - WindowUtil.getInstance().dip2px(WKBaseApplication.getInstance().getContext(), 254f) && isShowing()) {
//            dismiss();
//        }
//        return super.onTouchEvent(event);
//    }

    public boolean isShowing() {
        return mLayoutPanel != null && mLayoutPanel.getVisibility() == VISIBLE;
    }


    private void showSoftKeyBoard() {
        mImageSwitch.setSelected(false);
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null && mEditText != null) {

            if (outsideEt != null) {
                outsideEt.post(() -> {
                    outsideEt.requestFocus();
                    inputMethodManager.showSoftInput(outsideEt, 0);
                });
            } else {
                mEditText.post(() -> {
                    mEditText.requestFocus();
                    inputMethodManager.showSoftInput(mEditText, 0);
                });
            }
        }
    }


    private void hideSoftKeyBoard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null && mEditText != null) {
            inputMethodManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
        }
    }


    private void init() {
        View itemView = LayoutInflater.from(getContext()).inflate(R.layout.view_emoji_panel, this, false);
        mEditText = itemView.findViewById(R.id.edit_text);
        mEditText.setOnClickListener((v) -> showSoftKeyBoard());

        mEditText.addTextChangedListener(new TextWatcher() {
//            private int start;
//            private int count;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//                this.start = i;
//                this.count = i2;
            }

            @Override
            public void afterTextChanged(Editable editable) {

//                MoonUtil.replaceEmoticons(getContext(), editable, start, count);
                int editEnd = mEditText.getSelectionEnd();
                mEditText.removeTextChangedListener(this);
                while (StringUtils.counterChars(editable.toString()) > 150 && editEnd > 0) {
                    editable.delete(editEnd - 1, editEnd);
                    editEnd--;
                }
                mEditText.setSelection(editEnd);
                mEditText.addTextChangedListener(this);

                String content = editable.toString();
                if (TextUtils.isEmpty(content)) {
                    sendBtn.setEnabled(false);
                    sendBtn.setAlpha(0.2f);
                } else {
                    sendBtn.setEnabled(true);
                    sendBtn.setAlpha(1f);
                }
            }
        });
        mImageSwitch = itemView.findViewById(R.id.img_switch);
        mImageSwitch.setOnClickListener(v -> {
            if (!mImageSwitch.isSelected()) {
                mImageSwitch.setSelected(true);
                hideSoftKeyBoard();
            } else {
                mImageSwitch.setSelected(false);
                showSoftKeyBoard();
            }
        });
        sendBtn = itemView.findViewById(R.id.sendBtn);
        mLayoutNull = itemView.findViewById(R.id.layout_null);
        emojiLayout = itemView.findViewById(R.id.emojiLayout);
        mLayoutPanel = itemView.findViewById(R.id.layout_panel);

        addOnSoftKeyBoardVisibleListener((Activity) getContext(), this);
        int height = WKSharedPreferencesUtil.getInstance().getInt("moments_keyboardHeight");
        if (height == 0) {
            ((Activity) getContext()).getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
        } else {
            emojiLayout.getLayoutParams().height = height;
        }
        addView(itemView);
        mLayoutNull.setOnClickListener(view -> dismiss());
        sendBtn.setOnClickListener(view -> {
            if (iinputResult != null) {
                String content = mEditText.getText().toString();
                iinputResult.onResult(content);
                mEditText.setText("");
                sendBtn.setEnabled(false);
                sendBtn.setAlpha(0.2f);
                dismiss();
            }
        });
    }

    boolean isVisiableForLast = false;

    public void addOnSoftKeyBoardVisibleListener(Activity activity, final OnKeyBoardStateListener listener) {
        activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new KeyboardOnGlobalChangeListener(activity.getWindow().getDecorView(), new KeyboardOnGlobalChangeListener.ISoftKeyBoardStatus() {
            @Override
            public void onStatus(int status) {
                int keyboardHeight = WKSharedPreferencesUtil.getInstance().getInt("moments_keyboardHeight");
                //计算出可见屏幕的高度
                int displayHeight = AndroidUtilities.getScreenHeight() - keyboardHeight - AndroidUtilities.dp( 48f);// rect.bottom - rect.top;
                boolean visible = status == 1;
                if (visible != isVisiableForLast) {
                    listener.onSoftKeyBoardState(visible, keyboardHeight, displayHeight);
                }
                isVisiableForLast = visible;
            }

            @Override
            public void onKeyboardHeight(int keyboardHeight) {
//                Log.e("设置高度：", "--->" + keyboardHeight);
//                ViewGroup.LayoutParams layoutParams = bottomView.getLayoutParams();
//                layoutParams.height = keyboardHeight;
//                bottomView.setLayoutParams(layoutParams);
//                emojiLayout.getLayoutParams().height = keyboardHeight;
//                SharedPreferencesUtil.getInstance().putInt("keyboardHeight", keyboardHeight);

            }
        }));

    }


    @Override
    public void onSoftKeyBoardState(boolean visible, int keyboardHeight, int displayHeight) {
    }


    private void showOrHideAnimation(final boolean isShow) {
        TranslateAnimation animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, isShow ? 1.0f : 0.0f,
                Animation.RELATIVE_TO_PARENT, isShow ? 0.0f : 1.0f);
        animation.setDuration(200);
        mLayoutPanel.startAnimation(animation);
    }

    public void dismiss() {
        if (isShowing()) {
            if (mLayoutPanel != null) {
                mLayoutPanel.setVisibility(GONE);
            }
            showOrHideAnimation(false);
            mImageSwitch.setSelected(false);
            hideSoftKeyBoard();
        }
    }

    public void showEmojiPanel() {
        if (isInitComplete) {
            mImageSwitch.setSelected(false);
            if (mLayoutPanel != null) {
                mLayoutPanel.setVisibility(VISIBLE);
            }
            showOrHideAnimation(true);
            showSoftKeyBoard();
        }
    }

    public void showNormalImgSwitch() {
        mImageSwitch.setSelected(false);
    }

    public void showEmojiPanel(String hitStr) {
        if (isInitComplete) {
            if (mLayoutPanel != null) {
                mLayoutPanel.setVisibility(VISIBLE);
            }
            mEditText.setHint(hitStr);
            showOrHideAnimation(true);
            new Handler(Looper.myLooper()).postDelayed(this::showSoftKeyBoard, 400);
//            showSoftKeyBoard();
        }
    }

    public void initEmojiPanel(AppCompatActivity appCompatActivity) {
        EmojiFragment emojiFragment = new EmojiFragment();
        emojiFragment.setOnEmojiClick(emojiName -> {
            if (TextUtils.isEmpty(emojiName)) {
                if (outsideEt == null)
                    mEditText.dispatchKeyEvent(new KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                else outsideEt.dispatchKeyEvent(new KeyEvent(
                        KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            } else {
                if (outsideEt == null) {
                    int curPosition = mEditText.getSelectionStart();
                    StringBuilder sb = new StringBuilder(Objects.requireNonNull(mEditText.getText()).toString());
                    sb.insert(curPosition, emojiName);
//                    mEditText.setText(sb.toString());
                    MoonUtil.addEmojiSpan(mEditText, emojiName, getContext());
                    // 将光标设置到新增完表情的右侧
                    mEditText.setSelection(curPosition + emojiName.length());
                } else {
                    int curPosition = outsideEt.getSelectionStart();
                    StringBuilder sb = new StringBuilder(Objects.requireNonNull(outsideEt.getText()).toString());
                    sb.insert(curPosition, emojiName);
                    MoonUtil.addEmojiSpan(outsideEt, emojiName, getContext());
//                    outsideEt.setText(sb.toString());
                    // 将光标设置到新增完表情的右侧
                    outsideEt.setSelection(curPosition + emojiName.length());
                }
            }
        });
        appCompatActivity.getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.emojiLayout, emojiFragment)
                .commit();
        isInitComplete = true;
    }

    private IinputResult iinputResult;

    public void addOnInputResult(IinputResult iinputResult) {
        this.iinputResult = iinputResult;
    }

    public interface IinputResult {
        void onResult(String content);
    }

    public void setOutsideEditText(EditText editText) {
        mEditText.setVisibility(GONE);
        outsideEt = editText;
        sendBtn.setVisibility(GONE);
        mLayoutNull.setVisibility(GONE);
//        if (outsideEt != null) {
//            outsideEt.addTextChangedListener(new TextWatcher() {
//                private int start;
//                private int count;
//
//                @Override
//                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//
//                }
//
//                @Override
//                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//                    this.start = i;
//                    this.count = i2;
//                }
//
//                @Override
//                public void afterTextChanged(Editable editable) {
//
//                    MoonUtil.replaceEmoticons(getContext(), editable, start, count);
//                    int editEnd = outsideEt.getSelectionEnd();
//                    outsideEt.removeTextChangedListener(this);
//                    while (StringUtils.counterChars(editable.toString()) > 2000 && editEnd > 0) {
//                        editable.delete(editEnd - 1, editEnd);
//                        editEnd--;
//                    }
//                    outsideEt.setSelection(editEnd);
//                    outsideEt.addTextChangedListener(this);
//                }
//            });
//        }
    }

    private int mWindowHeight = 0;

    private final ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            Rect r = new Rect();
            //获取当前窗口实际的可见区域
            ((Activity) getContext()).getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
            int height = r.height();
            if (mWindowHeight == 0) {
                //一般情况下，这是原始的窗口高度
                mWindowHeight = height;
            } else {
                if (mWindowHeight != height) {
                    //两次窗口高度相减，就是软键盘高度
                    int softKeyboardHeight = mWindowHeight - height;
                    emojiLayout.getLayoutParams().height = softKeyboardHeight;
                    WKSharedPreferencesUtil.getInstance().putInt("moments_keyboardHeight", softKeyboardHeight);
                }
            }
        }
    };

    public int getKeyboardHeight() {
        return emojiLayout.getLayoutParams().height;
    }
}
