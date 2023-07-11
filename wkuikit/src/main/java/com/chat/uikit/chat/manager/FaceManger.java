package com.chat.uikit.chat.manager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.viewpager2.widget.ViewPager2;

import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatFunctionMenu;
import com.chat.base.msg.IConversationContext;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.uikit.R;
import com.chat.uikit.chat.fragment.FunctionViewPageAdapter;

import net.lucode.hackware.magicindicator.MagicIndicator;
import net.lucode.hackware.magicindicator.buildins.commonnavigator.CommonNavigator;
import net.lucode.hackware.magicindicator.buildins.commonnavigator.abs.CommonNavigatorAdapter;
import net.lucode.hackware.magicindicator.buildins.commonnavigator.abs.IPagerIndicator;
import net.lucode.hackware.magicindicator.buildins.commonnavigator.abs.IPagerTitleView;
import net.lucode.hackware.magicindicator.buildins.commonnavigator.indicators.LinePagerIndicator;
import net.lucode.hackware.magicindicator.buildins.commonnavigator.titles.DummyPagerTitleView;

import java.util.ArrayList;
import java.util.List;

/**
 * 2019-11-13 10:58
 * 面板管理
 */
public class FaceManger {

    private FaceManger() {
    }

    private static class FaceMangerBinder {
        final static FaceManger faceManger = new FaceManger();
    }

    public static FaceManger getInstance() {
        return FaceMangerBinder.faceManger;
    }

    /**
     * 功能面板
     *
     * @return view
     */
    public View getFunctionView(IConversationContext iConversationContext, IFuncListener iFuncListener) {
        View view = LayoutInflater.from(iConversationContext.getChatActivity()).inflate(R.layout.panel_function_layout, null);
        ViewPager2 viewPager2 = view.findViewById(R.id.viewPage);
        int h = WKConstants.getKeyboardHeight();
        if (h == 0) {
            h = AndroidUtilities.dp(280);
        }
        //软键盘高度减去指示器的高度就是功能菜单的高度
        int showH = h - AndroidUtilities.dp(23);
//        viewPager2.getLayoutParams().height = showH;
        MagicIndicator magicIndicator = view.findViewById(R.id.magicIndicator);
        List<ChatFunctionMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.chatFunction, iConversationContext);
        List<List<ChatFunctionMenu>> allList = new ArrayList<>();
        List<ChatFunctionMenu> tempList = new ArrayList<>();
        for (int i = 0, size = list.size(); i < size; i++) {
            if (tempList.size() < 8) {
                tempList.add(list.get(i));
                if (i == list.size() - 1) {
                    allList.add(tempList);
                    tempList = new ArrayList<>();
                }
            } else {
                allList.add(tempList);
                tempList = new ArrayList<>();
            }
        }
        magicIndicator.getLayoutParams().width = AndroidUtilities.dp(allList.size() * 10);
        FunctionViewPageAdapter adapter = new FunctionViewPageAdapter(iConversationContext.getChatActivity(), allList, showH, iFuncListener);
        viewPager2.setAdapter(adapter);
        initMagicIndicator2(iConversationContext.getChatActivity(), allList, magicIndicator, viewPager2);
        return view;
    }


    public interface IFuncListener {
        void onFuncLick(ChatFunctionMenu functionMenu);
    }

    private void initMagicIndicator2(Context context, List<List<ChatFunctionMenu>> list, MagicIndicator magicIndicator, final ViewPager2 mViewPager) {
        CommonNavigator commonNavigator = new CommonNavigator(context);
        commonNavigator.setAdjustMode(true);
        commonNavigator.setAdapter(new CommonNavigatorAdapter() {
            @Override
            public int getCount() {
                return list == null ? 0 : list.size();
            }

            @Override
            public IPagerTitleView getTitleView(Context context, final int index) {
                return new DummyPagerTitleView(context);
            }

            @Override
            public IPagerIndicator getIndicator(Context context) {
                LinePagerIndicator indicator = new LinePagerIndicator(context);
                float lineHeight = AndroidUtilities.dp(3);
                indicator.setLineHeight(lineHeight);
                indicator.setRoundRadius(AndroidUtilities.dp(2));
                indicator.setColors(Theme.colorAccount);
                return indicator;
            }
        });
        magicIndicator.setNavigator(commonNavigator);

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                magicIndicator.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }

            @Override
            public void onPageSelected(int position) {
                magicIndicator.onPageSelected(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                magicIndicator.onPageScrollStateChanged(state);
            }
        });
    }
}
