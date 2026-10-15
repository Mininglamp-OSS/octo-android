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

package com.chat.base.net.ud

import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

internal class Task(
    var url: String,
    var hasDownloadSize: Long = 0,
    var inputStream: InputStream? = null,
    var fileOutputStream: FileOutputStream? = null,
    var status: DownloadStatus = DownloadStatus.START,
    var errorMsg: String? = null,
    var call: Call? = null,
    val request: Request,
    val file: File,
    var contentSize: Long = 0
) {
    /**
     * 本任务的回调订阅者。回调归属任务而不是按 URL 存在全局表里，原因有二：
     *
     * 1. 在途守卫命中时（同 URL 同路径的第二次 download()），新调用方的回调在这里
     *    追加；任务完成/失败时逐个通知，第二个点击者才收得到进度与结果——若按 URL
     *    查表，任务只会用自己启动时捕获的那一个回调，后来者永远收不到。
     * 2. 同 URL 不同路径的两个任务并存时，各自通知各自的订阅者，不会把失败回调
     *    打到另一个还在正常下载的任务头上。
     *
     * 这把锁同时保护下面的 terminal 状态：订阅与终态转换必须互斥，见 [trySubscribe]。
     */
    val callbacks =
        java.util.Collections.synchronizedList(ArrayList<Triple<OnDownload?, OnComplete?, OnFail?>>())

    /**
     * 终态结果。非 null 即表示本任务已经发完终态通知、不会再有人读 [callbacks]。
     *
     * 必须保留而不只是置 ERROR 标志：成功路径没有任何可供迟到订阅者查询的结果，
     * 而迟到订阅者需要的恰恰是"那一个终态事件"。读写都在 `synchronized(callbacks)`
     * 内进行（见 [trySubscribe] / [markTerminalAndDrain]）。
     */
    private var terminal: Terminal? = null

    /** 终态：成功（带最终文件）或失败（带消息）。 */
    sealed class Terminal {
        class Success(val file: File) : Terminal()
        class Failure(val msg: String) : Terminal()
    }

    /**
     * 原子订阅：任务仍在途则把回调入队并返回 null；已终态则不入队、直接返回终态结果
     * 供调用方立即回放。
     *
     * 这是 B-2 的修复点。旧实现把"取任务"（putIfAbsent）与"加订阅"（addCallback）
     * 当成两步无协调的操作：B 拿到在途任务 T 后被调度器挂起，期间 T 完成、worker
     * 把 T 摘表并快照+清空 callbacks；B 恢复后往 T 追加回调就再也没人读——B 既收不到
     * 成功也收不到失败，UI 进度条永久停在那里。把表项移除提前、或只把快照与清空
     * 放进同一把锁，都不能解决"B 手里已经持有 T 的引用"这个前提。必须让订阅本身
     * 看得见终态。
     */
    fun trySubscribe(callback: Triple<OnDownload?, OnComplete?, OnFail?>): Terminal? {
        synchronized(callbacks) {
            val t = terminal
            if (t != null) return t
            callbacks.add(callback)
            return null
        }
    }

    /**
     * 置终态并取走全部订阅者（终端路径唯一入口）。返回的快照由调用方 post 通知。
     *
     * 置 terminal 与快照+清空在同一把锁内完成，因此此刻之后的 [trySubscribe] 必然
     * 走回放分支，不会有订阅者落进这个再也不被读取的列表。
     */
    fun markTerminalAndDrain(result: Terminal): List<Triple<OnDownload?, OnComplete?, OnFail?>> {
        synchronized(callbacks) {
            if (terminal == null) terminal = result
            val snapshot = callbacks.toList()
            callbacks.clear()
            return snapshot
        }
    }

    /** 是否已进入终态（仅供日志/断言，不要用它做订阅判定——那必须走 [trySubscribe]）。 */
    fun isTerminal(): Boolean = synchronized(callbacks) { terminal != null }
}


internal enum class DownloadStatus {
    START, DOWNLOADING, ERROR
}


typealias OnDownload = (String, Int) -> Unit
typealias OnFail = (String, String) -> Unit
typealias OnComplete = (String, file: File) -> Unit