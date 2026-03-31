package com.xinbida.wukongim.manager;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.db.ChannelMembersDbManager;
import com.xinbida.wukongim.db.WKDBColumns;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.interfaces.IAddChannelMemberListener;
import com.xinbida.wukongim.interfaces.IChannelMemberInfoListener;
import com.xinbida.wukongim.interfaces.IGetChannelMemberInfo;
import com.xinbida.wukongim.interfaces.IGetChannelMemberList;
import com.xinbida.wukongim.interfaces.IGetChannelMemberListResult;
import com.xinbida.wukongim.interfaces.IRefreshChannelMember;
import com.xinbida.wukongim.interfaces.IRemoveChannelMember;
import com.xinbida.wukongim.interfaces.ISyncChannelMembers;
import com.xinbida.wukongim.utils.WKCommonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5/20/21 5:50 PM
 * channel members 管理
 */
public class ChannelMembersManager extends BaseManager {
    private ChannelMembersManager() {
    }

    private static class ChannelMembersManagerBinder {
        static final ChannelMembersManager channelMembersManager = new ChannelMembersManager();
    }

    public static ChannelMembersManager getInstance() {
        return ChannelMembersManagerBinder.channelMembersManager;
    }

    private ConcurrentHashMap<String, IRefreshChannelMember> refreshMemberMap;
    private ConcurrentHashMap<String, IRemoveChannelMember> removeChannelMemberMap;//监听添加频道成员
    private ConcurrentHashMap<String, IAddChannelMemberListener> addChannelMemberMap;
    private ISyncChannelMembers syncChannelMembers;
    //获取频道成员监听
    private IGetChannelMemberInfo iGetChannelMemberInfoListener;
    private IGetChannelMemberList iGetChannelMemberList;


    //最大版本成员
    @Deprecated
    public WKChannelMember getMaxVersionMember(String channelID, byte channelType) {
        return ChannelMembersDbManager.getInstance().queryMaxVersionMember(channelID, channelType);
    }

    public long getMaxVersion(String channelID, byte channelType) {
        return ChannelMembersDbManager.getInstance().queryMaxVersion(channelID, channelType);
    }

    public List<WKChannelMember> getRobotMembers(String channelID, byte channelType) {
        return ChannelMembersDbManager.getInstance().queryRobotMembers(channelID, channelType);
    }

    public List<WKChannelMember> getWithRole(String channelID, byte channelType, int role) {
        return ChannelMembersDbManager.getInstance().queryWithRole(channelID, channelType, role);
    }

    public synchronized void save(WKChannelMember member) {
        if (member == null) {
            return;
        }

        List<WKChannelMember> list = new ArrayList<>();
        list.add(member);
        int handelType = 0; // 修改
        WKChannelMember tempMember = ChannelMembersDbManager.getInstance().query(member.channelID, member.channelType, member.memberUID);
        if (tempMember == null) {
            handelType = 1;// 新增
        } else {
            if (member.isDeleted == 1 && tempMember.isDeleted == 0) {
                handelType = 2;// 删除
            }
            if (member.isDeleted == 0 && tempMember.isDeleted == 1) {
                handelType = 1;// 新增
            }
        }
        ChannelMembersDbManager.getInstance().insert(member);
        if (handelType == 0) {
            setRefreshChannelMember(member, true);
        }
        if (handelType == 1) {
            setOnAddChannelMember(list);
        }
        if (handelType == 2) {
            setOnRemoveChannelMember(list);
        }
    }

    /**
     * 批量保存成员
     *
     * @param list 成员数据
     */
    public synchronized void save(List<WKChannelMember> list) {
        if (WKCommonUtils.isEmpty(list)) return;
        new Thread(() -> {
            String channelID = list.get(0).channelID;
            byte channelType = list.get(0).channelType;

            List<WKChannelMember> addList = new ArrayList<>();
            List<WKChannelMember> deleteList = new ArrayList<>();
            List<WKChannelMember> updateList = new ArrayList<>();

            List<WKChannelMember> existList = new ArrayList<>();
            List<String> uidList = new ArrayList<>();
            for (WKChannelMember channelMember : list) {
                if (uidList.size() == 200) {
                    List<WKChannelMember> tempList = ChannelMembersDbManager.getInstance().queryWithUIDs(channelMember.channelID, channelMember.channelType, uidList);
                    if (WKCommonUtils.isNotEmpty(tempList))
                        existList.addAll(tempList);
                    uidList.clear();
                }
                uidList.add(channelMember.memberUID);
            }

            if (WKCommonUtils.isNotEmpty(uidList)) {
                List<WKChannelMember> tempList = ChannelMembersDbManager.getInstance().queryWithUIDs(channelID, channelType, uidList);
                if (WKCommonUtils.isNotEmpty(tempList))
                    existList.addAll(tempList);
                uidList.clear();
            }

            for (WKChannelMember channelMember : list) {
                boolean isNewMember = true;
                for (int i = 0, size = existList.size(); i < size; i++) {
                    if (channelMember.memberUID.equals(existList.get(i).memberUID)) {
                        isNewMember = false;
                        if (channelMember.isDeleted == 1) {
                            deleteList.add(channelMember);
                        } else {
                            if (existList.get(i).isDeleted == 1) {
                                isNewMember = true;
                            } else
                                updateList.add(channelMember);
                        }
                        break;
                    }
                }
                if (isNewMember) {
                    addList.add(channelMember);
                }
            }

            // 先保存或修改成员
            ChannelMembersDbManager.getInstance().insertMembers(list, existList);

            if (WKCommonUtils.isNotEmpty(addList)) {
                setOnAddChannelMember(addList);
            }
            if (WKCommonUtils.isNotEmpty(deleteList))
                setOnRemoveChannelMember(deleteList);

            if (WKCommonUtils.isNotEmpty(updateList)) {
                for (int i = 0, size = updateList.size(); i < size; i++) {
                    setRefreshChannelMember(updateList.get(i), i == updateList.size() - 1);
                }
            }
        }).start();

    }

