package com.chat.uikit.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputFilter;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.chat.base.config.WKConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKToastUtils;
import com.chat.base.utils.StringUtils;
import com.chat.uikit.R;
import com.chat.uikit.group.service.GroupModel;
import com.lxj.xpopup.core.CenterPopupView;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 2020-01-31 19:32
 * 修改在群内的昵称
 */
@SuppressLint("ViewConstructor")
public class UpdateNameInGroupView extends CenterPopupView {
    private final String groupNo;
    private final IUpdateListener iUpdateListener;

    public UpdateNameInGroupView(@NonNull Context context, @NonNull String groupNo, final IUpdateListener iUpdateListener) {
        super(context);
        this.iUpdateListener = iUpdateListener;
        this.groupNo = groupNo;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.in_group_name_dialog_view;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        EditText nameEt = findViewById(R.id.nameEt);

        WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(groupNo, WKChannelType.GROUP, WKConfig.getInstance().getUid());
        if (member != null) {
            String name = member.memberRemark;
            if (TextUtils.isEmpty(name))
                name = member.memberName;
            if (!TextUtils.isEmpty(name)){
                nameEt.setText(name);
                nameEt.setSelection(name.length());
            }
        }
        nameEt.setFilters(new InputFilter[]{StringUtils.getInputFilter(10)});
        findViewById(R.id.cancelTv).setOnClickListener(v -> dismiss());
        findViewById(R.id.sureTv).setOnClickListener(v -> {
            String name = nameEt.getText().toString();
            if (!TextUtils.isEmpty(name)) {
                GroupModel.getInstance().updateGroupMemberInfo(groupNo, WKConfig.getInstance().getUid(), "remark", name, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        if (iUpdateListener != null)
                            iUpdateListener.onResult(name);
                        dismiss();
                    } else WKToastUtils.getInstance().showToastNormal(msg);
                });
            }
        });
    }

    public interface IUpdateListener {
        void onResult(String name);
    }
}
