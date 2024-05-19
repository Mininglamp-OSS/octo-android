package com.chat.base.db;

import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.DBMenu;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 2020-08-13 11:28
 * 数据库管理
 */
public class WKBaseDBManager {
    private WKBaseDBManager() {
    }

    private static class BaseDbManagerBinder {
        final static WKBaseDBManager baseDb = new WKBaseDBManager();
    }

    public static WKBaseDBManager getInstance() {
        return BaseDbManagerBinder.baseDb;
    }

    void onUpgrade(SQLiteDatabase db) {
        String loginUID = WKConfig.getInstance().getUid();
        long maxIndex = WKSharedPreferencesUtil.getInstance().getLong(loginUID + "_wk_db_upgrade_index");
        LinkedHashMap<Long, List<String>> map = getExecSQL();
        long tempIndex = maxIndex;
        for (Long key : map.keySet()) {
            List<String> list = map.get(key);
            if (key > maxIndex && WKReader.isNotEmpty(list)) {
                for (String sql : list) {
                    if (!TextUtils.isEmpty(sql)) {
                        db.execSQL(sql);
                    }
                }
                if (key > tempIndex)
                    tempIndex = key;
            }
        }
        WKSharedPreferencesUtil.getInstance().putLong(loginUID + "_wk_db_upgrade_index", tempIndex);
    }

    private LinkedHashMap<Long, List<String>> getExecSQL() {
        LinkedHashMap<Long, List<String>> sqlList = new LinkedHashMap<>();

        AssetManager assetManager = WKBaseApplication.getInstance().getContext().getAssets();
        if (assetManager != null) {
            try {
                List<DBMenu> DBMenuList = EndpointManager.getInstance().invokes(EndpointCategory.wkDBMenus, null);
                if (DBMenuList == null) DBMenuList = new ArrayList<>();
                DBMenuList.add(new DBMenu("wkbase_sql"));
                for (DBMenu dbMenu : DBMenuList) {
                    String[] strings = assetManager.list(dbMenu.sqlDirectory);
                    if (strings == null || strings.length == 0) {
                        Log.e("读取UIsql失败：", "--->");
                    }
                    assert strings != null;
                    for (String str : strings) {
                        StringBuilder stringBuilder = new StringBuilder();
                        BufferedReader bf;
                        if (!TextUtils.isEmpty(dbMenu.sqlDirectory)) {
                            bf = new BufferedReader(new InputStreamReader(assetManager.open(dbMenu.sqlDirectory + "/" + str)));
                        } else {
                            bf = new BufferedReader(new InputStreamReader(assetManager.open(str)));
                        }
                        String line;
                        while ((line = bf.readLine()) != null) {
                            stringBuilder.append(line);
                        }
                        String temp = str.replaceAll(".sql", "");
                        List<String> list = new ArrayList<>();
                        if (stringBuilder.toString().contains(";")) {
                            list = Arrays.asList(stringBuilder.toString().split(";"));
                        } else list.add(stringBuilder.toString());
                        sqlList.put(Long.valueOf(temp), list);
                    }

                }
            } catch (IOException e) {
                WKLogUtils.e("读取sql错误");
            }
        }
        return sqlList;
    }
}
