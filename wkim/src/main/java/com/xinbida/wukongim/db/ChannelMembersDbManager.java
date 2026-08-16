package com.xinbida.wukongim.db;

import static com.xinbida.wukongim.db.WKDBColumns.TABLE.channel;
import static com.xinbida.wukongim.db.WKDBColumns.TABLE.channelMembers;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.xinbida.wukongim.BuildConfig;
import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.manager.ChannelMembersManager;
import com.xinbida.wukongim.utils.WKCommonUtils;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 2019-11-10 14:06
 * 频道成员数据管理
 */
public class ChannelMembersDbManager {
    private static final String TAG = "ChannelMembersDbManager";
    final String channelCols = channel + ".channel_remark," + channel + ".channel_name," + channel + ".avatar," + channel + ".avatar_cache_key";

    /**
     * 单成员缓存（对照 {@link com.xinbida.wukongim.manager.ChannelManager} 的 channelInfoCache）。
     *
     * <p>动机：Bugly 上 19 份 ANR 里有 7 份卡在 {@code getMember} —— RecyclerView 每 bind 一行
     * （会话列表 {@code ChatConversationAdapter} 与聊天页 {@code WKChatBaseProvider} 两处）
     * 都会经 {@code WKMsg.getMemberOfFrom()} 打一次本表。{@code WKMsg.memberOfFrom} 只是实例级
     * 懒缓存，列表 rebuild / 新消息对象即作废，且**只缓存非 null**，所以「发送者不在
     * channel_members 里」的消息每次 bind 都要重查一次 —— 故这里必须连负结果一起缓存。
     *
     * <p>Key：{@code channelId|channelType|memberUid}。
     *
     * <p>失效面（{@link #query(String, byte, String)} 的 SQL 是 channel_members LEFT JOIN channel，
     * 见 {@link #serializableChannelMember} 里 memberName/remark/avatar 被 channel 列覆写，
     * 所以缓存同时依赖两张表）：
     * <ul>
     *     <li>channel_members：本类是全仓库唯一写者（已核），所有写方法就地失效；</li>
     *     <li>channel：{@link ChannelDBManager} 只有 4 个写方法，其中 PERSONAL 频道的写入
     *         会回调 {@link #invalidateMemberUid(String)}。</li>
     * </ul>
     *
     * <p>命中时返回**副本**：缓存条目是进程级共享的，若把实例交出去，调用方就地改字段
     * （如 ChatActivity 收到 PERSONAL 频道刷新后改 memberName/memberRemark）会污染所有
     * 引用它的消息，且写入的语义可能不同（channel.channelRemark 是个人频道备注，
     * member_remark 是群内备注）。副本是一次浅拷贝，比它替掉的那次 DB 查询便宜得多。
     */
    private static final int MAX_CACHE_SIZE = 2000;
    /** 负缓存哨兵：DB 里查不到该成员时也要记住，否则每次 bind 都重查。 */
    private static final WKChannelMember NULL_MEMBER = new WKChannelMember();
    private final ConcurrentHashMap<String, WKChannelMember> memberCache = new ConcurrentHashMap<>();
    /** memberUid -> 该成员出现过的 cache key，供 channel 侧按 uid 精确失效，避免整表清空。 */
    private final ConcurrentHashMap<String, Set<String>> keysByMemberUid = new ConcurrentHashMap<>();
    /**
     * 失效代际。用于堵住读穿透缓存的经典竞态：
     * <pre>
     * 线程B: querySlowPath 读到旧值 ──────────────┐
     * 线程A:            写 DB + 失效（无可删）    │
     * 线程B:                        回填旧值 ← 脏 ┘
     * </pre>
     * {@code insert}/{@code update}/{@code querySlowPath} 互为 synchronized 不会交错，
     * 但 {@code insertMembers}（两个重载）与 {@code deleteWithChannel} 没有加锁，会。
     * 故读路径在发起 DB 查询前记下代际，回填时若代际已变就丢弃本次结果
     * （退化为不缓存，与改动前行为一致，是 fail-safe 方向）。
     *
     * <p>回填的发布顺序是「反向索引 add → memberCache put → 复查代际」。两个结构都在复查之前
     * 发布，于是任一交错都安全：失效方的代际自增落在复查之前 → 这里 remove；落在复查之后 →
     * 它必然已看见索引与缓存，由它 remove。只查 put 之前是不够的（失效落在 check 与 put 之间
     * 时脏值写在删除之后），只在 put 后复查也不够 —— {@link #invalidateMemberUid} 是唯一经
     * 反向索引到达 memberCache 的路径，它可能在 key 尚未进索引时跑完而什么都没删。
     * 而这份缓存没有 TTL，一次漏删就把错的昵称/头像钉到下次该成员被写或切账号。
     * 见 {@link #putCache}。
     */
    private final AtomicLong cacheGeneration = new AtomicLong();

