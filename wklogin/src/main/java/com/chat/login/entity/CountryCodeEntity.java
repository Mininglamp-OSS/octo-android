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

    public static final Creator<CountryCodeEntity> CREATOR = new Creator<CountryCodeEntity>() {
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
