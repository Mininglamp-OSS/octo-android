/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.uikit.utils;


import com.chat.uikit.contacts.BotStoreUIEntity;
import com.chat.uikit.contacts.FriendUIEntity;
import com.chat.uikit.enity.MailListEntity;
import com.chat.uikit.group.GroupMemberEntity;

import java.util.List;

/**
 * 2019-11-30 15:54
 * 拼音相关
 */
public class PyingUtils {

    private PyingUtils() {
    }

    private static class PyingUtilsBinder {
        private static final PyingUtils pyingUtils = new PyingUtils();
    }

    public static PyingUtils getInstance() {
        return PyingUtilsBinder.pyingUtils;
    }

    public void sortListBasic1(List<GroupMemberEntity> list) {
        transferListBasic1(list);
    }

    /**
     * 进行冒泡排序
     */
    private void transferListBasic1(List<GroupMemberEntity> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                exchangeNameOrderBasic1(j, list);
            }
        }
    }

    /**
     * 交换两个名字的顺序,根据首字母判断
     */
    private void exchangeNameOrderBasic1(int j, List<GroupMemberEntity> list) {
        String namePinYin1 = list.get(j).pying;
        String namePinYin2 = list.get(j + 1).pying;

        int size = Math.min(namePinYin1.length(), namePinYin2.length());
        for (int i = 0; i < size; i++) {
            char jc = namePinYin1.charAt(i);
            char jcNext = namePinYin2.charAt(i);
            if (jc < jcNext) {//A在B之前就不用比较了
                break;
            }
            if (jc > jcNext) {//A在B之后就直接交换,让A在前面B在后面
                GroupMemberEntity nameBean = list.get(j);
                list.set(j, list.get(j + 1));
                list.set(j + 1, nameBean);
                break;
            }
            //如果AB一样就继续比较后面的字母
        }
    }

    public void sortMailList(List<MailListEntity> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                exchangeMailList(j, list);
            }
        }
    }

    private void exchangeMailList(int j, List<MailListEntity> list) {
        String namePinYin1 = list.get(j).pying;
        String namePinYin2 = list.get(j + 1).pying;

        int size = Math.min(namePinYin1.length(), namePinYin2.length());
        for (int i = 0; i < size; i++) {
            char jc = namePinYin1.charAt(i);
            char jcNext = namePinYin2.charAt(i);
            if (jc < jcNext) {//A在B之前就不用比较了
                break;
            }
            if (jc > jcNext) {//A在B之后就直接交换,让A在前面B在后面
                MailListEntity nameBean = list.get(j);
                list.set(j, list.get(j + 1));
                list.set(j + 1, nameBean);
                break;
            }
            //如果AB一样就继续比较后面的字母
        }
    }

    public void sortListGroupMember(List<GroupMemberEntity> list){
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                exchangeNameOrderGroupMember(j, list);
            }
        }
    }
    private void exchangeNameOrderGroupMember(int j, List<GroupMemberEntity> list) {
        String namePinYin1 = list.get(j).pying;
        String namePinYin2 = list.get(j + 1).pying;

        int size = Math.min(namePinYin1.length(), namePinYin2.length());
        for (int i = 0; i < size; i++) {
            char jc = namePinYin1.charAt(i);
            char jcNext = namePinYin2.charAt(i);
            if (jc < jcNext) {//A在B之前就不用比较了
                break;
            }
            if (jc > jcNext) {//A在B之后就直接交换,让A在前面B在后面
                GroupMemberEntity nameBean = list.get(j);
                list.set(j, list.get(j + 1));
                list.set(j + 1, nameBean);
                break;
            }
            //如果AB一样就继续比较后面的字母
        }
    }

    public void sortListBasic(List<FriendUIEntity> list) {
        transferListBasic(list);
    }

    /**
     * 进行冒泡排序
     */
    private void transferListBasic(List<FriendUIEntity> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                exchangeNameOrderBasic(j, list);
            }
        }
    }

    /**
     * 交换两个名字的顺序,根据首字母判断
     */
    private void exchangeNameOrderBasic(int j, List<FriendUIEntity> list) {
        String namePinYin1 = list.get(j).pying;
        String namePinYin2 = list.get(j + 1).pying;

        int size = Math.min(namePinYin1.length(), namePinYin2.length());
        for (int i = 0; i < size; i++) {
            char jc = namePinYin1.charAt(i);
            char jcNext = namePinYin2.charAt(i);
            if (jc < jcNext) {//A在B之前就不用比较了
                break;
            }
            if (jc > jcNext) {//A在B之后就直接交换,让A在前面B在后面
                FriendUIEntity nameBean = list.get(j);
                list.set(j, list.get(j + 1));
                list.set(j + 1, nameBean);
                break;
            }
            //如果AB一样就继续比较后面的字母
        }
    }

    public void sortBotStoreList(List<BotStoreUIEntity> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                String namePinYin1 = list.get(j).pying;
                String namePinYin2 = list.get(j + 1).pying;
                int size = Math.min(namePinYin1.length(), namePinYin2.length());
                for (int k = 0; k < size; k++) {
                    char jc = namePinYin1.charAt(k);
                    char jcNext = namePinYin2.charAt(k);
                    if (jc < jcNext) break;
                    if (jc > jcNext) {
                        BotStoreUIEntity temp = list.get(j);
                        list.set(j, list.get(j + 1));
                        list.set(j + 1, temp);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 判断首字母是否为字母
     */
    public boolean isStartLetter(String str) {
        String temp = str.substring(0, 1);
        return Character.isLetter(temp.charAt(0));
    }

    /**
     * 判断首字母是否数字
     */
    public boolean isStartNum(String str) {
        String temp = str.substring(0, 1);
        return Character.isDigit(temp.charAt(0));
    }

}
