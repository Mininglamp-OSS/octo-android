package com.xinbida.wukongim.db;

import java.util.List;

/**
 * 2020-09-08 09:58
 * 升级管理
 */
public class WKDBSql {
    public long index;
    public List<String> sqlList;

    public WKDBSql(long index, List<String> sqlList) {
        this.index = index;
        this.sqlList = sqlList;
    }
}
