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

package com.chat.base.net;


import android.text.TextUtils;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.ActManagerUtils;
import com.tencent.bugly.crashreport.CrashReport;
import com.xinbida.wukongim.WKIM;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;


/**
 * 2020-07-17 15:21
 * 服务器返回的状态是在http的状态码上
 */
public abstract class BaseObserver<T> implements Observer<T> {

    /**
     * 本进程内最多上报几条被 Rx 吞掉的 {@link Error}。堆已经耗尽时上报本身也要分配内存，
     * 不能因为要报告 OOM 反而把进程推下去；而且同一次事故通常连着抛好几条，报 3 条足够定性。
     */
    private static final int MAX_SWALLOWED_ERROR_REPORTS = 3;
    private static final AtomicInteger swallowedErrorReports = new AtomicInteger(0);

    @Override
    public void onComplete() {
    }

    @Override
    public void onNext(@NotNull T t) {
        //这里直接返回服务器的结果，因为该结果就是你需要的数据。无需在获取data，code，msg啥的了，给后端点个赞
        onSuccess(t);
    }

    @Override
    public void onError(@NotNull Throwable e) {
        reportIfSwallowedError(e);
        ResponseThrowable throwable = ResponseExceptionHandle.getInstance().handleException(e);
        if (throwable != null) {
            String msg = throwable.getMessage();
            if (TextUtils.isEmpty(msg)) msg = "";
            String errJson = throwable.getErrJson();
            if (TextUtils.isEmpty(errJson)) errJson = "";
            if (throwable.getCode() == 401) {
                // 如果 token 已清空（说明已在退出流程中），跳过重复处理
                if (TextUtils.isEmpty(WKConfig.getInstance().getToken())) {
                    return;
                }
                onFail(throwable.getCode(), msg, errJson);
                //关闭UI层数据库
                WKBaseApplication.getInstance().closeDbHelper();
                WKConfig.getInstance().clearInfo();
                WKIM.getInstance().getConnectionManager().disconnect(true);
                ActManagerUtils.getInstance().clearAllActivity();
                EndpointManager.getInstance().invoke("main_show_home_view",0);
            } else {
                onFail(throwable.getCode(), msg, errJson);
            }
        }
    }

    /**
     * 把落到 Rx 错误管道里的 {@link Error}（主要是 {@link OutOfMemoryError}）补一条 Bugly 上报。
     *
     * <p><b>为什么需要</b>：{@link ResponseExceptionHandle#handleException} 的兜底分支
     * 把任何非 {@code HttpException} 一律转成 {@code code=-1} 的普通请求失败，业务层只看到
     * 「这次请求没成功」。OOM 走到这里就彻底消失了——**不崩溃、不上报**，线上表现是
     * 「会话列表突然空了」而 Bugly 里一条记录都没有，等于把排查线索掐断。
     *
     * <p>2026-08-14 真机实测：压舱到只剩 3.84MB 可用堆时，会话同步的 OOM 正是这样被吞掉的
     * （logcat 只有一行 {@code Throwing OutOfMemoryError}，无 FATAL EXCEPTION、无崩溃记录）。
     * 而同一个 OOM 若抛在 {@code Retrofit.parseResponse} 里则会被原样重抛 → 崩溃 → Bugly 收到
     * ——同一类事故，能不能被发现全看它恰好抛在哪一帧，不可接受。
     *
     * <p><b>刻意不改崩溃语义</b>：这里只补一条 {@code postCatchedException}，然后让流程照常往下走。
     * 该崩的路径（Retrofit / OkHttp 对 VirtualMachineError 的重抛）不受影响，行为与 main 一致。
     *
     * <p>release 也执行 —— 这是线上诊断通道，不是开发期日志。
     */
    private static void reportIfSwallowedError(Throwable e) {
        if (!containsError(e)) return;
        if (swallowedErrorReports.incrementAndGet() > MAX_SWALLOWED_ERROR_REPORTS) return;
        try {
            CrashReport.postCatchedException(e);
        } catch (Throwable ignored) {
            // 上报失败不能反过来影响业务的错误处理
        }
    }

    /** 顺着 cause 链和第一层 suppressed 找 {@link Error}；OkHttp 会把原始 Throwable 挂在 suppressed 上。 */
    private static boolean containsError(Throwable e) {
        Throwable t = e;
        for (int depth = 0; t != null && depth < 5; depth++) {
            if (t instanceof Error) return true;
            for (Throwable s : t.getSuppressed()) {
                if (s instanceof Error) return true;
            }
            if (t.getCause() == t) break;
            t = t.getCause();
        }
        return false;
    }

    @Override
    public void onSubscribe(@NotNull Disposable d) {
    }

    protected abstract void onSuccess(T result);

    protected abstract void onFail(int code, String msg, String errJson);
}
