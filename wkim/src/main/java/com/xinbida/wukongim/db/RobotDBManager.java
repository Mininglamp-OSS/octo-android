package com.xinbida.wukongim.db;

import static com.xinbida.wukongim.db.WKDBColumns.TABLE.robot;
import static com.xinbida.wukongim.db.WKDBColumns.TABLE.robotMenu;

import android.content.ContentValues;
import android.database.Cursor;

import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.entity.WKRobot;
import com.xinbida.wukongim.entity.WKRobotMenu;
import com.xinbida.wukongim.utils.WKCommonUtils;

import java.util.ArrayList;
import java.util.List;

public class RobotDBManager {

    private RobotDBManager() {
    }

    private static class RobotDBManagerBinder {
        private final static RobotDBManager db = new RobotDBManager();
    }

    public static RobotDBManager getInstance() {
        return RobotDBManagerBinder.db;
    }

    public void insertOrUpdateMenus(List<WKRobotMenu> list) {
        for (WKRobotMenu menu : list) {
            if (isExitMenu(menu.robotID, menu.cmd)) {
                update(menu);
            } else {
                WKIMApplication.getInstance().getDbHelper().insert(robotMenu, getCV(menu));
            }
        }
    }

    public boolean isExitMenu(String robotID, String cmd) {
        boolean isExist = false;
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(robotMenu, "robot_id =? and cmd=?", new String[]{robotID, cmd}, null)) {
            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    private void update(WKRobotMenu menu) {
        String[] updateKey = new String[3];
        String[] updateValue = new String[3];
        updateKey[0] = "type";
        updateValue[0] = menu.type;
        updateKey[1] = "remark";
        updateValue[1] = menu.remark;
        updateKey[2] = "updated_at";
        updateValue[2] = menu.updatedAT;
        String where = "robot_id=? and cmd=?";
        String[] whereValue = new String[2];
        whereValue[0] = menu.robotID;
        whereValue[1] = menu.cmd;
        WKIMApplication.getInstance().getDbHelper()
                .update(robotMenu, updateKey, updateValue, where, whereValue);
    }

    public void insertOrUpdateRobots(List<WKRobot> list) {
        for (WKRobot robot : list) {
            if (isExist(robot.robotID)) {
                update(robot);
            } else {
                insert(robot);
            }
        }
    }

    public boolean isExist(String robotID) {
        boolean isExist = false;
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(robot, "robot_id=?", new String[]{robotID}, null)) {
            if (cursor != null && cursor.moveToLast()) {
                isExist = true;
            }
        }
        return isExist;
    }

    private void update(WKRobot wkRobot) {
        String[] updateKey = new String[6];
        String[] updateValue = new String[6];
        updateKey[0] = "status";
        updateValue[0] = String.valueOf(wkRobot.status);
        updateKey[1] = "version";
        updateValue[1] = String.valueOf(wkRobot.version);
        updateKey[2] = "updated_at";
        updateValue[2] = String.valueOf(wkRobot.updatedAT);
        updateKey[3] = "username";
        updateValue[3] = wkRobot.username;
        updateKey[4] = "placeholder";
        updateValue[4] = wkRobot.placeholder;
        updateKey[5] = "inline_on";
        updateValue[5] = String.valueOf(wkRobot.inlineOn);

        String where = "robot_id=?";
        String[] whereValue = new String[1];
        whereValue[0] = wkRobot.robotID;
        WKIMApplication.getInstance().getDbHelper()
                .update(robot, updateKey, updateValue, where, whereValue);

    }

    private void insert(WKRobot robot1) {
        ContentValues cv = getCV(robot1);
        WKIMApplication.getInstance().getDbHelper().insert(robot, cv);
    }

