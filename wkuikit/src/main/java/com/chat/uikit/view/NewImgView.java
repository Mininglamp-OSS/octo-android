package com.chat.uikit.view;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.chat.base.glide.GlideUtils;
import com.chat.uikit.R;
import com.lxj.xpopup.core.AttachPopupView;

/**
 * 2020-08-01 22:48
 * 最新图片弹框
 */
public class NewImgView extends AttachPopupView {
    String path;
    Context context;
    private final IClick iClick;

    public NewImgView(@NonNull Context context, String path, IClick iClick) {
        super(context);
        this.context = context;
        this.path = path;
        this.iClick = iClick;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        ImageView imageView = findViewById(R.id.imageView);
        GlideUtils.getInstance().showImg(context, path, imageView);
        findViewById(R.id.imageLayout).setOnClickListener(view -> {
            dismiss();
            iClick.onClick(path);
        });
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.new_img_layout;
    }

    public interface IClick {
        void onClick(String path);
    }
}
