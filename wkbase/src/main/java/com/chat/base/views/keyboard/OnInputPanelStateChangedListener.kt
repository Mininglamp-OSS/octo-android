package com.chat.base.views.keyboard

interface OnInputPanelStateChangedListener {

    /**
     * 显示语音面板
     */
    fun onShowVoicePanel()

    /**
     * 显示软键盘面板
     */
    fun onShowInputMethodPanel()

    /**
     * 显示表情面板
     */
    fun onShowExpressionPanel()

    /**
     * 显示更多面板
     */
    fun onShowMorePanel()
}