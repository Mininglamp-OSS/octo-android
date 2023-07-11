package com.chat.base.views.pwdview;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chat.base.R;
import com.chat.base.ui.Theme;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 2020-11-02 13:37
 */
public class NumPwdView extends LinearLayout implements
        View.OnClickListener {

    private final PwdView pwdView;
    Context context;
    private final ArrayList<Map<String, String>> valueList;
    private final EditText[] tvList;
    private final GridView gridView;
    private int currentIndex = -1;
    private String strPassword;

    public NumPwdView(Context context) {
        this(context, null);
    }

    public NumPwdView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        View view = View.inflate(context, R.layout.wk_num_pwd_input_layout, null);

        valueList = new ArrayList<>();
        tvList = new EditText[6];
        pwdView = view.findViewById(R.id.pwdDialog);

        tvList[0] = pwdView.findViewById(R.id.tv_pass1);
        tvList[1] = pwdView.findViewById(R.id.tv_pass2);
        tvList[2] = pwdView.findViewById(R.id.tv_pass3);
        tvList[3] = pwdView.findViewById(R.id.tv_pass4);
        tvList[4] = pwdView.findViewById(R.id.tv_pass5);
        tvList[5] = pwdView.findViewById(R.id.tv_pass6);

        gridView = view.findViewById(R.id.gv_keybord);
        pwdView.showPwdView();
        setView();
        addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    }

    public void clearAllPwd() {
        tvList[0].setText("");
        tvList[1].setText("");
        tvList[2].setText("");
        tvList[3].setText("");
        tvList[4].setText("");
        tvList[5].setText("");
        currentIndex = -1;
    }

    public void setPwdViewBg() {
        pwdView.setBg();
    }

    public void hideCloseIV() {
        pwdView.hideCloseIV();
    }

    public void setBottomTv(String content, int color, PwdView.IBottomClick iBottomClick) {
        pwdView.setBottomTv(content, color, iBottomClick);
    }

    @Override
    public void onClick(View v) {

    }


    private void setView() {
        for (int i = 1; i < 13; i++) {
            Map<String, String> map = new HashMap<String, String>();
            if (i < 10) {
                map.put("name", String.valueOf(i));
            } else if (i == 10) {
                map.put("name", "");
            } else if (i == 11) {
                map.put("name", String.valueOf(0));
            } else {
                map.put("name", "<<-");
            }
            valueList.add(map);
        }

        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 11 && position != 9) { // 点击0~9按钮
                if (currentIndex >= -1 && currentIndex < 5) { // 判断输入位置————要小心数组越界
                    tvList[++currentIndex].setText(valueList.get(position)
                            .get("name"));
                }
            } else {
                if (position == 11) { // 点击退格键
                    if (currentIndex - 1 >= -1) { // 判断是否删除完毕————要小心数组越界
                        tvList[currentIndex--].setText("");
                    }
                }
            }
        });
        Animation animation = AnimationUtils.loadAnimation(context, R.anim.dialog_enter_anim);
        gridView.setAnimation(animation);
        animation.start();
    }

    // GrideView的适配器
    BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return valueList.size();
        }

        @Override
        public Object getItem(int position) {
            return valueList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = View.inflate(context, R.layout.item_num_pwd,
                        null);
                Theme.setColorFilter(context,convertView.findViewById(R.id.delete_iv),R.color.colorDark);
                viewHolder = new ViewHolder();
                viewHolder.btnKey = convertView.findViewById(R.id.btn_keys);
                viewHolder.delete_iv = convertView.findViewById(R.id.delete_iv);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            viewHolder.btnKey.setText(valueList.get(position).get("name"));
          /*  if (valueList.get(position).get("name").equals("<<-")) {
                viewHolder.delete_iv.setVisibility(View.VISIBLE);
                viewHolder.btnKey.setVisibility(View.GONE);
            } else {
                viewHolder.delete_iv.setVisibility(View.GONE);
                viewHolder.btnKey.setVisibility(View.VISIBLE);
            }*/
            viewHolder.btnKey.setEnabled(true);
            if (position == 9 || position == 11) {
                viewHolder.btnKey
                        .setBackgroundResource(R.color.colord8d5d5);
                viewHolder.btnKey.setVisibility(GONE);
                if (position == 9) {
                    viewHolder.btnKey.setEnabled(false);
                } else {
                    viewHolder.btnKey.setVisibility(GONE);
                    viewHolder.delete_iv.setVisibility(View.VISIBLE);
                }

            } else {
                viewHolder.btnKey.setVisibility(VISIBLE);
                viewHolder.delete_iv.setVisibility(View.GONE);
            }
            return convertView;
        }
    };

    static final class ViewHolder {
        TextView btnKey;
        ImageView delete_iv;
    }

    public void setOnFinishInput(final INumPwdInputFinish pass) {

        tvList[5].addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().length() == 1) {
                    strPassword = "";
                    for (int i = 0; i < 6; i++) {
                        strPassword = MessageFormat.format("{0}{1}", strPassword, tvList[i].getText().toString().trim());
                    }
                    pass.inputFinish();
                }
            }
        });
    }

    public interface INumPwdInputFinish {
        void inputFinish();
    }

    public String getNumPwd() {
        return strPassword;
    }
}
