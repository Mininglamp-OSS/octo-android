package com.xinbida.wukongim.manager;

import android.text.TextUtils;

import com.xinbida.wukongim.db.ChannelDBManager;
import com.xinbida.wukongim.db.WKDBColumns;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelSearchResult;
import com.xinbida.wukongim.interfaces.IChannelInfoListener;
import com.xinbida.wukongim.interfaces.IGetChannelInfo;
import com.xinbida.wukongim.interfaces.IRefreshChannel;
import com.xinbida.wukongim.interfaces.IRefreshChannelAvatar;
import com.xinbida.wukongim.utils.WKCommonUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 5/20/21 5:49 PM
 * channel管理
 */
public class ChannelManager extends BaseManager {
    private final String TAG = "ChannelManager";

    private ChannelManager() {
    }

    private static class ChannelManagerBinder {
        static final ChannelManager channelManager = new ChannelManager();
    }

    public static ChannelManager getInstance() {
        return ChannelManagerBinder.channelManager;
    }

    private IRefreshChannelAvatar iRefreshChannelAvatar;
    private IGetChannelInfo iGetChannelInfo;
    private final CopyOnWriteArrayList<WKChannel> wkChannelList = new CopyOnWriteArrayList<>();
    /**
     *  · ChannelInfoCache（对照 iOS `WKChannelManager.cacheDict`）。
     *
     * <p>Key 形如 {@code channelId|channelType}，value 与 {@link #wkChannelList} 共享同一
     * {@link WKChannel} 实例（在 {@link #updateChannel(WKChannel)} / DB miss 分支里一并
     * put，保证 in-place 字段修改在 list 与 map 双视图下都可见）。
     *
     * <p>读路径（{@link #getChannel(String, byte)}）走 ConcurrentHashMap 无锁查找，
     * 命中后立即返回，**不进入 synchronized 慢路径**。这是  H3（adapter.convert
     * 每行 getWkChannel × 36 主线程 SQLCipher 查询）的核心止血：sync 完成后首屏 bind，
     * 第 1 次 miss 查 DB 并回填 cache，之后同一 Space 内的后续 bind 全部 O(1) 内存命中。
     */
    private final ConcurrentHashMap<String, WKChannel> channelInfoCache = new ConcurrentHashMap<>();
    //监听刷新频道
    private ConcurrentHashMap<String, IRefreshChannel> refreshChannelMap;

    /**
     *  · 生成 ChannelInfoCache 的 key。保持与 iOS `channelId|channelType` 对齐。
     */
    private static String cacheKey(String channelID, byte channelType) {
        return channelID + "|" + (int) channelType;
    }

