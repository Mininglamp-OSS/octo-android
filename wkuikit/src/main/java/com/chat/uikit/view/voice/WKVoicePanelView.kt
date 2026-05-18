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

package com.chat.uikit.view.voice

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.chat.base.ui.Theme
import com.chat.uikit.R

class WKVoicePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var voiceInputEnabled = false
        set(value) {
            field = value
            rebuildTabs()
        }

    var talkBackView: TalkBackView? = null
        private set
    var speechToTextView: SpeechToTextView? = null
        private set
    var voiceInputView: VoiceInputView? = null
        private set

    private val viewPager: ViewPager2
    private val tabContainer: LinearLayout
    private val dotIndicator: View
    private val tabLabels = mutableListOf<TextView>()
    private val pages = mutableListOf<View>()
    private val tabTitles = mutableListOf<String>()

    private val normalColor = Color.parseColor("#999999")
    private val selectedColor: Int
        get() = Theme.colorAccount

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_voice_panel, this, true)
        viewPager = view.findViewById(R.id.viewPager)
        tabContainer = view.findViewById(R.id.tabContainer)
        dotIndicator = view.findViewById(R.id.dotIndicator)

        // Set dot indicator shape
        dotIndicator.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(selectedColor)
        }
    }

    fun setup() {
        buildPages()
        buildTabBar()
        setupViewPager()
    }

    private fun buildPages() {
        pages.clear()
        tabTitles.clear()

        if (voiceInputEnabled) {
            val voiceInput = VoiceInputView(context)
            voiceInputView = voiceInput
            pages.add(voiceInput)
            tabTitles.add(context.getString(R.string.voice_input))
        }

        val sttView = SpeechToTextView(context)
        speechToTextView = sttView
        pages.add(sttView)
        tabTitles.add(context.getString(R.string.speech_to_text))

        val talkBack = TalkBackView(context)
        talkBackView = talkBack
        pages.add(talkBack)
        tabTitles.add(context.getString(R.string.talk_back))
    }

    private fun buildTabBar() {
        tabContainer.removeAllViews()
        tabLabels.clear()

        for ((index, title) in tabTitles.withIndex()) {
            val label = TextView(context).apply {
                text = title
                textSize = 14f
                setTextColor(if (index == 0) selectedColor else normalColor)
                setPadding(dp(5), 0, dp(5), 0)
                setOnClickListener { viewPager.currentItem = index }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                if (index > 0) marginStart = dp(10)
            }
            tabContainer.addView(label, lp)
            tabLabels.add(label)
        }
    }

    private fun setupViewPager() {
        // Keep all pages alive to prevent recycling when swiping far
        viewPager.offscreenPageLimit = pages.size

        viewPager.adapter = object : RecyclerView.Adapter<PagerViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder {
                val container = FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                return PagerViewHolder(container)
            }

            override fun getItemCount(): Int = pages.size

            override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
                val container = holder.itemView as FrameLayout
                container.removeAllViews()
                val page = pages[position]
                // Remove from parent if already attached
                (page.parent as? ViewGroup)?.removeView(page)
                container.addView(
                    page,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateSelectedTab(position)
                cancelAllRecording(position)
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                updateDotPosition(position, positionOffset)
            }
        })

        // Set initial dot position
        post { updateDotPosition(0, 0f) }
    }

    private fun updateSelectedTab(position: Int) {
        for ((index, label) in tabLabels.withIndex()) {
            label.setTextColor(if (index == position) selectedColor else normalColor)
        }
    }

    private fun updateDotPosition(position: Int, offset: Float) {
        if (tabLabels.isEmpty()) return
        val currentTab = tabLabels.getOrNull(position) ?: return
        val nextTab = tabLabels.getOrNull(position + 1) ?: currentTab

        // Tab label center X relative to the panel
        val currentCenterX = tabContainer.left + currentTab.left + currentTab.width / 2f
        val nextCenterX = tabContainer.left + nextTab.left + nextTab.width / 2f
        val dotCenterX = currentCenterX + (nextCenterX - currentCenterX) * offset

        // Only move the dot indicator, tab bar stays fixed
        dotIndicator.translationX = dotCenterX - dotIndicator.width / 2f
    }

    private fun cancelAllRecording(exceptPage: Int) {
        val voiceInputIndex = if (voiceInputEnabled) 0 else -1
        val sttIndex = if (voiceInputEnabled) 1 else 0
        val talkBackIndex = if (voiceInputEnabled) 2 else 1

        if (exceptPage != voiceInputIndex) {
            voiceInputView?.cancelIfRecording()
        }
        if (exceptPage != sttIndex) {
            speechToTextView?.cancelRecording()
        }
        if (exceptPage != talkBackIndex) {
            talkBackView?.cancelRecording()
        }
    }

    fun cancelAllRecording() {
        voiceInputView?.cancelIfRecording()
        speechToTextView?.cancelRecording()
        talkBackView?.cancelRecording()
    }

    private fun rebuildTabs() {
        buildPages()
        buildTabBar()
        viewPager.adapter?.notifyDataSetChanged()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class PagerViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