    private ChannelMembersDbManager() {
    }

    private static class ChannelMembersManagerBinder {
        private final static ChannelMembersDbManager channelMembersManager = new ChannelMembersDbManager();
    }

    public static ChannelMembersDbManager getInstance() {
        return ChannelMembersManagerBinder.channelMembersManager;
    }

    private static String cacheKey(String channelId, byte channelType, String uid) {
        return channelId + "|" + (int) channelType + "|" + uid;
    }

    private void putCache(String key, String memberUid, WKChannelMember member, long generation) {
        // 代际已变说明读期间发生过写，本次结果可能已过期，直接丢弃不缓存
        if (cacheGeneration.get() != generation) return;
        // 惰性上限：只在超限时整清，不做 LRU —— 实际 key 数受「可见消息的发送者」约束，
        // 正常量级在几百，2000 是防御性护栏而非常态路径。
        if (memberCache.size() >= MAX_CACHE_SIZE) {
            clearCache();
            return;
        }
        // 发布顺序：先注册反向索引，再写缓存，最后复查代际 —— 两个结构都在复查之前发布。
        // 反过来（put → 复查 → add）关不掉 invalidateMemberUid：它是唯一经反向索引到达
        // memberCache 的失效路径，若整段跑在复查之后、add 之前，remove(memberUid) 时 key 还
        // 不在集合里，于是什么都没删，紧接着 add 又把 key 注册回去，脏值永久留下。
        keysByMemberUid.computeIfAbsent(memberUid, k -> ConcurrentHashMap.newKeySet()).add(key);
        memberCache.put(key, member == null ? NULL_MEMBER : copyOf(member));
        if (cacheGeneration.get() != generation) {
            memberCache.remove(key);
            // 反向索引里留下孤儿 key 是无害的（下次 invalidateMemberUid 会带走），
            // 但顺手清掉，理由同 invalidateChannel：只 remove、不删空 Set。
            Set<String> keys = keysByMemberUid.get(memberUid);
            if (keys != null) keys.remove(key);
        }
    }

    /**
     * 浅拷贝。{@code extraMap} 也复制一层，否则调用方改 map 内容照样穿透到缓存条目。
     */
    private static WKChannelMember copyOf(WKChannelMember src) {
        WKChannelMember copy = new WKChannelMember();
        copy.id = src.id;
        copy.channelID = src.channelID;
        copy.channelType = src.channelType;
        copy.memberUID = src.memberUID;
        copy.memberName = src.memberName;
        copy.memberRemark = src.memberRemark;
        copy.memberAvatar = src.memberAvatar;
        copy.memberAvatarCacheKey = src.memberAvatarCacheKey;
        copy.role = src.role;
        copy.status = src.status;
        copy.isDeleted = src.isDeleted;
        copy.createdAt = src.createdAt;
        copy.updatedAt = src.updatedAt;
        copy.version = src.version;
        copy.robot = src.robot;
        copy.remark = src.remark;
        copy.memberInviteUID = src.memberInviteUID;
        copy.forbiddenExpirationTime = src.forbiddenExpirationTime;
        copy.extraMap = src.extraMap == null ? null : new HashMap<>(src.extraMap);
        return copy;
    }

    private void invalidate(String channelId, byte channelType, String uid) {
        if (TextUtils.isEmpty(channelId) || TextUtils.isEmpty(uid)) return;
        cacheGeneration.incrementAndGet();
        String key = cacheKey(channelId, channelType, uid);
        memberCache.remove(key);
        Set<String> keys = keysByMemberUid.get(uid);
        if (keys != null) keys.remove(key);
    }

    private void invalidate(WKChannelMember member) {
        if (member == null) return;
        invalidate(member.channelID, member.channelType, member.memberUID);
    }

    private void invalidate(List<WKChannelMember> list) {
        if (WKCommonUtils.isEmpty(list)) return;
        for (WKChannelMember member : list) {
            invalidate(member);
        }
    }

