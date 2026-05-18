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

package com.chat.base.endpoint;

/**
 * 2020-09-01 18:08
 */
public class Endpoint implements Comparable<Endpoint> {
    public int sort;
    public String sid;
    public String category;
    public EndpointHandler iHandler;

    public Endpoint(String sid, String category, int sort, EndpointHandler iHandler) {
        this.sid = sid;
        this.category = category;
        this.sort = sort;
        this.iHandler = iHandler;
    }

    @Override
    public int compareTo(Endpoint Endpoint) {
        return Endpoint.sort - this.sort;
    }
}
