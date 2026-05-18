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

package com.chat.login.entity;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 2020-08-03 21:37
 * 国家码
 */
public class CountryCodeEntity implements Parcelable {
    public String code;
    public String icon;
    public String name;
    public String pying;

    public CountryCodeEntity() {
    }

    protected CountryCodeEntity(Parcel in) {
        code = in.readString();
        name = in.readString();
        pying = in.readString();
        icon = in.readString();
    }

    public static final Creator<CountryCodeEntity> CREATOR = new Creator<>() {
        @Override
        public CountryCodeEntity createFromParcel(Parcel in) {
            return new CountryCodeEntity(in);
        }

        @Override
        public CountryCodeEntity[] newArray(int size) {
            return new CountryCodeEntity[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(code);
        parcel.writeString(name);
        parcel.writeString(pying);
        parcel.writeString(icon);
    }
}