    public WKChannel getChannel(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID)) return null;
        //  · Fast path：ConcurrentHashMap 无锁命中直接返回，避免 synchronized
        // 把 adapter.convert 的 N 行查询串行化（原 H3 主线程卡顿根因）。
        String key = cacheKey(channelID, channelType);
        WKChannel cached = channelInfoCache.get(key);
        if (cached != null) return cached;
        return getChannelSlowPath(channelID, channelType, key);
    }

    /**
     *  · 慢路径：cache miss 时走 synchronized + list 线性扫描 + DB fallback。
     *
     * <p>保留 {@link #wkChannelList} 的原有语义是因为别的路径（比如
     * {@link #updateChannel(String, byte, String, Object)} 的单字段 in-place 修改）依赖
     * 同一 {@link WKChannel} 实例被多处引用。map put 的也是这同一实例，因此 map 命中
     * 读出的对象字段变更可以被看到。
     */
    private synchronized WKChannel getChannelSlowPath(String channelID, byte channelType, String key) {
        // double-check：进 synchronized 之前可能另一线程已经回填
        WKChannel cached = channelInfoCache.get(key);
        if (cached != null) return cached;
        WKChannel wkChannel = null;
        for (WKChannel channel : wkChannelList) {
            if (channel != null && channel.channelID.equals(channelID) && channel.channelType == channelType) {
                wkChannel = channel;
                break;
            }
        }
        if (wkChannel == null) {
            wkChannel = ChannelDBManager.getInstance().query(channelID, channelType);
            if (wkChannel != null) {
                wkChannelList.add(wkChannel);
            }
        }
        if (wkChannel != null) {
            channelInfoCache.put(key, wkChannel);
        }
        return wkChannel;
    }

    /**
     *  · 批量预热 ChannelInfoCache（对照 iOS sync 完成后的 `cacheDict` 回填）。
     *
     * <p>典型调用点：Space 切换后 sync 完成，App 层一次性把相关 channel 列表喂进来，
     * 后续 RecyclerView bind 的 `getWkChannel` 直接走 fast path，不再触发 36×N 次
     * 主线程 DB 查询。
     *
     * <p>线程安全：ConcurrentHashMap.put 与 synchronized 慢路径的 put 并发安全，
     * 采用 putIfAbsent 语义以避免覆盖 updateChannel 路径下正被其他线程 in-place
     * 修改的已有实例（优先保留已在用的引用）。
     */
    public void preloadChannels(List<WKChannel> channels) {
        if (WKCommonUtils.isEmpty(channels)) return;
        for (int i = 0, size = channels.size(); i < size; i++) {
            WKChannel c = channels.get(i);
            if (c == null || TextUtils.isEmpty(c.channelID)) continue;
            String key = cacheKey(c.channelID, c.channelType);
            WKChannel existing = channelInfoCache.putIfAbsent(key, c);
            if (existing == null) {
                // 保持 list 与 map 的成员一致（首次进入时）
                wkChannelList.addIfAbsent(c);
            }
        }
    }

    // 从网络获取channel
    public void fetchChannelInfo(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID)) return;
        WKChannel channel = getChannel(channelID, channelType, wkChannel -> {
            if (wkChannel != null)
                saveOrUpdateChannel(wkChannel);
        });
        if (channel != null) {
            saveOrUpdateChannel(channel);
        }
    }

    public WKChannel getChannel(String channelId, byte channelType, IChannelInfoListener iChannelInfoListener) {
        if (this.iGetChannelInfo != null && !TextUtils.isEmpty(channelId) && iChannelInfoListener != null) {
            return iGetChannelInfo.onGetChannelInfo(channelId, channelType, iChannelInfoListener);
        } else return null;
    }

    public void addOnGetChannelInfoListener(IGetChannelInfo iGetChannelInfoListener) {
        this.iGetChannelInfo = iGetChannelInfoListener;
    }

    public void saveOrUpdateChannel(WKChannel channel) {
        if (channel == null) return;
        //先更改内存数据
        updateChannel(channel);
        setRefreshChannel(channel, true);
        ChannelDBManager.getInstance().insertOrUpdate(channel);
    }


    /**
     * 修改频道信息
     *
     * @param channel 频道
     */
    private void updateChannel(WKChannel channel) {
        if (channel == null) return;
        boolean isAdd = true;
        for (int i = 0, size = wkChannelList.size(); i < size; i++) {
            if (wkChannelList.get(i).channelID.equals(channel.channelID) && wkChannelList.get(i).channelType == channel.channelType) {
                isAdd = false;
                wkChannelList.get(i).forbidden = channel.forbidden;
                wkChannelList.get(i).channelName = channel.channelName;
                wkChannelList.get(i).avatar = channel.avatar;
                wkChannelList.get(i).category = channel.category;
                wkChannelList.get(i).lastOffline = channel.lastOffline;
                wkChannelList.get(i).online = channel.online;
                wkChannelList.get(i).follow = channel.follow;
                wkChannelList.get(i).top = channel.top;
                wkChannelList.get(i).channelRemark = channel.channelRemark;
                wkChannelList.get(i).status = channel.status;
                wkChannelList.get(i).version = channel.version;
                wkChannelList.get(i).invite = channel.invite;
                wkChannelList.get(i).localExtra = channel.localExtra;
                wkChannelList.get(i).mute = channel.mute;
                wkChannelList.get(i).save = channel.save;
                wkChannelList.get(i).showNick = channel.showNick;
                wkChannelList.get(i).isDeleted = channel.isDeleted;
                wkChannelList.get(i).receipt = channel.receipt;
                wkChannelList.get(i).robot = channel.robot;
                wkChannelList.get(i).flameSecond = channel.flameSecond;
                wkChannelList.get(i).flame = channel.flame;
                wkChannelList.get(i).deviceFlag = channel.deviceFlag;
                wkChannelList.get(i).parentChannelID = channel.parentChannelID;
                wkChannelList.get(i).parentChannelType = channel.parentChannelType;
                wkChannelList.get(i).avatarCacheKey = channel.avatarCacheKey;
                wkChannelList.get(i).remoteExtraMap = channel.remoteExtraMap;
                break;
            }
        }
        if (isAdd) {
            wkChannelList.add(channel);
        }
        //  · 与 ChannelInfoCache 保持一致：
        // - isAdd 分支把新实例写入 map
        // - 命中分支（isAdd=false）则把 map 里的 key 指向 list 中当前被 in-place 修改的实例，
        //   避免「list 里的引用被改了，map 里还指向旧实例」的潜在漂移。
        channelInfoCache.put(cacheKey(channel.channelID, channel.channelType), resolveInstance(channel));
    }

    /**
     *  · 返回 {@link #wkChannelList} 中与入参 key 相同的实例；若不存在则返回入参本身。
     * 配合 {@link #updateChannel(WKChannel)} 的 in-place 字段修改路径，保证 cache 指向
     * list 中真正被修改的那份对象。
     */
    private WKChannel resolveInstance(WKChannel channel) {
        for (int i = 0, size = wkChannelList.size(); i < size; i++) {
            WKChannel c = wkChannelList.get(i);
            if (c != null
                    && c.channelID != null
                    && c.channelID.equals(channel.channelID)
                    && c.channelType == channel.channelType) {
                return c;
            }
        }
        return channel;
    }

    private void updateChannel(String channelID, byte channelType, String key, Object value) {
        if (TextUtils.isEmpty(channelID) || TextUtils.isEmpty(key)) return;
        for (int i = 0, size = wkChannelList.size(); i < size; i++) {
            if (wkChannelList.get(i).channelID.equals(channelID) && wkChannelList.get(i).channelType == channelType) {
                switch (key) {
                    case WKDBColumns.WKChannelColumns.avatar_cache_key:
                        wkChannelList.get(i).avatarCacheKey = (String) value;
                        break;
                    case WKDBColumns.WKChannelColumns.remote_extra:
                        wkChannelList.get(i).remoteExtraMap = (HashMap<String, Object>) value;
                        break;
                    case WKDBColumns.WKChannelColumns.avatar:
                        wkChannelList.get(i).avatar = (String) value;
                        break;
                    case WKDBColumns.WKChannelColumns.channel_remark:
                        wkChannelList.get(i).channelRemark = (String) value;
                        break;
                    case WKDBColumns.WKChannelColumns.channel_name:
                        wkChannelList.get(i).channelName = (String) value;
                        break;
                    case WKDBColumns.WKChannelColumns.follow:
                        wkChannelList.get(i).follow = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.forbidden:
                        wkChannelList.get(i).forbidden = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.invite:
                        wkChannelList.get(i).invite = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.is_deleted:
                        wkChannelList.get(i).isDeleted = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.last_offline:
                        wkChannelList.get(i).lastOffline = (long) value;
                        break;
                    case WKDBColumns.WKChannelColumns.mute:
                        wkChannelList.get(i).mute = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.top:
                        wkChannelList.get(i).top = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.online:
                        wkChannelList.get(i).online = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.receipt:
                        wkChannelList.get(i).receipt = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.save:
                        wkChannelList.get(i).save = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.show_nick:
                        wkChannelList.get(i).showNick = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.status:
                        wkChannelList.get(i).status = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.username:
                        wkChannelList.get(i).username = (String) value;
                        break;
                    case WKDBColumns.WKChannelColumns.flame:
                        wkChannelList.get(i).flame = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.flame_second:
                        wkChannelList.get(i).flameSecond = (int) value;
                        break;
                    case WKDBColumns.WKChannelColumns.localExtra:
                        wkChannelList.get(i).localExtra = (HashMap<String, Object>) value;
                        break;
                }
                setRefreshChannel(wkChannelList.get(i), true);
                break;
            }
        }
    }

    /**
     * 添加或修改频道信息
     *
     * @param list 频道数据
     */
    public void saveOrUpdateChannels(List<WKChannel> list) {
        if (WKCommonUtils.isEmpty(list)) return;
        // 先修改内存数据
        for (int i = 0, size = list.size(); i < size; i++) {
            updateChannel(list.get(i));
            setRefreshChannel(list.get(i), i == list.size() - 1);
        }
        ChannelDBManager.getInstance().insertChannels(list);
    }

    /**
     * 修改频道状态
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param status      状态
     */
    public void updateStatus(String channelID, byte channelType, int status) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.status, status);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.status, String.valueOf(status));
    }


    /**
     * 修改频道名称
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param name        名称
     */
    public void updateName(String channelID, byte channelType, String name) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.channel_name, name);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.channel_name, name);
    }

    /**
     * 获取频道信息
     *
     * @param channelType 频道类型
     * @param status      状态
     * @return List<WKChannel>
     */
    public List<WKChannel> getWithStatus(byte channelType, int status) {
        return ChannelDBManager.getInstance().queryWithStatus(channelType, status);
    }

    public List<WKChannel> getWithChannelIdsAndChannelType(List<String> channelIds, byte channelType) {
        return ChannelDBManager.getInstance().queryWithChannelIdsAndChannelType(channelIds, channelType);
    }

    public List<WKChannel> getChannels(List<String> channelIds) {
        return ChannelDBManager.getInstance().queryWithChannelIds(channelIds);
    }

    /**
     * 搜索频道
     *
     * @param keyword 关键字
     * @return List<WKChannelSearchResult>
     */
    public List<WKChannelSearchResult> search(String keyword) {
        return ChannelDBManager.getInstance().search(keyword);
    }

    /**
     * 搜索频道
     *
     * @param keyword     关键字
     * @param channelType 频道类型
     * @return List<WKChannel>
     */
    public List<WKChannel> searchWithChannelType(String keyword, byte channelType) {
        return ChannelDBManager.getInstance().searchWithChannelType(keyword, channelType);
    }

    public List<WKChannel> searchWithChannelTypeAndFollow(String keyword, byte channelType, int follow) {
        return ChannelDBManager.getInstance().searchWithChannelTypeAndFollow(keyword, channelType, follow);
    }

    /**
     * 获取频道信息
     *
     * @param channelType 频道类型
     * @param follow      关注状态
     * @return List<WKChannel>
     */
    public List<WKChannel> getWithChannelTypeAndFollow(byte channelType, int follow) {
        return ChannelDBManager.getInstance().queryWithChannelTypeAndFollow(channelType, follow);
    }

    /**
     * 修改某个频道免打扰
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param isMute      1：免打扰
     */
    public void updateMute(String channelID, byte channelType, int isMute) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.mute, isMute);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.mute, String.valueOf(isMute));
    }

    /**
     * 修改备注信息
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param hashExtra   扩展字段
     */
    public void updateLocalExtra(String channelID, byte channelType, HashMap<String, Object> hashExtra) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.localExtra, hashExtra);
        if (hashExtra != null) {
            JSONObject jsonObject = new JSONObject();
            for (String key : hashExtra.keySet()) {
                try {
                    jsonObject.put(key, hashExtra.get(key));
                } catch (JSONException e) {
                    WKLoggerUtils.getInstance().e(TAG, "updateLocalExtra error");
                }
            }
            ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.localExtra, jsonObject.toString());
        }
    }

    /**
     * 修改频道是否保存在通讯录
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param isSave      1:保存
     */
    public void updateSave(String channelID, byte channelType, int isSave) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.save, isSave);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.save, String.valueOf(isSave));
    }

    /**
     * 是否显示频道昵称
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param showNick    1：显示频道昵称
     */
    public void updateShowNick(String channelID, byte channelType, int showNick) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.show_nick, showNick);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.show_nick, String.valueOf(showNick));
    }

    /**
     * 修改某个频道是否置顶
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param top         1：置顶
     */
    public void updateTop(String channelID, byte channelType, int top) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.top, top);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.top, String.valueOf(top));
    }

    /**
     * 修改某个频道的备注
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param remark      备注
     */
    public void updateRemark(String channelID, byte channelType, String remark) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.channel_remark, remark);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.channel_remark, remark);
    }

    /**
     * 修改关注状态
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param follow      是否关注
     */
    public void updateFollow(String channelID, byte channelType, int follow) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.follow, follow);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.follow, String.valueOf(follow));
    }

    /**
     * 通过follow和status查询频道
     *
     * @param channelType 频道类型
     * @param follow      是否关注 好友或陌生人
     * @param status      状态 正常或黑名单
     * @return list
     */
    public List<WKChannel> getWithFollowAndStatus(byte channelType, int follow, int status) {
        return ChannelDBManager.getInstance().queryWithFollowAndStatus(channelType, follow, status);
    }

    public void updateAvatarCacheKey(String channelID, byte channelType, String avatar) {
        updateChannel(channelID, channelType, WKDBColumns.WKChannelColumns.avatar_cache_key, avatar);
        ChannelDBManager.getInstance().updateWithField(channelID, channelType, WKDBColumns.WKChannelColumns.avatar_cache_key, avatar);
    }

    public void addOnRefreshChannelAvatar(IRefreshChannelAvatar iRefreshChannelAvatar) {
        this.iRefreshChannelAvatar = iRefreshChannelAvatar;
    }

    public void setOnRefreshChannelAvatar(String channelID, byte channelType) {
        if (iRefreshChannelAvatar != null) {
            runOnMainThread(() -> iRefreshChannelAvatar.onRefreshChannelAvatar(channelID, channelType));
        }
    }

    public synchronized void clearARMCache() {
        wkChannelList.clear();
        //  · ChannelInfoCache 必须同步清理，否则切账号 / 退出登录后 map 里的
        // stale 实例会让 fast path 返回旧 Space 的 channel（同 iOS `removeChannelAllCache`）。
        channelInfoCache.clear();
    }

    // 刷新频道
    public void setRefreshChannel(WKChannel channel, boolean isEnd) {
        if (refreshChannelMap != null) {
            runOnMainThread(() -> {
                updateChannel(channel);
                for (Map.Entry<String, IRefreshChannel> entry : refreshChannelMap.entrySet()) {
                    entry.getValue().onRefreshChannel(channel, isEnd);
                }
            });
        }
    }

    // 监听刷新普通
    public void addOnRefreshChannelInfo(String key, IRefreshChannel iRefreshChannelListener) {
        if (TextUtils.isEmpty(key)) return;
        if (refreshChannelMap == null) refreshChannelMap = new ConcurrentHashMap<>();
        if (iRefreshChannelListener != null)
            refreshChannelMap.put(key, iRefreshChannelListener);
    }

    // 移除频道刷新监听
    public void removeRefreshChannelInfo(String key) {
        if (TextUtils.isEmpty(key) || refreshChannelMap == null) return;
        refreshChannelMap.remove(key);
    }

}
