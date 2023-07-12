package com.chat.push.push;

import android.content.Context;

import com.heytap.msp.push.mode.DataMessage;
import com.heytap.msp.push.service.CompatibleDataMessageCallbackService;

public class OPPOPushMessageService extends CompatibleDataMessageCallbackService {
    /**
     * 透传消息处理，应用可以打开页面或者执行命令,如果应用不需要处理透传消息，则不需要重写此方法
     *
     * @param context
     * @param message
     */
    @Override
    public void processMessage(Context context, DataMessage message) {
        super.processMessage(context.getApplicationContext(), message);
        String content = message.getContent();
//        TestModeUtil.addLogString(PushMessageService.class.getSimpleName(), "Receive SptDataMessage:" + message.toString());
//        MessageDispatcher.dispatch(context, content);//统一处理
//        LogUtil.d("processMessage  message" +message.toString());
//        NotificationUtil.showNotification(context,message.getTitle(),message.getContent(),message.getNotifyID(), ConfigManager.getInstant().isRedBadge());
    }
}