    public void insertRobots(List<WKRobot> list) {
        if (WKCommonUtils.isEmpty(list)) return;
        List<ContentValues> cvList = new ArrayList<>();
        for (WKRobot robot : list) {
            cvList.add(getCV(robot));
        }
        try {
            WKIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            for (ContentValues cv : cvList) {
                WKIMApplication.getInstance().getDbHelper().insert(robot, cv);
            }
            WKIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();

        } finally {
            WKIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
    }

    public WKRobot query(String robotID) {
        WKRobot wkRobot = null;
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(robot, "robot_id =?", new String[]{robotID}, null)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                wkRobot = serializeRobot(cursor);
            }
        }
        return wkRobot;
    }

    public WKRobot queryWithUsername(String username) {
        WKRobot wkRobot = null;
        try (Cursor cursor = WKIMApplication
                .getInstance()
                .getDbHelper().select(robot, "username=?", new String[]{username}, null)) {
            if (cursor == null) {
                return null;
            }
            if (cursor.moveToLast()) {
                wkRobot = serializeRobot(cursor);
            }
        }
        return wkRobot;
    }

    public List<WKRobot> queryRobots(List<String> robotIds) {
        List<WKRobot> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().select(robot, "robot_id in (" + WKCursor.getPlaceholders(robotIds.size()) + ")", robotIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKRobot robot = serializeRobot(cursor);
                list.add(robot);
            }
        }
        return list;
    }

    public List<WKRobotMenu> queryRobotMenus(List<String> robotIds) {
        List<WKRobotMenu> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().select(robotMenu, "robot_id in (" + WKCursor.getPlaceholders(robotIds.size()) + ")", robotIds.toArray(new String[0]), null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKRobotMenu robotMenu = serializeRobotMenu(cursor);
                list.add(robotMenu);
            }
        }
        return list;
    }

    public List<WKRobotMenu> queryRobotMenus(String robotID) {
        List<WKRobotMenu> list = new ArrayList<>();
        try (Cursor cursor = WKIMApplication.getInstance().getDbHelper().select(robotMenu, "robot_id=?", new String[]{robotID}, null)) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKRobotMenu robotMenu = serializeRobotMenu(cursor);
                list.add(robotMenu);
            }
        }
        return list;
    }

    public void insertMenus(List<WKRobotMenu> list) {
        if (WKCommonUtils.isEmpty(list)) return;
        List<ContentValues> cvList = new ArrayList<>();
        for (WKRobotMenu robot : list) {
            cvList.add(getCV(robot));
        }
        try {
            WKIMApplication.getInstance().getDbHelper().getDb().beginTransaction();
            for (ContentValues cv : cvList) {
                WKIMApplication.getInstance().getDbHelper().insert(robotMenu, cv);
            }
            WKIMApplication.getInstance().getDbHelper().getDb().setTransactionSuccessful();

        } finally {
            WKIMApplication.getInstance().getDbHelper().getDb().endTransaction();
        }
    }

    private WKRobot serializeRobot(Cursor cursor) {
        WKRobot robot = new WKRobot();
        robot.robotID = WKCursor.readString(cursor, "robot_id");
        robot.status = WKCursor.readInt(cursor, "status");
        robot.version = WKCursor.readLong(cursor, "version");
        robot.username = WKCursor.readString(cursor, "username");
        robot.inlineOn = WKCursor.readInt(cursor, "inline_on");
        robot.placeholder = WKCursor.readString(cursor, "placeholder");
        robot.createdAT = WKCursor.readString(cursor, "created_at");
        robot.updatedAT = WKCursor.readString(cursor, "updated_at");
        return robot;
    }

    private WKRobotMenu serializeRobotMenu(Cursor cursor) {
        WKRobotMenu robot = new WKRobotMenu();
        robot.robotID = WKCursor.readString(cursor, "robot_id");
        robot.type = WKCursor.readString(cursor, "type");
        robot.cmd = WKCursor.readString(cursor, "cmd");
        robot.remark = WKCursor.readString(cursor, "remark");
        robot.createdAT = WKCursor.readString(cursor, "created_at");
        robot.updatedAT = WKCursor.readString(cursor, "updated_at");
        return robot;
    }

    private ContentValues getCV(WKRobot wkRobot) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("robot_id", wkRobot.robotID);
        contentValues.put("inline_on", wkRobot.inlineOn);
        contentValues.put("username", wkRobot.username);
        contentValues.put("placeholder", wkRobot.placeholder);
        contentValues.put("status", wkRobot.status);
        contentValues.put("version", wkRobot.version);
        contentValues.put("created_at", wkRobot.createdAT);
        contentValues.put("updated_at", wkRobot.updatedAT);
        return contentValues;
    }

    private ContentValues getCV(WKRobotMenu robotMenu) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("robot_id", robotMenu.robotID);
        contentValues.put("cmd", robotMenu.cmd);
        contentValues.put("remark", robotMenu.remark);
        contentValues.put("type", robotMenu.type);
        contentValues.put("created_at", robotMenu.createdAT);
        contentValues.put("updated_at", robotMenu.updatedAT);
        return contentValues;
    }
}
