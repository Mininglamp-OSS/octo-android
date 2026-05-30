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

package com.chat.base.msgcontent;

import android.os.Parcel;
import android.os.Parcelable;

import com.chat.base.WKBaseApplication;
import com.chat.base.R;
import com.xinbida.wukongim.msgmodel.WKMediaMessageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

public class WKFileContent extends WKMediaMessageContent implements Parcelable {
    public String name;
    public String extension;
    public long size;

    public WKFileContent() {
        type = 8;
    }

    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("extension", extension);
            jsonObject.put("size", size);
            jsonObject.put("url", url);
            jsonObject.put("localPath", localPath);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        this.name = jsonObject.optString("name");
        this.extension = jsonObject.optString("extension");
        this.size = jsonObject.optLong("size");
        this.url = jsonObject.optString("url");
        this.localPath = jsonObject.optString("localPath");
        return this;
    }

    protected WKFileContent(Parcel in) {
        super(in);
        name = in.readString();
        extension = in.readString();
        size = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(name);
        dest.writeString(extension);
        dest.writeLong(size);
    }

    public static final Creator<WKFileContent> CREATOR = new Creator<WKFileContent>() {
        @Override
        public WKFileContent createFromParcel(Parcel in) {
            return new WKFileContent(in);
        }

        @Override
        public WKFileContent[] newArray(int size) {
            return new WKFileContent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String getSearchableWord() {
        String prefix = WKBaseApplication.getInstance().getContext().getString(R.string.last_message_file);
        return name != null ? prefix + name : prefix;
    }

    @Override
    public String getDisplayContent() {
        return WKBaseApplication.getInstance().getContext().getString(R.string.last_message_file);
    }
}
