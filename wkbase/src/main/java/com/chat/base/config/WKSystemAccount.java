package com.chat.base.config;


/**
 * 1/29/21 11:53 AM
 * 系统账号
 */
public class WKSystemAccount {
    public final static String system_team = "u_10000";
    public final static String system_file_helper = "fileHelper";

    public final static String accountCategorySystem = "system";
    public final static String accountCategoryVisitor = "visitor";
    public final static String accountCategoryCustomerService = "customerService";
    public final static String channelCategoryOrganization = "organization";
    public final static String channelCategoryDepartment = "department";
    public static boolean isSystemAccount(String channelID) {
        return channelID.equals(system_team) || channelID.equals(system_file_helper);
    }
}
