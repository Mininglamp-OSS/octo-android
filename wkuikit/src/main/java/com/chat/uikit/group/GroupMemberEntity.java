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
    public int checked;
    public int isCanCheck;
    public String pying;
    public boolean isSetDelete;
    public WKChannelMember member;

    protected GroupMemberEntity(Parcel in) {
        checked = in.readInt();
        isCanCheck = in.readInt();
        pying = in.readString();
        member = in.readParcelable(WKChannelMember.class.getClassLoader());
        isSetDelete = in.readByte() != 0;
    }

    public GroupMemberEntity() {
        isCanCheck = 1;
        member = null;
    }

    public GroupMemberEntity(WKChannelMember member) {
        isCanCheck = 1;
        this.member = member;
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
    }

}