    /**
     * 批量移除频道成员
     *
     * @param list 频道成员
     */
    public void delete(List<WKChannelMember> list) {
        runOnMainThread(() -> ChannelMembersDbManager.getInstance().deleteMembers(list));
    }

    /**
     * 通过状态查询频道成员
     *
     * @param channelId   频道ID
     * @param channelType 频道类型
     * @param status      状态
     * @return List<>
     */
    public List<WKChannelMember> getWithStatus(String channelId, byte channelType, int status) {
        return ChannelMembersDbManager.getInstance().queryWithStatus(channelId, channelType, status);
    }

    /**
     * 修改频道成员备注
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param uid         用户ID
     * @param remarkName  备注
     */
    public boolean updateRemarkName(String channelID, byte channelType, String uid, String remarkName) {
        return ChannelMembersDbManager.getInstance().updateWithField(channelID, channelType, uid, WKDBColumns.WKChannelMembersColumns.member_remark, remarkName);
    }

    /**
     * 修改频道成员名称
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param uid         用户ID
     * @param name        名称
     */
    public boolean updateMemberName(String channelID, byte channelType, String uid, String name) {
        return ChannelMembersDbManager.getInstance().updateWithField(channelID, channelType, uid, WKDBColumns.WKChannelMembersColumns.member_name, name);
    }

    /**
     * 修改频道成员状态
     *
     * @param channelId   频道ID
     * @param channelType 频道类型
     * @param uid         用户ID
     * @param status      状态
     */
    public boolean updateMemberStatus(String channelId, byte channelType, String uid, int status) {
        return ChannelMembersDbManager.getInstance().updateWithField(channelId, channelType, uid, WKDBColumns.WKChannelMembersColumns.status, String.valueOf(status));
    }

    public void addOnGetChannelMembersListener(IGetChannelMemberList iGetChannelMemberList) {
        this.iGetChannelMemberList = iGetChannelMemberList;
    }