    /**
     * 某个频道整体失效（{@link #deleteWithChannel} 用）。调用频率极低，扫全表可接受。
     */
    private void invalidateChannel(String channelId, byte channelType) {
        if (TextUtils.isEmpty(channelId)) return;
        cacheGeneration.incrementAndGet();
        String prefix = channelId + "|" + (int) channelType + "|";
        for (String key : memberCache.keySet()) {
            if (!key.startsWith(prefix)) continue;
            memberCache.remove(key);
            // 反向索引同步剪掉，否则频道反复删除时它只增不减：上限只看 memberCache.size()，
            // 拦不住这里的孤儿 key。空 Set 留着不删 —— 删它有「删除瞬间另一线程刚 add」的
            // 竞态，代价是漏一次失效（脏名字），比留一个空 Set 的代价大。
            Set<String> keys = keysByMemberUid.get(key.substring(prefix.length()));
            if (keys != null) keys.remove(key);
        }
    }

    /**
     * PERSONAL 频道行变更时由 {@link ChannelDBManager} 回调：该 uid 作为成员出现过的所有
     * 缓存条目全部失效（因为 memberName / remark / avatar 是从 channel 表 join 出来的）。
     */
    public void invalidateMemberUid(String memberUid) {
        if (TextUtils.isEmpty(memberUid)) return;
        cacheGeneration.incrementAndGet();
        Set<String> keys = keysByMemberUid.remove(memberUid);
        if (keys == null) return;
        for (String key : keys) {
            memberCache.remove(key);
        }
    }

    public void clearCache() {
        cacheGeneration.incrementAndGet();
        memberCache.clear();
        keysByMemberUid.clear();
    }

    public synchronized List<WKChannelMember> search(String channelId, byte channelType, String keyword, int page, int size) {
        int queryPage = (page - 1) * size;
        Object[] args = new Object[6];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = "%" + keyword + "%";
        args[3] = "%" + keyword + "%";
        args[4] = "%" + keyword + "%";
        args[5] = "%" + keyword + "%";
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 and (member_name like ? or member_remark like ? or channel_name like ? or channel_remark like ?) order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc limit " + queryPage + "," + size;
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<WKChannelMember> queryWithPage(String channelId, byte channelType, int page, int size) {
        int queryPage = (page - 1) * size;
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc limit " + queryPage + "," + size;
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * 查询某个频道的所有成员
     *
     * @param channelId 频道ID
     * @return List<WKChannelMember>
     */
    public synchronized List<WKChannelMember> query(String channelId, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=0 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<WKChannelMember> queryDeleted(String channelId, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelId;
        args[1] = channelType;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " LEFT JOIN " + channel + " on " + channelMembers + ".member_uid=" + channel + ".channel_id and " + channel + ".channel_type=1 where " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".is_deleted=1 and " + channelMembers + ".status=1 order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized boolean isExist(String channelId, byte channelType, String uid) {
        boolean isExist = false;
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = uid;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " left join " + channel + " on " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 where (" + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.member_uid + "=?)";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {

            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    public List<WKChannelMember> queryWithUIDs(String channelID, byte channelType, List<String> uidList) {
        List<String> args = new ArrayList<>();
        args.add(channelID);
        args.add(String.valueOf(channelType));
        args.addAll(uidList);
        uidList.add(String.valueOf(channelType));
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, "channel_id =? and channel_type=? and member_uid in (" + WKCursor.getPlaceholders(uidList.size()) + ")", args.toArray(new String[0]), null);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * 查询单个频道成员
     *
     * <p>快路径：ConcurrentHashMap 无锁命中直接返回，**不进 synchronized 慢路径**。
     * 这点和 {@code ChannelManager.getChannel} 同构，同时解掉线上实测的
     * {@code Long monitor contention with owner main ... for 677ms} —— 原实现整个方法
     * synchronized，主线程在锁内等 SQLCipher 连接时会把所有后台线程的成员查询一并堵死。
     *
     * @param channelId 频道ID
     * @param uid       用户ID
     */
    public WKChannelMember query(String channelId, byte channelType, String uid) {
        if (TextUtils.isEmpty(channelId) || TextUtils.isEmpty(uid)) return null;
        WKChannelMember cached = memberCache.get(cacheKey(channelId, channelType, uid));
        if (cached != null) {
            if (BuildConfig.DEBUG) debugCountHit();
            return cached == NULL_MEMBER ? null : copyOf(cached);
        }
        if (BuildConfig.DEBUG) debugCountMiss();
        return querySlowPath(channelId, channelType, uid);
    }

    // ---- debug-only 命中率埋点（release 由 BuildConfig.DEBUG 短路，字段也不会被读）----
    private static final String CACHE_TAG = "ANRFix-MemberCache";
    private long debugHit;
    private long debugMiss;
    private long debugLastLogAt;

    private void debugCountHit() {
        debugHit++;
        debugMaybeLog();
    }

    private void debugCountMiss() {
        debugMiss++;
        debugMaybeLog();
    }

    /** 每 5 秒最多打一行，避免 bind 路径上刷屏反过来影响测量。计数存在良性竞争，只用于看量级。 */
    private void debugMaybeLog() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - debugLastLogAt < 5000) return;
        debugLastLogAt = now;
        long total = debugHit + debugMiss;
        android.util.Log.d(CACHE_TAG, "hit=" + debugHit + " miss=" + debugMiss
                + " hitRate=" + (total == 0 ? 0 : debugHit * 100 / total) + "%"
                + " size=" + memberCache.size());
    }

    private synchronized WKChannelMember querySlowPath(String channelId, byte channelType, String uid) {
        String key = cacheKey(channelId, channelType, uid);
        // double-check：进 synchronized 之前可能另一线程已经回填
        WKChannelMember cached = memberCache.get(key);
        if (cached != null) return cached == NULL_MEMBER ? null : copyOf(cached);

        long generation = cacheGeneration.get();
        WKChannelMember wkChannelMember = null;
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = uid;
        String sql = "select " + channelMembers + ".*," + channelCols + " from " + channelMembers + " left join " + channel + " on " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 where (" + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.member_uid + "=?)";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                // DB 不可用属于瞬时态，不进缓存，否则会把「库还没开」固化成负结果
                return null;
            }
            if (cursor.moveToLast()) {
                wkChannelMember = serializableChannelMember(cursor);
            }
        }
        putCache(key, uid, wkChannelMember, generation);
        return wkChannelMember;
    }

