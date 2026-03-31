package com.xinbida.wukongim.interfaces;


import com.xinbida.wukongim.entity.WKChannelMember;

import java.util.List;

public interface IChannelMemberListResult {
    void onResult(List<WKChannelMember> list);
}
