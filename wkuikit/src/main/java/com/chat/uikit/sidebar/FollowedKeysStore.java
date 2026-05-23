package com.chat.uikit.sidebar;

import android.text.TextUtils;
import android.util.Log;

import com.chat.base.config.WKConstants;
import com.chat.uikit.BuildConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class FollowedKeysStore {

    public interface IFollowedKeysChangeListener {
        void onFollowedKeysChanged();
    }

    private volatile boolean loaded = false;
    private volatile int followVersion = 0;
    private volatile int reloadGeneration = 0;
    private volatile Set<String> followedKeys = Collections.emptySet();
    private volatile Map<String, List<SidebarItemEntity>> itemsByCategory = Collections.emptyMap();
    private volatile Set<String> followedGroupNos = Collections.emptySet();

    private final CopyOnWriteArrayList<IFollowedKeysChangeListener> listeners = new CopyOnWriteArrayList<>();

    private FollowedKeysStore() {
    }

    private static class Binder {
        static final FollowedKeysStore INSTANCE = new FollowedKeysStore();
    }

    public static FollowedKeysStore getInstance() {
        return Binder.INSTANCE;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int getFollowVersion() {
        return followVersion;
    }

    public Set<String> getFollowedKeys() {
        return followedKeys;
    }

    public Map<String, List<SidebarItemEntity>> getItemsByCategory() {
        return itemsByCategory;
    }

    public Set<String> getFollowedGroupNos() {
        return followedGroupNos;
    }

    public boolean isFollowed(int targetType, String targetId) {
        if (TextUtils.isEmpty(targetId)) return false;
        String key = targetType + "::" + targetId;
        return followedKeys.contains(key);
    }

    public boolean hasFollowedThreadsForGroup(String groupChannelId) {
        if (TextUtils.isEmpty(groupChannelId)) return false;
        for (Map.Entry<String, List<SidebarItemEntity>> entry : itemsByCategory.entrySet()) {
            for (SidebarItemEntity item : entry.getValue()) {
                if (item.target_type == SidebarItemEntity.TARGET_TYPE_THREAD
                        && groupChannelId.equals(item.parent_channel_id)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void reload() {
        String deviceUUID = WKConstants.getDeviceUUID();
        final int gen = ++reloadGeneration;
        if (BuildConfig.DEBUG) Log.d("FollowedKeysStore", "reload() called, gen=" + gen);
        SidebarModel.getInstance().sync("follow", 0, "", deviceUUID,
                new SidebarModel.ISidebarSyncListener() {
                    @Override
                    public void onResult(SidebarSyncResponse response) {
                        if (gen != reloadGeneration) return;
                        if (BuildConfig.DEBUG) Log.d("FollowedKeysStore", "reload SUCCESS: items=" + response.items.size()
                                + " followVersion=" + response.follow_version);
                        applyItems(response.items, response.follow_version);
                    }

                    @Override
                    public void onError(int code, String msg) {
                        if (gen != reloadGeneration) return;
                        if (BuildConfig.DEBUG) Log.e("FollowedKeysStore", "reload FAILED: code=" + code + " msg=" + msg);
                        notifyListeners();
                    }
                });
    }

    public void bumpVersion() {
        this.followVersion = this.followVersion + 1;
    }

    public void applyItems(List<SidebarItemEntity> items, int version) {
        Set<String> keys = new HashSet<>(items.size());
        Set<String> groupNos = new HashSet<>();
        Map<String, List<SidebarItemEntity>> buckets = new HashMap<>();

        for (SidebarItemEntity item : items) {
            if (TextUtils.isEmpty(item.target_id)) continue;
            if (!item.is_followed) continue;
            keys.add(item.followKey());
            if (item.target_type == SidebarItemEntity.TARGET_TYPE_CHANNEL) {
                groupNos.add(item.target_id);
            }
            String bucketKey = item.category_id != null ? item.category_id : "";
            List<SidebarItemEntity> bucket = buckets.get(bucketKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                buckets.put(bucketKey, bucket);
            }
            bucket.add(item);
        }

        Map<String, List<SidebarItemEntity>> sortedBuckets = new HashMap<>(buckets.size());
        for (Map.Entry<String, List<SidebarItemEntity>> entry : buckets.entrySet()) {
            List<SidebarItemEntity> list = entry.getValue();
            Collections.sort(list, (a, b) -> {
                int cmp = Long.compare(a.follow_sort, b.follow_sort);
                if (cmp != 0) return cmp;
                return Long.compare(b.timestamp, a.timestamp);
            });
            sortedBuckets.put(entry.getKey(), Collections.unmodifiableList(list));
        }

        this.followedKeys = Collections.unmodifiableSet(keys);
        this.followedGroupNos = Collections.unmodifiableSet(groupNos);
        this.itemsByCategory = Collections.unmodifiableMap(sortedBuckets);
        this.followVersion = version;
        this.loaded = true;

        notifyListeners();
    }

    public void addListener(IFollowedKeysChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(IFollowedKeysChangeListener listener) {
        listeners.remove(listener);
    }

    public void reset() {
        loaded = false;
        followVersion = 0;
        reloadGeneration++;
        followedKeys = Collections.emptySet();
        itemsByCategory = Collections.emptyMap();
        followedGroupNos = Collections.emptySet();
    }

    private void notifyListeners() {
        for (IFollowedKeysChangeListener listener : listeners) {
            listener.onFollowedKeysChanged();
        }
    }
}
