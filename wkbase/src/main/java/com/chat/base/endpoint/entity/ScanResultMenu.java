package com.chat.base.endpoint.entity;

import java.util.HashMap;

/**
 * 2020-09-07 10:20
 * 扫一扫结果
 */
public class ScanResultMenu extends BaseEndpoint {
    public IResultClick iResultClick;

    public ScanResultMenu(IResultClick iResultClick) {
        this.iResultClick = iResultClick;
    }

    public interface IResultClick {
        boolean invoke(HashMap<String, Object> hashMap);
    }
}
