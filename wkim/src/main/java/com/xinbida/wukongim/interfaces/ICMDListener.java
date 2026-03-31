package com.xinbida.wukongim.interfaces;


import com.xinbida.wukongim.entity.WKCMD;

/**
 * 2/3/21 2:23 PM
 * cmd监听
 */
public interface ICMDListener {
    void onMsg(WKCMD wkcmd);
}
