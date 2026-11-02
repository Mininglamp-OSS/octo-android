/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.octoim.app

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.Toast
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.msgcontent.WKFileContent
import com.chat.uikit.R
import com.chat.uikit.message.MsgModel
import com.xinbida.wukongim.WKIM
import com.xinbida.wukongim.entity.WKChannel
import com.xinbida.wukongim.entity.WKChannelType
import com.xinbida.wukongim.entity.WKSendOptions
import com.xinbida.wukongim.msgmodel.WKMessageContent
import java.util.concurrent.atomic.AtomicBoolean

internal class ShareSendRequest(
    context: Context,
    private val contents: List<WKMessageContent>,
    private val session: WKConfig.SessionSnapshot,
    private val serverScope: String
) {
    private val appContext = context.applicationContext
    private val terminal = AtomicBoolean(false)

    fun confirm(channels: List<WKChannel>?, extraText: String) {
        if (!terminal.compareAndSet(false, true)) return
        try {
            if (channels.isNullOrEmpty() || !isSessionCurrent()) return
            val spaceId = MsgModel.getInstance().getCurrentSpaceId()
            for (content in contents) {
                val duplicates = mutableListOf<WKFileContent>()
                try {
                    val outgoing = if (content is WKFileContent && channels.size > 1) {
                        channels.mapIndexed { index, _ ->
                            if (index == 0) content else try {
                                content.duplicateOwnedSource(appContext, session, serverScope)?.also(duplicates::add)
                            } catch (_: Exception) { null }
                        }
                    } else channels.map { content }
                    for ((index, channel) in channels.withIndex()) {
                        if (!isSessionCurrent()) return
                        val message = outgoing[index] ?: continue
                        if (!TextUtils.isEmpty(spaceId) && channel.channelType == WKChannelType.PERSONAL) message.spaceId = spaceId
                        val options = WKSendOptions()
                        options.setting.receipt = channel.receipt
                        WKIM.getInstance().msgManager.sendWithOptions(message, channel, options)
                    }
                } finally {
                    duplicates.forEach { it.cleanupOwnedTemporarySource() }
                    (content as? WKFileContent)?.cleanupOwnedTemporarySource()
                }
            }
            if (isSessionCurrent() && !TextUtils.isEmpty(extraText)) {
                val selectedChannels = ArrayList(channels)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!isSessionCurrent()) return@postDelayed
                    val text = com.xinbida.wukongim.msgmodel.WKTextContent(extraText)
                    for (channel in selectedChannels) {
                        if (!isSessionCurrent()) return@postDelayed
                        val options = WKSendOptions()
                        options.setting.receipt = channel.receipt
                        WKIM.getInstance().msgManager.sendWithOptions(text, channel, options)
                    }
                }, 200)
            }
            val toast = Toast.makeText(appContext, R.string.is_forward, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        } catch (_: RuntimeException) {
        } finally {
            cleanup()
        }
    }

    fun cancel() {
        if (!terminal.compareAndSet(false, true)) return
        cleanup()
    }

    private fun isSessionCurrent(): Boolean {
        val current = WKConfig.getInstance().sessionSnapshot
        return session.uid == current.uid && session.token == current.token && session.version == current.version
                && serverScope == WKApiConfig.baseUrl
    }

    private fun cleanup() {
        contents.forEach { (it as? WKFileContent)?.cleanupOwnedTemporarySource() }
    }
}
