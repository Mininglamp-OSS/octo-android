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

package com.chat.base.config;


/**
 * 1/29/21 11:53 AM
 * 系统账号
 */
public class WKSystemAccount {
    public final static String system_team = "u_10000";
    public final static String system_file_helper = "fileHelper";
    public final static String system_team_short_no = "10000";
    public final static String system_file_helper_short_no = "20000";

    public final static String accountCategorySystem = "system";
    public final static String accountCategoryVisitor = "visitor";
    public final static String accountCategoryCustomerService = "customerService";
    public final static String channelCategoryOrganization = "organization";
    public final static String channelCategoryDepartment = "department";
    public static boolean isSystemAccount(String channelID) {
        return channelID.equals(system_team) || channelID.equals(system_file_helper);
    }
}