    public void getWithPageOrSearch(String channelID, byte channelType, String searchKey, int page, int limit, @NonNull IGetChannelMemberListResult iGetChannelMemberListResult) {
        List<WKChannelMember> list;
        if (TextUtils.isEmpty(searchKey)) {
            list = getMembersWithPage(channelID, channelType, page, limit);
        } else {
            list = searchMembers(channelID, channelType, searchKey, page, limit);
        }

        iGetChannelMemberListResult.onResult(list, false);
        int groupType = 0;
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null && channel.remoteExtraMap != null && channel.remoteExtraMap.containsKey(WKChannelExtras.groupType)) {
            Object groupTypeObject = channel.remoteExtraMap.get(WKChannelExtras.groupType);
            if (groupTypeObject instanceof Integer) {
                groupType = (int) groupTypeObject;
            }
        }
        if (iGetChannelMemberList != null && groupType == 1) {
            iGetChannelMemberList.request(channelID, channelType, searchKey, page, limit, list1 -> {
                iGetChannelMemberListResult.onResult(list1, true);
                if (WKCommonUtils.isNotEmpty(list1)) {
                  //  ChannelMembersDbManager.getInstance().deleteWithChannel(channelID, channelType);
                    save(list1);
                }
            });
        }
    }

    public void addOnGetChannelMemberListener(IGetChannelMemberInfo iGetChannelMemberInfoListener) {
        this.iGetChannelMemberInfoListener = iGetChannelMemberInfoListener;
    }

    public void refreshChannelMemberCache(WKChannelMember channelMember) {
        if (channelMember == null) return;
        List<WKChannelMember> list = new ArrayList<>();
        list.add(channelMember);
        ChannelMembersDbManager.getInstance().insertMembers(list);
    }

    /**
     * 添加加入频道成员监听
     *
     * @param listener 回调
     */
    public void addOnAddChannelMemberListener(String key, IAddChannelMemberListener listener) {
        if (TextUtils.isEmpty(key) || listener == null) return;
        if (addChannelMemberMap == null)
            addChannelMemberMap = new ConcurrentHashMap<>();
        addChannelMemberMap.put(key, listener);
    }

    public void removeAddChannelMemberListener(String key) {
        if (TextUtils.isEmpty(key) || addChannelMemberMap == null) return;
        addChannelMemberMap.remove(key);
    }

    public void setOnAddChannelMember(List<WKChannelMember> list) {
        if (addChannelMemberMap != null) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IAddChannelMemberListener> entry : addChannelMemberMap.entrySet()) {
                    entry.getValue().onAddMembers(list);
                }
            });
        }
    }

    /**
     * 获取频道成员信息
     *
     * @param channelId                  频道ID
     * @param uid                        成员ID
     * @param iChannelMemberInfoListener 回调
     */
    public WKChannelMember getMember(String channelId, byte channelType, String uid, IChannelMemberInfoListener iChannelMemberInfoListener) {
        if (iGetChannelMemberInfoListener != null && !TextUtils.isEmpty(channelId) && !TextUtils.isEmpty(uid) && iChannelMemberInfoListener != null) {
            return iGetChannelMemberInfoListener.onResult(channelId, channelType, uid, iChannelMemberInfoListener);
        } else return null;
    }

    public WKChannelMember getMember(String channelID, byte channelType, String uid) {
        return ChannelMembersDbManager.getInstance().query(channelID, channelType, uid);
    }

    public List<WKChannelMember> getMembers(String channelID, byte channelType) {
        return ChannelMembersDbManager.getInstance().query(channelID, channelType);
    }

    private List<WKChannelMember> searchMembers(String channelId, byte channelType, String keyword, int page, int size) {
        return ChannelMembersDbManager.getInstance().search(channelId, channelType, keyword, page, size);
    }

    private List<WKChannelMember> getMembersWithPage(String channelId, byte channelType, int page, int size) {
        return ChannelMembersDbManager.getInstance().queryWithPage(channelId, channelType, page, size);
    }

    public List<WKChannelMember> getDeletedMembers(String channelID, byte channelType) {
        return ChannelMembersDbManager.getInstance().queryDeleted(channelID, channelType);
    }

    //成员数量
    public int getMemberCount(String channelID, byte channelType) {
        return ChannelMembersDbManager.getInstance().queryCount(channelID, channelType);
    }

    public void addOnRefreshChannelMemberInfo(String key, IRefreshChannelMember iRefreshChannelMemberListener) {
        if (TextUtils.isEmpty(key) || iRefreshChannelMemberListener == null) return;
        if (refreshMemberMap == null)
            refreshMemberMap = new ConcurrentHashMap<>();
        refreshMemberMap.put(key, iRefreshChannelMemberListener);
    }

    public void removeRefreshChannelMemberInfo(String key) {
        if (TextUtils.isEmpty(key) || refreshMemberMap == null) return;
        refreshMemberMap.remove(key);
    }

    public void setRefreshChannelMember(WKChannelMember channelMember, boolean isEnd) {
        if (refreshMemberMap != null && channelMember != null) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IRefreshChannelMember> entry : refreshMemberMap.entrySet()) {
                    entry.getValue().onRefresh(channelMember, isEnd);
                }
            });
        }
    }

    public void addOnRemoveChannelMemberListener(String key, IRemoveChannelMember listener) {
        if (listener == null || TextUtils.isEmpty(key)) return;
        if (removeChannelMemberMap == null) removeChannelMemberMap = new ConcurrentHashMap<>();
        removeChannelMemberMap.put(key, listener);
    }

    public void removeRemoveChannelMemberListener(String key) {
        if (TextUtils.isEmpty(key) || removeChannelMemberMap == null) return;
        removeChannelMemberMap.remove(key);
    }

    public void setOnRemoveChannelMember(List<WKChannelMember> list) {
        if (removeChannelMemberMap != null) {
            runOnMainThread(() -> {
                for (Map.Entry<String, IRemoveChannelMember> entry : removeChannelMemberMap.entrySet()) {
                    entry.getValue().onRemoveMembers(list);
                }
            });
        }
    }

    public void addOnSyncChannelMembers(ISyncChannelMembers syncChannelMembersListener) {
        this.syncChannelMembers = syncChannelMembersListener;
    }

    public void setOnSyncChannelMembers(String channelID, byte channelType) {
        if (syncChannelMembers != null) {
            runOnMainThread(() -> {
                syncChannelMembers.onSyncChannelMembers(channelID, channelType);
            });
        }
    }
}
