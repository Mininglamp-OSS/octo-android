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
        long tempIndex = maxIndex;
        List<WKDBSql> list = getExecSQL();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).index > maxIndex && list.get(i).sqlList != null && !list.get(i).sqlList.isEmpty()) {
                for (String sql : list.get(i).sqlList) {
                    if (!TextUtils.isEmpty(sql)) {
                        db.execSQL(sql);
                    }
                }
                if (list.get(i).index > tempIndex) {
                    tempIndex = list.get(i).index;
                }
            }
        }
        WKIMApplication.getInstance().setDBUpgradeIndex(tempIndex);
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
