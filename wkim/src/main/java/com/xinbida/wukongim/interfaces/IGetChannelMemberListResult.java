package com.xinbida.wukongim.interfaces;

import com.xinbida.wukongim.entity.WKChannelMember;

import java.util.List;

public interface IGetChannelMemberListResult {
    public void onResult(List<WKChannelMember> list, boolean isRemote);
}
