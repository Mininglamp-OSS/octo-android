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

package com.chat.base.app

import com.chat.base.base.WKBaseModel
import com.chat.base.entity.AppInfo
import com.chat.base.entity.AuthInfo
import com.chat.base.net.HttpResponseCode
import com.chat.base.net.IRequestResultListener

class WKAppModel private constructor() : WKBaseModel() {

    companion object {
        val instance = SingletonHolder.holder
    }

    private object SingletonHolder {
        val holder = WKAppModel()
    }

    interface IApp {
        fun onResult(code: Int, msg: String?, appInfo: AppInfo?)
    }

    fun getAppInfo(appId: String, iApp: IApp) {
        request(createService(IAppService::class.java).getAPPInfo(appId),
            object : IRequestResultListener<AppInfo> {
                override fun onSuccess(result: AppInfo) {
                    iApp.onResult(HttpResponseCode.success.toInt(), "", result)
                }

                override fun onFail(code: Int, msg: String?) {
                    iApp.onResult(code, msg, null)
                }

            })
    }

    interface IAuth {
        fun onResult(code: Int, msg: String?, authInfo: AuthInfo?)
    }

    fun getAuthCode(appId: String, iAuth: IAuth) {
        request(createService(IAppService::class.java).getAuthCode(appId),
            object : IRequestResultListener<AuthInfo> {
                override fun onSuccess(result: AuthInfo) {
                    iAuth.onResult(HttpResponseCode.success.toInt(), "", result)
                }

                override fun onFail(code: Int, msg: String?) {
                    iAuth.onResult(code, msg, null)
                }

            })
    }
}