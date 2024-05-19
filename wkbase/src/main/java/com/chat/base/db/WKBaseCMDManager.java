package com.chat.base.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.chat.base.WKBaseApplication;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.WKReader;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMD;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 2020-11-23 11:48
 * cmd管理
 */
public class WKBaseCMDManager {
    private WKBaseCMDManager() {
    }

    private static class BaseCMDManagerBinder {
        final static WKBaseCMDManager cmdManager = new WKBaseCMDManager();
    }

    public static WKBaseCMDManager getInstance() {
        return BaseCMDManagerBinder.cmdManager;
    }

    //添加
    public void addCmd(List<WKBaseCMD> list) {
        if (WKReader.isEmpty(list)) return;
        try {
            List<WKBaseCMD> tempList = new ArrayList<>();
            List<ContentValues> cvList = new ArrayList<>();
            List<String> clientMsgNos = new ArrayList<>();
            List<String> msgIds = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                clientMsgNos.add(list.get(i).client_msg_no);
                msgIds.add(list.get(i).message_id);
                // if (!isExistWithClientMsgNo(list.get(i).client_msg_no) && !isExistWithMessageID(list.get(i).message_id))
//                cvList.add(getContentValues(list.get(i)));
            }
            tempList.addAll(queryWithClientMsgNos(clientMsgNos));
            tempList.addAll(queryWithMsgIds(msgIds));
            boolean isCheck = WKReader.isNotEmpty(tempList);

            for (int i = 0; i < list.size(); i++) {
                boolean isAdd = true;
                if (isCheck) {
                    for (WKBaseCMD cmd : tempList) {
                        if (cmd.client_msg_no.equals(list.get(i).client_msg_no) || cmd.message_id.equals(list.get(i).message_id)) {
                            isAdd = false;
                            break;
                        }
                    }
                }
                if (isAdd) {
                    cvList.add(getContentValues(list.get(i)));
                }
            }
            WKBaseApplication.getInstance().getDbHelper().getDB()
                    .beginTransaction();
            for (ContentValues cv : cvList) {
                WKBaseApplication.getInstance().getDbHelper()
                        .insert("cmd", cv);
            }
            WKBaseApplication.getInstance().getDbHelper().getDB()
                    .setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            if (WKBaseApplication.getInstance().getDbHelper().getDB().inTransaction()) {
                WKBaseApplication.getInstance().getDbHelper().getDB()
                        .endTransaction();
            }
        }
    }

    private List<WKBaseCMD> queryWithClientMsgNos(List<String> clientMsgNos) {
        List<WKBaseCMD> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("select * from cmd where client_msg_no in (");
        for (int i = 0, size = clientMsgNos.size(); i < size; i++) {
            if (i != 0) sb.append(",");
            sb.append("'").append(clientMsgNos.get(i)).append("'");
        }
        sb.append(")");
        try (Cursor cursor = WKBaseApplication.getInstance().getDbHelper().rawQuery(sb.toString())) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKBaseCMD cmd = serializeCmd(cursor);
                list.add(cmd);
            }
        }
        return list;
    }

    private List<WKBaseCMD> queryWithMsgIds(List<String> clientMsgNos) {
        List<WKBaseCMD> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("select * from cmd where message_id in (");
        for (int i = 0, size = clientMsgNos.size(); i < size; i++) {
            if (i != 0) sb.append(",");
            sb.append("'").append(clientMsgNos.get(i)).append("'");
        }
        sb.append(")");
        try (Cursor cursor = WKBaseApplication.getInstance().getDbHelper().rawQuery(sb.toString())) {
            if (cursor == null) {
                return list;
            }
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                WKBaseCMD cmd = serializeCmd(cursor);
                list.add(cmd);
            }
        }
        return list;
    }

    //是否存在某条cmd
    private boolean isExistWithClientMsgNo(String clientMsgNo) {
        String sql = "select * from cmd where client_msg_no = " + "\"" + clientMsgNo + "\"";
        Cursor cursor = WKBaseApplication.getInstance().getDbHelper().rawQuery(sql, null);
        boolean isExist;
        if (cursor == null) {
            isExist = false;
        } else {
            isExist = cursor.moveToLast();
            cursor.close();
        }
        return isExist;
    }

    //是否存在某条cmd
    private boolean isExistWithMessageID(String messageID) {
        String sql = "select * from cmd where message_id = " + "\"" + messageID + "\"";
        Cursor cursor = WKBaseApplication.getInstance().getDbHelper().rawQuery(sql, null);
        boolean isExist;
        if (cursor == null) {
            isExist = false;
        } else {
            isExist = cursor.moveToLast();
            cursor.close();
        }
        return isExist;
    }


    //删除某条cmd
    public void deleteCmd(String client_msg_no) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_deleted", 1);
        String[] update = new String[1];
        update[0] = client_msg_no;
        WKBaseApplication.getInstance().getDbHelper().update("cmd", contentValues, "client_msg_no=?", update);
    }

    //查询所有cmd
    private List<WKBaseCMD> queryAllCmd() {
        List<WKBaseCMD> list = new ArrayList<>();
        String sql = "select * from cmd where is_deleted=0";
        Cursor cursor = WKBaseApplication.getInstance().getDbHelper().rawQuery(sql, null);
        if (cursor == null) {
            return list;
        }
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            list.add(serializeCmd(cursor));
        }
        cursor.close();
        return list;
    }

    private ContentValues getContentValues(WKBaseCMD WKBaseCmd) {
        ContentValues contentValues = new ContentValues();
        if (WKBaseCmd == null) {
            return contentValues;
        }
        contentValues.put("client_msg_no", WKBaseCmd.client_msg_no);
        contentValues.put("cmd", WKBaseCmd.cmd);
        contentValues.put("sign", WKBaseCmd.sign);
        contentValues.put("created_at", WKBaseCmd.created_at);
        contentValues.put("message_id", WKBaseCmd.message_id);
        contentValues.put("message_seq", WKBaseCmd.message_seq);
        contentValues.put("param", WKBaseCmd.param);
        contentValues.put("timestamp", WKBaseCmd.timestamp);
        return contentValues;
    }

    @SuppressLint("Range")
    private WKBaseCMD serializeCmd(Cursor cursor) {
        WKBaseCMD WKBaseCmd = new WKBaseCMD();
        WKBaseCmd.client_msg_no = WKCursor.readString(cursor, "client_msg_no");
        WKBaseCmd.cmd = WKCursor.readString(cursor, "cmd");
        WKBaseCmd.created_at = WKCursor.readString(cursor, "created_at");
        WKBaseCmd.message_id = WKCursor.readString(cursor, "message_id");
        WKBaseCmd.message_seq = WKCursor.readLong(cursor, "message_seq");
        WKBaseCmd.param = WKCursor.readString(cursor, "param");
        WKBaseCmd.sign = WKCursor.readString(cursor, "sign");
        WKBaseCmd.timestamp = WKCursor.readLong(cursor, "timestamp");
        return WKBaseCmd;
    }

    private void handleRevokeCmd(List<WKBaseCMD> list) {
        final Timer[] timer = {new Timer()};
        final int[] i = {0};
        timer[0].schedule(new TimerTask() {
            @Override
            public void run() {
                if (i[0] == list.size() - 1) {
                    timer[0].cancel();
                    timer[0] = null;
                }
                WKIM.getInstance().getCMDManager().handleCMD(list.get(i[0]).cmd, list.get(i[0]).param, list.get(i[0]).sign);
                i[0]++;
            }
        }, 0, 100);
    }

    //处理cmd
    public void handleCmd() {
        List<WKCMD> rtcList = new ArrayList<>();
        List<WKBaseCMD> cmdList = queryAllCmd();
        if (WKReader.isEmpty(cmdList)) return;
        HashMap<String, List<WKBaseCMD>> revokeMap = new HashMap<>();
        for (WKBaseCMD WKBaseCmd : cmdList) {
            if (WKBaseCmd.is_deleted == 0 && !TextUtils.isEmpty(WKBaseCmd.cmd)) {
                if (WKBaseCmd.cmd.equals(WKCMDKeys.wk_messageRevoke)) {
                    if (!TextUtils.isEmpty(WKBaseCmd.param)) {
                        try {
                            String channelID = "";
                            byte channelType = 0;
                            JSONObject jsonObject = new JSONObject(WKBaseCmd.param);
                            if (jsonObject.has("channel_id")) {
                                channelID = jsonObject.optString("channel_id");
                            }
                            if (jsonObject.has("channel_type")) {
                                channelType = (byte) jsonObject.optInt("channel_type");
                            }
                            if (!TextUtils.isEmpty(channelID)) {
                                List<WKBaseCMD> list;
                                String key = String.format("%s,%s", channelID, channelType);
                                if (revokeMap.containsKey(key)) {
                                    list = revokeMap.get(key);
                                    if (list == null) list = new ArrayList<>();
                                } else {
                                    list = new ArrayList<>();
                                }
                                list.add(WKBaseCmd);
                                revokeMap.put(key, list);
                            }
                        } catch (JSONException e) {
                            WKLogUtils.e("处理cmd错误");
                        }
                    }
                } else if (WKBaseCmd.cmd.startsWith("rtc.p2p")) {
                    try {
                        JSONObject jsonObject = new JSONObject(WKBaseCmd.param);
                        rtcList.add(new WKCMD(WKBaseCmd.cmd, jsonObject));
                    } catch (JSONException e) {
                        WKLogUtils.e("解析cmd错误");
                    }
                } else
                    WKIM.getInstance().getCMDManager().handleCMD(WKBaseCmd.cmd, WKBaseCmd.param, WKBaseCmd.sign);
            }
        }
        if (WKReader.isNotEmpty(rtcList)) {
            EndpointManager.getInstance().invoke("rtc_offline_data", rtcList);
        }
        if (!revokeMap.isEmpty()) {
            List<WKBaseCMD> tempList = new ArrayList<>();
            for (String key : revokeMap.keySet()) {
                String channelID = key.split(",")[0];
                byte channelType = Byte.parseByte(key.split(",")[1]);
                WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
                //是否撤回提醒
                int revokeRemind = 0;
                if (channel != null && channel.localExtra != null && channel.localExtra.containsKey(WKChannelExtras.revokeRemind)) {
                    Object object = channel.localExtra.get(WKChannelExtras.revokeRemind);
                    if (object != null) {
                        revokeRemind = (int) object;
                    }
                }
                if (revokeRemind == 1) {
                    EndpointManager.getInstance().invoke("syncExtraMsg", new WKChannel(channelID, channelType));
                } else {
                    List<WKBaseCMD> list = revokeMap.get(key);
                    if (WKReader.isNotEmpty(list))
                        tempList.addAll(list);
                }
                if (WKReader.isNotEmpty(tempList)) {
                    new Thread(() -> handleRevokeCmd(tempList)).start();
                }
            }
        }
        try {
            WKBaseApplication.getInstance().getDbHelper().getDB()
                    .beginTransaction();
            for (int i = 0; i < cmdList.size(); i++) {
                deleteCmd(cmdList.get(i).client_msg_no);
            }
            WKBaseApplication.getInstance().getDbHelper().getDB()
                    .setTransactionSuccessful();
        } catch (Exception ignored) {
        } finally {
            if (WKBaseApplication.getInstance().getDbHelper() != null && WKBaseApplication.getInstance().getDbHelper().getDB().inTransaction()) {
                WKBaseApplication.getInstance().getDbHelper().getDB()
                        .endTransaction();
            }
        }
    }
}
