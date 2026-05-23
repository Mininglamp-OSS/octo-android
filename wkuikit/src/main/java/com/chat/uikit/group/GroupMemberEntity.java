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

package com.chat.uikit.group;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.entity.WKChannelMember;

/**
 * 2020-05-31 16:55
 * 群成员
 */
public class GroupMemberEntity implements Parcelable {
    /**
     * 三态 mention 入口类型。
     * 0 = 普通群成员（member 字段非空）
     * 1 = @所有人（broadcast humans，sentinel uid = "-1"）
     * 2 = @所有AI（broadcast ais，sentinel uid = "-2"）
     */
    public static final int TYPE_MEMBER = 0;
    public static final int TYPE_AT_ALL = 1;
    public static final int TYPE_AT_AIS = 2;

    public int checked;
    public int isCanCheck;
    public String pying;
    public boolean isSetDelete;
    public WKChannelMember member;
    /**
     * 列表项类型：默认 TYPE_MEMBER，@所有人 / @所有AI 用 TYPE_AT_ALL / TYPE_AT_AIS 区分。
     * 沿用 member==null 等价 TYPE_AT_ALL 的旧约定，新增的 @所有AI 必须显式设置 type=TYPE_AT_AIS。
     */
    public int type = TYPE_MEMBER;

    protected GroupMemberEntity(Parcel in) {
        checked = in.readInt();
        isCanCheck = in.readInt();
        pying = in.readString();
        member = in.readParcelable(WKChannelMember.class.getClassLoader());
        isSetDelete = in.readByte() != 0;
        type = in.readInt();
    }

    public GroupMemberEntity() {
        isCanCheck = 1;
        member = null;
        type = TYPE_AT_ALL;
    }

    public GroupMemberEntity(WKChannelMember member) {
        isCanCheck = 1;
        this.member = member;
        this.type = TYPE_MEMBER;
    }

    /**
     * 构造广播条目（@所有人 / @所有AI）。member 字段保持 null，由 type 区分类型。
     */
    public static GroupMemberEntity broadcast(int type) {
        GroupMemberEntity entity = new GroupMemberEntity();
        entity.type = type;
        return entity;
    }

    public static final Creator<GroupMemberEntity> CREATOR = new Creator<GroupMemberEntity>() {
        @Override
        public GroupMemberEntity createFromParcel(Parcel in) {
            return new GroupMemberEntity(in);
        }

        @Override
        public GroupMemberEntity[] newArray(int size) {
            return new GroupMemberEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(checked);
        parcel.writeInt(isCanCheck);
        parcel.writeString(pying);
        parcel.writeParcelable(member, i);
        parcel.writeByte((byte) (isSetDelete ? 1 : 0));
        parcel.writeInt(type);
    }

}
