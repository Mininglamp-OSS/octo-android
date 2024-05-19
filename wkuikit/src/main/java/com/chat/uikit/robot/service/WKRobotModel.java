package com.chat.uikit.robot.service;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.utils.WKReader;
import com.chat.uikit.robot.entity.WKRobotEntity;
import com.chat.uikit.robot.entity.WKRobotInlineQueryResult;
import com.chat.uikit.robot.entity.WKRobotMenuEntity;
import com.chat.uikit.robot.entity.WKSyncRobotEntity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKRobot;
import com.xinbida.wukongim.entity.WKRobotMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WKRobotModel extends WKBaseModel {
    private WKRobotModel() {
    }

    private static class WKRobotModelBinder {
        final static WKRobotModel model = new WKRobotModel();
    }

    public static WKRobotModel getInstance() {
        return WKRobotModelBinder.model;
    }

    public void syncRobotData(WKChannel channel) {
        new Thread(() -> sync(channel)).start();
    }

    private void sync(WKChannel channel) {

        boolean isSync = false;
        List<WKRobotEntity> list = new ArrayList<>();
        if (channel.robot == 1) {
            isSync = true;
            WKRobotEntity entity = new WKRobotEntity();
            entity.robot_id = channel.channelID;
            WKRobot robot = WKIM.getInstance().getRobotManager().getWithRobotID(channel.channelID);
            if (robot != null) {
                entity.version = robot.version;
            } else {
                entity.version = 0;
            }
            list.add(entity);
        }
        if (channel.channelType == WKChannelType.GROUP) {
            List<WKChannelMember> memberList = WKIM.getInstance().getChannelMembersManager().getRobotMembers(channel.channelID, channel.channelType);
            if (WKReader.isNotEmpty(memberList)) {
                List<String> robotIds = new ArrayList<>();
                for (WKChannelMember member : memberList) {
                    robotIds.add(member.memberUID);
                }
                List<WKRobot> robotList = WKIM.getInstance().getRobotManager().getWithRobotIds(robotIds);
                if (WKReader.isNotEmpty(robotList)) {
                    for (String robotID : robotIds) {
                        long version = 0;
                        for (WKRobot robot : robotList) {
                            if (robotID.equals(robot.robotID)) {
                                version = robot.version;
                                break;
                            }
                        }
                        list.add(new WKRobotEntity(robotID, version));
                    }
                } else {
                    for (String robotID : robotIds) {
                        list.add(new WKRobotEntity(robotID, 0));
                    }
                }
                isSync = true;
            }
        }
        if (isSync && WKReader.isNotEmpty(list)) {
            WKRobotModel.getInstance().syncRobot(1, list);
        }
    }

    public void syncRobot(int syncType, List<WKRobotEntity> list) {
        JSONArray jsonArray = new JSONArray();
        for (WKRobotEntity entity : list) {
            JSONObject jsonObject = new JSONObject();
            if (syncType == 1) {
                jsonObject.put("robot_id", entity.robot_id);
            } else
                jsonObject.put("username", entity.username);
            jsonObject.put("version", entity.version);
            jsonArray.add(jsonObject);
        }
        request(createService(WKRobotService.class).syncRobot(jsonArray), new IRequestResultListener<List<WKSyncRobotEntity>>() {
            @Override
            public void onSuccess(List<WKSyncRobotEntity> result) {
                List<WKRobot> robotList = new ArrayList<>();
                List<WKRobotMenu> menuList = new ArrayList<>();
                if (WKReader.isNotEmpty(result)) {
                    for (WKSyncRobotEntity entity : result) {
                        WKRobot robot = new WKRobot();
                        robot.username = entity.username;
                        robot.placeholder = entity.placeholder;
                        robot.inlineOn = entity.inline_on;
                        robot.robotID = entity.robot_id;
                        robot.status = entity.status;
                        robot.version = entity.version;
                        robot.updatedAT = entity.updated_at;
                        robot.createdAT = entity.created_at;
                        robotList.add(robot);

                        if (WKReader.isNotEmpty(entity.menus)) {
                            for (WKRobotMenuEntity mRobotMenuEntity : entity.menus) {
                                WKRobotMenu menu = new WKRobotMenu();
                                menu.cmd = mRobotMenuEntity.cmd;
                                menu.type = mRobotMenuEntity.type;
                                menu.remark = mRobotMenuEntity.remark;
                                menu.robotID = mRobotMenuEntity.robot_id;
                                menu.createdAT = mRobotMenuEntity.created_at;
                                menu.updatedAT = mRobotMenuEntity.updated_at;
                                menuList.add(menu);
                            }
                        }
                    }
                }
                // 无数据也调用sdk保存是为了让页面刷新出robot menus
                WKIM.getInstance().getRobotManager().saveOrUpdateRobotMenus(menuList);
                WKIM.getInstance().getRobotManager().saveOrUpdateRobots(robotList);
            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    public List<WKRobotMenuEntity> getRobotMenus(String channelID, byte channelType) {
        List<WKRobotMenuEntity> list = new ArrayList<>();
        if (channelType == WKChannelType.PERSONAL) {
            WKRobot robot = WKIM.getInstance().getRobotManager().getWithRobotID(channelID);
            if (robot != null && !TextUtils.isEmpty(robot.robotID) && robot.status == 1) {
                List<WKRobotMenu> menus = WKIM.getInstance().getRobotManager().getRobotMenus(robot.robotID);
                for (WKRobotMenu menu : menus) {
                    WKRobotMenuEntity entity = new WKRobotMenuEntity();
                    entity.robot_id = menu.robotID;
                    entity.cmd = menu.cmd;
                    entity.remark = menu.remark;
                    entity.type = menu.type;
                    list.add(entity);
                }
            }
        } else {
            List<WKChannelMember> memberList = WKIM.getInstance().getChannelMembersManager().getRobotMembers(channelID, channelType);
            if (WKReader.isNotEmpty(memberList)) {
                List<String> robotIds = new ArrayList<>();
                for (WKChannelMember member : memberList) {
                    if (!TextUtils.isEmpty(member.memberUID) && member.robot == 1) {
                        robotIds.add(member.memberUID);
                    }
                }
                if (WKReader.isNotEmpty(robotIds)) {
                    HashMap<String, List<WKRobotMenuEntity>> hashMap = new HashMap<>();
                    List<WKRobot> robotList = WKIM.getInstance().getRobotManager().getWithRobotIds(robotIds);
                    List<WKRobotMenu> menuList = WKIM.getInstance().getRobotManager().getRobotMenus(robotIds);
                    for (WKRobotMenu menu : menuList) {
                        boolean isAddMenu = true;
                        if (WKReader.isNotEmpty(robotList)) {
                            for (WKRobot robot : robotList) {
                                if (menu.robotID.equals(robot.robotID)) {
                                    if (robot.status == 0) {
                                        isAddMenu = false;
                                    }
                                    break;
                                }
                            }
                        }
                        if (!isAddMenu) continue;
                        WKRobotMenuEntity entity = new WKRobotMenuEntity();
                        entity.cmd = menu.cmd;
                        entity.robot_id = menu.robotID;
                        entity.remark = menu.remark;
                        entity.type = menu.type;
                        List<WKRobotMenuEntity> tempList;
                        if (hashMap.containsKey(menu.robotID)) {
                            tempList = hashMap.get(menu.robotID);
                        } else {
                            tempList = new ArrayList<>();
                        }
                        tempList.add(entity);
                        hashMap.put(menu.robotID, tempList);
                    }

                    for (String key : hashMap.keySet()) {
                        list.addAll(hashMap.get(key));
                    }
                }
            }
        }
        return list;
    }

    public void inlineQuery(String offset, String username, String searchContent, String channelID, byte channelType, final InlineQueryListener inlineQueryListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("query", searchContent);
        jsonObject.put("username", username);
        jsonObject.put("channel_id", channelID);
        jsonObject.put("offset", offset);
        jsonObject.put("channel_type", channelType);
        request(createService(WKRobotService.class).inlineQuery(jsonObject), new IRequestResultListener<WKRobotInlineQueryResult>() {
            @Override
            public void onSuccess(WKRobotInlineQueryResult result) {
                inlineQueryListener.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                inlineQueryListener.onResult(code, msg, null);
            }
        });
    }

    public interface InlineQueryListener {
        void onResult(int code, String msg, WKRobotInlineQueryResult result);
    }
}
