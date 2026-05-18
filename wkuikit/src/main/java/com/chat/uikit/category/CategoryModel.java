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

package com.chat.uikit.category;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;

import com.alibaba.fastjson.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class CategoryModel extends WKBaseModel {

    private List<CategoryEntity> cachedCategories = null;
    private String cachedSpaceId = null;

    private CategoryModel() {
    }

    private static class Binder {
        static final CategoryModel INSTANCE = new CategoryModel();
    }

    public static CategoryModel getInstance() {
        return Binder.INSTANCE;
    }

    public interface ICategoryListListener {
        void onResult(List<CategoryEntity> list);

        void onError(int code, String msg);
    }

    public interface ICategoryListener {
        void onResult(CategoryEntity category);

        void onError(int code, String msg);
    }

    public void list(String spaceId, ICategoryListListener listener) {
        if (cachedCategories != null && spaceId.equals(cachedSpaceId)) {
            listener.onResult(new ArrayList<>(cachedCategories));
        }
        request(createService(CategoryService.class).list(spaceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(List<CategoryEntity> result) {
                cachedSpaceId = spaceId;
                cachedCategories = result;
                listener.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (cachedCategories == null || !spaceId.equals(cachedSpaceId)) {
                    listener.onError(code, msg);
                }
            }
        });
    }

    public void create(String spaceId, String name, ICategoryListener listener) {
        JSONObject json = new JSONObject();
        json.put("name", name);
        request(createService(CategoryService.class).create(spaceId, json), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CategoryEntity result) {
                if (cachedCategories != null && spaceId.equals(cachedSpaceId)) {
                    cachedCategories.add(result);
                }
                listener.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onError(code, msg);
            }
        });
    }

    public void moveGroup(String groupNo, String categoryId, ICommonListener listener) {
        JSONObject json = new JSONObject();
        json.put("category_id", categoryId != null ? categoryId : "");
        request(createService(CategoryService.class).moveGroup(groupNo, json), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                invalidateCache();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void rename(String spaceId, String categoryId, String name, ICommonListener listener) {
        JSONObject json = new JSONObject();
        json.put("name", name);
        request(createService(CategoryService.class).update(spaceId, categoryId, json), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                invalidateCache();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void delete(String spaceId, String categoryId, ICommonListener listener) {
        request(createService(CategoryService.class).delete(spaceId, categoryId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                invalidateCache();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void sort(String spaceId, List<String> categoryIds, ICommonListener listener) {
        JSONObject json = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.addAll(categoryIds);
        json.put("category_ids", arr);
        request(createService(CategoryService.class).sort(spaceId, json), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                invalidateCache();
                listener.onResult(200, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                listener.onResult(code, msg);
            }
        });
    }

    public void invalidateCache() {
        cachedCategories = null;
        cachedSpaceId = null;
    }
}
