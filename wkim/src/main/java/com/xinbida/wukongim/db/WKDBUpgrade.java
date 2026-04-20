package com.xinbida.wukongim.db;

import android.content.res.AssetManager;
import android.text.TextUtils;

import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.utils.WKLoggerUtils;


import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 2020-07-31 09:36
 * 数据库升级管理
 */
public class WKDBUpgrade {
    private static final String TAG = "WKDBUpgrade";

    private WKDBUpgrade() {
    }

    static class DBUpgradeBinder {
        final static WKDBUpgrade db = new WKDBUpgrade();
    }

    public static WKDBUpgrade getInstance() {
        return DBUpgradeBinder.db;
    }

    void onUpgrade(SQLiteDatabase db) {
        long maxIndex = WKIMApplication.getInstance().getDBUpgradeIndex();
        List<WKDBSql> list = getExecSQL();
        // 按 index 升序排序，确保迁移按时间顺序执行
        Collections.sort(list, new Comparator<WKDBSql>() {
            @Override
            public int compare(WKDBSql a, WKDBSql b) {
                return Long.compare(a.index, b.index);
            }
        });
        for (int i = 0; i < list.size(); i++) {
            WKDBSql dbSql = list.get(i);
            if (dbSql.index > maxIndex && dbSql.sqlList != null && !dbSql.sqlList.isEmpty()) {
                boolean success = true;
                for (String sql : dbSql.sqlList) {
                    if (!TextUtils.isEmpty(sql)) {
                        try {
                            db.execSQL(sql);
                        } catch (Exception e) {
                            WKLoggerUtils.getInstance().e(TAG, "Migration " + dbSql.index + " failed: " + e.getMessage());
                            success = false;
                        }
                    }
                }
                // 无论单条 SQL 是否失败，都更新 index，避免重复执行导致连锁错误（如 ADD COLUMN 重复会报 duplicate column）
                WKIMApplication.getInstance().setDBUpgradeIndex(dbSql.index);
            }
        }
    }

    private List<WKDBSql> getExecSQL() {
        List<WKDBSql> sqlList = new ArrayList<>();

        AssetManager assetManager = WKIMApplication.getInstance().getContext().getAssets();
        if (assetManager != null) {
            try {
                String[] strings = assetManager.list("wk_sql");
                if (strings == null || strings.length == 0) {
                    WKLoggerUtils.getInstance().e(TAG,"Failed to read SQL");
                }
                assert strings != null;
                for (String str : strings) {
                    StringBuilder stringBuilder = new StringBuilder();
                    BufferedReader bf = new BufferedReader(new InputStreamReader(
                            assetManager.open("wk_sql/" + str)));
                    String line;
                    while ((line = bf.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    String temp = str.replaceAll(".sql", "");
                    List<String> list = new ArrayList<>();
                    if (stringBuilder.toString().contains(";")) {
                        list = Arrays.asList(stringBuilder.toString().split(";"));
                    } else list.add(stringBuilder.toString());
                    sqlList.add(new WKDBSql(Long.parseLong(temp), list));
                }
            } catch (IOException e) {
                WKLoggerUtils.getInstance().e(TAG , "getExecSQL error");
            }
        }
        return sqlList;
    }
}