    public synchronized void insert(WKChannelMember channelMember) {
        if (TextUtils.isEmpty(channelMember.channelID) || TextUtils.isEmpty(channelMember.memberUID))
            return;
        ContentValues cv = new ContentValues();
        try {
            cv = WKSqlContentValues.getContentValuesWithChannelMember(channelMember);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "insert error");
        }
        WKIMApplication.getInstance().getDbHelper()
                .insert(channelMembers, cv);
        invalidate(channelMember);
    }

    /**
     * 批量插入频道成员
     *
     * @param list List<WKChannelMember>
     */
    public void insertMembers(List<WKChannelMember> list) {
        List<ContentValues> newCVList = new ArrayList<>();
        for (WKChannelMember member : list) {
            ContentValues cv = WKSqlContentValues.getContentValuesWithChannelMember(member);
            newCVList.add(cv);
        }
        WKDBHelper helper = WKIMApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) {
            return;
        }
        try {
            helper.beginTransaction();
            if (WKCommonUtils.isNotEmpty(newCVList)) {
                for (ContentValues cv : newCVList) {
                    helper.insert(channelMembers, cv);
                }
            }
            helper.setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            helper.endTransaction();
        }
        invalidate(list);
    }

    public void insertMembers(List<WKChannelMember> allMemberList, List<WKChannelMember> existList) {
        List<ContentValues> insertCVList = new ArrayList<>();
//        List<ContentValues> updateCVList = new ArrayList<>();
        for (WKChannelMember channelMember : allMemberList) {
//            boolean isAdd = true;
//            for (WKChannelMember cm : existList) {
//                if (channelMember.memberUID.equals(cm.memberUID)) {
//                    isAdd = false;
//                    updateCVList.add(WKSqlContentValues.getContentValuesWithChannelMember(channelMember));
//                    break;
//                }
//            }
//            if (isAdd) {
            insertCVList.add(WKSqlContentValues.getContentValuesWithChannelMember(channelMember));
//            }
        }
        WKDBHelper helper = WKIMApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) {
            return;
        }
        helper.beginTransaction();
        try {
            if (WKCommonUtils.isNotEmpty(insertCVList)) {
                for (ContentValues cv : insertCVList) {
                    helper.insert(channelMembers, cv);
                }
            }
            helper.setTransactionSuccessful();
        } finally {
            helper.endTransaction();
        }
        invalidate(allMemberList);
    }

    public void insertOrUpdate(WKChannelMember channelMember) {
        if (channelMember == null) return;
        if (isExist(channelMember.channelID, channelMember.channelType, channelMember.memberUID)) {
            update(channelMember);
        } else {
            insert(channelMember);
        }
    }

    /**
     * 修改某个频道的某个成员信息
     *
     * @param channelMember 成员
     */
    public synchronized void update(WKChannelMember channelMember) {
        String[] update = new String[3];
        update[0] = channelMember.channelID;
        update[1] = String.valueOf(channelMember.channelType);
        update[2] = channelMember.memberUID;
        ContentValues cv = new ContentValues();
        try {
            cv = WKSqlContentValues.getContentValuesWithChannelMember(channelMember);
        } catch (Exception e) {
            WKLoggerUtils.getInstance().e(TAG, "update error");
        }
        WKIMApplication.getInstance().getDbHelper()
                .update(channelMembers, cv, WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + WKDBColumns.WKChannelMembersColumns.member_uid + "=?", update);
        invalidate(channelMember);
    }

    /**
     * 根据字段修改频道成员
     *
     * @param channelID   频道ID
     * @param channelType 频道类型
     * @param uid         用户ID
     * @param field       字段
     * @param value       值
     */
    public synchronized boolean updateWithField(String channelID, byte channelType, String uid, String field, String value) {
        String[] updateKey = new String[]{field};
        String[] updateValue = new String[]{value};
        String where = WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + WKDBColumns.WKChannelMembersColumns.member_uid + "=?";
        String[] whereValue = new String[3];
        whereValue[0] = channelID;
        whereValue[1] = String.valueOf(channelType);
        whereValue[2] = uid;
        int row = WKIMApplication.getInstance().getDbHelper()
                .update(channelMembers, updateKey, updateValue, where, whereValue);
        if (row > 0) {
            // 必须在下面 query 之前失效，否则会把改前的旧值重新读回来并回填缓存
            invalidate(channelID, channelType, uid);
            WKChannelMember channelMember = query(channelID, channelType, uid);
            if (channelMember != null)
                //刷新频道成员信息
                ChannelMembersManager.getInstance().setRefreshChannelMember(channelMember, true);
        }
        return row > 0;
    }

    public void deleteWithChannel(String channelID, byte channelType) {
        String selection = "channel_id=? and channel_type=?";
        String[] selectionArgs = new String[2];
        selectionArgs[0] = channelID;
        selectionArgs[1] = String.valueOf(channelType);
        WKIMApplication.getInstance().getDbHelper().delete(channelMembers, selection, selectionArgs);
        invalidateChannel(channelID, channelType);
    }

    /**
     * 批量删除频道成员
     *
     * @param list 频道成员
     */
    public synchronized void deleteMembers(List<WKChannelMember> list) {
        WKDBHelper helper = WKIMApplication.getInstance().getDbHelper();
        if (helper == null || helper.isClosed()) {
            return;
        }
        try {
            helper.beginTransaction();
            if (WKCommonUtils.isNotEmpty(list)) {
                for (int i = 0, size = list.size(); i < size; i++) {
                    insertOrUpdate(list.get(i));
                }
                helper.setTransactionSuccessful();
            }
        } catch (Exception ignored) {
        } finally {
            helper.endTransaction();
        }
        ChannelMembersManager.getInstance().setOnRemoveChannelMember(list);
    }

    public long queryMaxVersion(String channelID, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select max(version) version from " + channelMembers + " where channel_id =? and channel_type=? limit 0, 1";
        long version = 0;
        try {
            if (WKIMApplication.getInstance().getDbHelper() != null) {
                Cursor cursor = WKIMApplication
                        .getInstance()
                        .getDbHelper()
                        .rawQuery(sql, args);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        version = WKCursor.readLong(cursor, "version");
                    }
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
        }
        return version;
    }

    @Deprecated
    public synchronized WKChannelMember queryMaxVersionMember(String channelID, byte channelType) {
        WKChannelMember channelMember = null;
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select * from " + channelMembers + " where " + WKDBColumns.WKChannelMembersColumns.channel_id + "=? and " + WKDBColumns.WKChannelMembersColumns.channel_type + "=? order by " + WKDBColumns.WKChannelMembersColumns.version + " desc limit 0,1";
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                channelMember = serializableChannelMember(cursor);
            }
        }
        return channelMember;
    }

    public synchronized List<WKChannelMember> queryRobotMembers(String channelId, byte channelType) {
        String selection = "channel_id=? and channel_type=? and robot=1 and is_deleted=0";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, selection, new String[]{channelId, String.valueOf(channelType)}, null);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public List<WKChannelMember> queryWithRole(String channelId, byte channelType, int role) {
        String selection = "channel_id=? AND channel_type=? AND role=? AND is_deleted=0";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(channelMembers, selection, new String[]{channelId, String.valueOf(channelType), String.valueOf(role)}, null);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized List<WKChannelMember> queryWithStatus(String channelId, byte channelType, int status) {
        Object[] args = new Object[3];
        args[0] = channelId;
        args[1] = channelType;
        args[2] = status;
        String sql = "select " + channelMembers + ".*," + channel + ".channel_name," + channel + ".channel_remark," + channel + ".avatar from " + channelMembers + " left Join " + channel + " where " + channelMembers + ".member_uid = " + channel + ".channel_id AND " + channel + ".channel_type=1 AND " + channelMembers + ".channel_id=? and " + channelMembers + ".channel_type=? and " + channelMembers + ".status=? order by " + channelMembers + ".role=1 desc," + channelMembers + ".role=2 desc," + channelMembers + "." + WKDBColumns.WKChannelMembersColumns.created_at + " asc";
        Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().rawQuery(sql, args);
        List<WKChannelMember> list = new ArrayList<>();
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializableChannelMember(cursor));
        }
        cursor.close();
        return list;
    }

    public synchronized int queryCount(String channelID, byte channelType) {
        Object[] args = new Object[2];
        args[0] = channelID;
        args[1] = channelType;
        String sql = "select count(*) from " + channelMembers
                + " where (" + WKDBColumns.WKChannelMembersColumns.channel_id + "=? and "
                + WKDBColumns.WKChannelMembersColumns.channel_type + "=? and " + WKDBColumns.WKChannelMembersColumns.is_deleted + "=0 and " + WKDBColumns.WKChannelMembersColumns.status + "=1)";
        Cursor cursor = WKIMApplication.getInstance().getDbHelper().rawQuery(sql, args);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    /**
     * 序列化频道成员
     *
     * @param cursor Cursor
     * @return WKChannelMember
     */
    private WKChannelMember serializableChannelMember(Cursor cursor) {
        WKChannelMember channelMember = new WKChannelMember();
        channelMember.id = WKCursor.readLong(cursor, WKDBColumns.WKChannelMembersColumns.id);
        channelMember.status = WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.status);
        channelMember.channelID = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.channel_id);
        channelMember.channelType = (byte) WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.channel_type);
        channelMember.memberUID = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_uid);
        channelMember.memberName = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_name);
        channelMember.memberAvatar = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_avatar);
        channelMember.memberRemark = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_remark);
        channelMember.role = WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.role);
        channelMember.isDeleted = WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.is_deleted);
        channelMember.version = WKCursor.readLong(cursor, WKDBColumns.WKChannelMembersColumns.version);
        channelMember.createdAt = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.created_at);
        channelMember.updatedAt = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.updated_at);
        channelMember.memberInviteUID = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.member_invite_uid);
        channelMember.robot = WKCursor.readInt(cursor, WKDBColumns.WKChannelMembersColumns.robot);
        channelMember.forbiddenExpirationTime = WKCursor.readLong(cursor, WKDBColumns.WKChannelMembersColumns.forbidden_expiration_time);
        String channelName = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.channel_name);
        if (!TextUtils.isEmpty(channelName)) channelMember.memberName = channelName;
        channelMember.remark = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.channel_remark);
        channelMember.memberAvatar = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.avatar);
        String avatarCache = WKCursor.readString(cursor, WKDBColumns.WKChannelColumns.avatar_cache_key);
        if (!TextUtils.isEmpty(avatarCache)) {
            channelMember.memberAvatarCacheKey = avatarCache;
        } else {
            channelMember.memberAvatarCacheKey = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.memberAvatarCacheKey);
        }
        String extra = WKCursor.readString(cursor, WKDBColumns.WKChannelMembersColumns.extra);
        if (!TextUtils.isEmpty(extra)) {
            HashMap<String, Object> hashMap = new HashMap<>();
            try {
                JSONObject jsonObject = new JSONObject(extra);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    hashMap.put(key, jsonObject.opt(key));
                }
            } catch (JSONException e) {
                WKLoggerUtils.getInstance().e(TAG, "serializableChannelMember extra error");
            }
            channelMember.extraMap = hashMap;
        }
        return channelMember;
    }
}
