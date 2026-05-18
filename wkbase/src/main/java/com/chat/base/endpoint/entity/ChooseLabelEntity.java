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

package com.chat.base.endpoint.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.xinbida.wukongim.entity.WKChannel;

import java.util.List;

/**
 * 2020-11-19 12:42
 * 选择标签返回信息
 */
public class ChooseLabelEntity implements Parcelable {
    public boolean isSelected;
    public String labelName;
    public String labelId;
    public List<WKChannel> members;

    public ChooseLabelEntity() {
    }

    protected ChooseLabelEntity(Parcel in) {
        isSelected = in.readByte() != 0;
        labelName = in.readString();
        labelId = in.readString();
        members = in.createTypedArrayList(WKChannel.CREATOR);
    }

    public static final Creator<ChooseLabelEntity> CREATOR = new Creator<ChooseLabelEntity>() {
        @Override
        public ChooseLabelEntity createFromParcel(Parcel in) {
            return new ChooseLabelEntity(in);
        }

        @Override
        public ChooseLabelEntity[] newArray(int size) {
            return new ChooseLabelEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeByte((byte) (isSelected ? 1 : 0));
        parcel.writeString(labelName);
        parcel.writeString(labelId);
        parcel.writeTypedList(members);
    }
}
