package com.chat.uikit.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import com.chat.base.config.WKConstants;
import com.chat.uikit.R;

/**
 * 2020-10-03 14:35
 * 聊天面板view
 */
public class ChatPanelView extends LinearLayout {
    public static final int DEFAULT_KEYBOARD_HEIGHT = 387;
    public static final int MIN_KEYBOARD_HEIGHT = 20;
    private int mMaxKeyboardHeight = Integer.MIN_VALUE;
    private int mDefaultKeyboardHeight;
    private int mMinKeyboardHeight;
    private int mKeyboardHeight;
    private int mInputViewId;
    private View mInputView;
    private boolean mKeyboardOpen = false;
    private int mInputPaneId;
    private View mInputPane;
    private OnVisibilityChangeListener mListener;
    private OnKeyboardChangeListener keyboardChangeListener;
    private boolean mAutoSaveKeyboardHeight;
    private KeyboardProcessor mKeyboardProcessor;
    private boolean tShowInputPane = false;

    public ChatPanelView(Context context) {
        super(context);
        initView(null);
    }

    public ChatPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(attrs);
    }

    @TargetApi(11)
    public ChatPanelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(attrs);
    }

    @TargetApi(21)
    public ChatPanelView(Context context, AttributeSet attrs, int defStyleAttr,
                         int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView(attrs);
    }

    private void initView(AttributeSet attrs) {
        int defaultInputHeight = (int) (DEFAULT_KEYBOARD_HEIGHT *
                getResources().getDisplayMetrics().density);
        int minInputHeight = (int) (MIN_KEYBOARD_HEIGHT *
                getResources().getDisplayMetrics().density);
        mInputViewId = NO_ID;
        mInputPaneId = NO_ID;
        boolean autoSave;
        TypedArray custom = getContext().obtainStyledAttributes(attrs,
                R.styleable.SmoothInputLayout);
        defaultInputHeight = custom.getDimensionPixelOffset(
                R.styleable.SmoothInputLayout_silDefaultKeyboardHeight, defaultInputHeight);
        minInputHeight = custom.getDimensionPixelOffset(
                R.styleable.SmoothInputLayout_silMinKeyboardHeight, minInputHeight);
        mInputViewId = custom.getResourceId(R.styleable.SmoothInputLayout_silInputView,
                mInputViewId);
        mInputPaneId = custom.getResourceId(R.styleable.SmoothInputLayout_silInputPane,
                mInputPaneId);
        autoSave = custom.getBoolean(R.styleable.SmoothInputLayout_silAutoSaveKeyboardHeight,
                true);
        custom.recycle();
        setDefaultKeyboardHeight(defaultInputHeight);
        setMinKeyboardHeight(minInputHeight);
        setAutoSaveKeyboardHeight(autoSave);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mInputViewId != NO_ID) {
            setInputView(findViewById(mInputViewId));
        }
        if (mInputPaneId != NO_ID) {
            setInputPane(findViewById(mInputPaneId));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (heightSize > mMaxKeyboardHeight) {
            mMaxKeyboardHeight = heightSize;
        }
        final int heightChange = mMaxKeyboardHeight - heightSize;
        if (heightChange > mMinKeyboardHeight) {
            if (mKeyboardHeight != heightChange) {
                mKeyboardHeight = heightChange;
                saveKeyboardHeight();
            }
            if (!mKeyboardOpen)
                iRecyclerViewScrollEnd.onScrollEnd();
            mKeyboardOpen = true;
            // 输入法弹出，隐藏功能面板
            if (mInputPane != null && mInputPane.getVisibility() == VISIBLE) {
                mInputPane.setVisibility(GONE);
                if (mListener != null)
                    mListener.onVisibilityChange(GONE);
            }
        } else {
            if (tShowInputPane) {
                tShowInputPane = false;
                if (mInputPane != null && mInputPane.getVisibility() == GONE) {
                    if (!mKeyboardOpen)
                        iRecyclerViewScrollEnd.onScrollEnd();
                    updateLayout();
                    mInputPane.setVisibility(VISIBLE);
                    if (mListener != null)
                        mListener.onVisibilityChange(VISIBLE);
                    forceLayout();
                }
            }
            mKeyboardOpen = false;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }


    //列表滚动到底部
    public interface IRecyclerViewScrollEnd {
        void onScrollEnd();
    }

    IRecyclerViewScrollEnd iRecyclerViewScrollEnd;

    public void setRecyclerViewScrollEnd(IRecyclerViewScrollEnd iRecyclerViewScrollEnd) {
        this.iRecyclerViewScrollEnd = iRecyclerViewScrollEnd;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (keyboardChangeListener != null) {
            keyboardChangeListener.onKeyboardChanged(mKeyboardOpen);
        }
        super.onSizeChanged(w, h, oldw, oldh);

    }


    /**
     * 存储键盘高度
     */
    private void saveKeyboardHeight() {
        if (mAutoSaveKeyboardHeight)
            WKConstants.setKeyboardHeight(mKeyboardHeight);
        else {
            if (mKeyboardProcessor != null)
                mKeyboardProcessor.onSaveKeyboardHeight(mKeyboardHeight);
        }
    }

    /**
     * 更新子项高度
     */
    private void updateLayout() {
        if (mInputPane == null)
            return;
        if (mKeyboardHeight == 0)
            mKeyboardHeight = getKeyboardHeight(mDefaultKeyboardHeight);
        ViewGroup.LayoutParams layoutParams = mInputPane.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.height = mKeyboardHeight;
            mInputPane.setLayoutParams(layoutParams);
        }
    }

    private int getKeyboardHeight(int defaultHeight) {
        if (mAutoSaveKeyboardHeight) {
            int h = WKConstants.getKeyboardHeight();
            if (h <= 0) h = defaultHeight;
            return h;
        } else
            return mKeyboardProcessor != null ?
                    mKeyboardProcessor.getSavedKeyboardHeight(defaultHeight) : defaultHeight;
    }

    /**
     * 设置默认系统输入面板高度
     *
     * @param height 输入面板高度
     */
    public void setDefaultKeyboardHeight(int height) {
        if (mDefaultKeyboardHeight != height)
            mDefaultKeyboardHeight = height;
    }

    /**
     * 设置最小系统输入面板高度
     *
     * @param height 输入面板高度
     */
    public void setMinKeyboardHeight(int height) {
        if (mMinKeyboardHeight != height)
            mMinKeyboardHeight = height;
    }

    /**
     * 设置输入框
     *
     * @param edit 输入框
     */
    public void setInputView(View edit) {
        if (mInputView != edit)
            mInputView = edit;
    }

    /**
     * 设置特殊输入面板
     *
     * @param pane 面板
     */
    public void setInputPane(View pane) {
        if (mInputPane != pane)
            mInputPane = pane;
    }

    /**
     * 设置面板可见改变监听
     *
     * @param listener 面板可见改变监听
     */
    public void setOnVisibilityChangeListener(OnVisibilityChangeListener listener) {
        mListener = listener;
    }

    /**
     * 设置键盘改变监听
     *
     * @param listener 键盘改变监听
     */
    public void setOnKeyboardChangeListener(OnKeyboardChangeListener listener) {
        keyboardChangeListener = listener;
    }

    /**
     * 设置自动保存键盘高度
     *
     * @param auto 是否自动
     */
    public void setAutoSaveKeyboardHeight(boolean auto) {
        mAutoSaveKeyboardHeight = auto;
    }

    /**
     * 设置键盘处理器
     * 仅在关闭自动保存键盘高度时设置的处理器才有效{@link #setAutoSaveKeyboardHeight(boolean)}
     *
     * @param processor 处理器
     */
    public void setKeyboardProcessor(KeyboardProcessor processor) {
        mKeyboardProcessor = processor;
    }

    /**
     * 是否输入法已打开
     *
     * @return 是否输入法已打开
     */
    public boolean isKeyBoardOpen() {
        return mKeyboardOpen;
    }

    /**
     * 是否特殊输入面板已打开
     *
     * @return 特殊输入面板已打开
     */
    public boolean isInputPaneOpen() {
        return mInputPane != null && mInputPane.getVisibility() == VISIBLE;
    }

    /**
     * 关闭特殊输入面板
     */
    public void closeInputPane() {
//        if (isInputPaneOpen()) {
        mInputPane.setVisibility(GONE);
        if (mListener != null)
            mListener.onVisibilityChange(GONE);
//        }
    }

    /**
     * 显示特殊输入面板
     *
     * @param focus 是否让输入框拥有焦点
     */
    public void showInputPane(boolean focus) {
        if (isKeyBoardOpen()) {
            tShowInputPane = true;
            InputMethodManager imm = ((InputMethodManager) (getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE)));
            if (imm != null) {
                imm.hideSoftInputFromWindow(getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
            }

        } else {
            if (mInputPane != null && mInputPane.getVisibility() == GONE) {
                iRecyclerViewScrollEnd.onScrollEnd();
                updateLayout();
                mInputPane.setVisibility(VISIBLE);
                if (mListener != null)
                    mListener.onVisibilityChange(VISIBLE);
                if (keyboardChangeListener != null){
                    keyboardChangeListener.onKeyboardChanged(false);
                }
            }
        }
        if (focus) {
            if (mInputView != null) {
                mInputView.requestFocus();
                mInputView.requestFocusFromTouch();
            }
        } else {
            if (mInputView != null) {
                setFocusable(true);
                setFocusableInTouchMode(true);
                mInputView.clearFocus();
            }
        }
    }

    /**
     * 关闭键盘
     *
     * @param clearFocus 是否清除输入框焦点
     */
    public void closeKeyboard(boolean clearFocus) {
        InputMethodManager imm = ((InputMethodManager) (getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE)));
        if (imm != null) {
            imm.hideSoftInputFromWindow(getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
        if (clearFocus && mInputView != null) {
            setFocusable(true);
            setFocusableInTouchMode(true);
            mInputView.clearFocus();
        }
    }

    /**
     * 打开键盘
     */
    public void showKeyboard() {
        if (mInputView == null) {
            return;
        }
        mInputView.requestFocus();
        mInputView.requestFocusFromTouch();
        InputMethodManager imm = ((InputMethodManager) (getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE)));
        if (imm != null) {
            imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
            // imm.showSoftInput(mInputView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * 面板可见改变监听
     */
    public interface OnVisibilityChangeListener {
        void onVisibilityChange(int visibility);
    }

    /**
     * 键盘改变监听
     */
    public interface OnKeyboardChangeListener {
        void onKeyboardChanged(boolean open);
    }

    /**
     * 键盘处理器
     */
    public interface KeyboardProcessor {
        /**
         * 存储键盘高度
         *
         * @param height 高度
         */
        void onSaveKeyboardHeight(int height);

        /**
         * 获取存储的键盘高度
         *
         * @param defaultHeight 默认高度
         * @return 键盘高度
         */
        int getSavedKeyboardHeight(int defaultHeight);
    }
}