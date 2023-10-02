package com.chat.uikit.setting

import android.content.Intent
import android.os.Process
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alibaba.fastjson.JSON
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.base.WKBaseActivity
import com.chat.base.common.WKCommonModel
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.entity.AppModule
import com.chat.base.net.HttpResponseCode
import com.chat.base.ui.Theme
import com.chat.base.ui.components.CheckBox
import com.chat.base.utils.AndroidUtilities
import com.chat.uikit.R
import com.chat.uikit.databinding.ActCommonListLayoutBinding

class AppModulesActivity : WKBaseActivity<ActCommonListLayoutBinding>() {
    private lateinit var adapter: ModuleAdapter
    override fun getViewBinding(): ActCommonListLayoutBinding {
        return ActCommonListLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView?) {
        titleTv!!.setText(R.string.choose_module)
    }

    override fun initView() {
        adapter = ModuleAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
    }

    override fun getRightTvText(textView: TextView?): String {
        return getString(R.string.str_save)
    }


    override fun rightLayoutClick() {
        val list = ArrayList<AppModule>()
        for (item in adapter.data) {
            if (item.status == 1) {
                list.add(item)
            }
        }
        val json = JSON.toJSONString(list)
        WKSharedPreferencesUtil.getInstance().putSPWithUID("app_module", json)

        showDialog(getString(R.string.restart_app_desc)) { index ->
            if (index == 1) {
                val intent = packageManager.getLaunchIntentForPackage(application.packageName)
                intent!!.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                startActivity(intent)
                Process.killProcess(Process.myPid())
            }
        }
    }

    override fun initListener() {
        adapter.setOnItemClickListener { adapter, _, position ->
            val module = adapter.data[position] as AppModule
            if (module.status == 1) {
                module.checked = !module.checked
                adapter.notifyItemChanged(position, module)
            }
        }
    }

    override fun initData() {
        WKCommonModel.getInstance().getAppModule { code, msg, list ->
            if (code == HttpResponseCode.success.toInt()) {
                adapter.setList(list)
            } else {
                showToast(msg!!)
            }
        }
    }

    class ModuleAdapter :
        BaseQuickAdapter<AppModule, BaseViewHolder>(R.layout.item_app_module_layout) {
        override fun convert(holder: BaseViewHolder, item: AppModule, payloads: List<Any>) {
            super.convert(holder, item, payloads)
            val entity = payloads[0] as AppModule
            val checkBox = holder.getView<CheckBox>(R.id.checkBox)
            val isChecked = entity.checked
            checkBox.setChecked(isChecked, true)
        }

        override fun convert(holder: BaseViewHolder, item: AppModule) {
            holder.setText(R.id.nameTv, item.name)
            holder.setText(R.id.descTv, item.desc)

            val checkBox: CheckBox = holder.getView(R.id.checkBox)
            checkBox.setResId(context, R.mipmap.round_check2)
            checkBox.setDrawBackground(true)
            checkBox.setHasBorder(true)
            checkBox.setStrokeWidth(AndroidUtilities.dp(2f))
            checkBox.setBorderColor(ContextCompat.getColor(context, R.color.layoutColor))
            checkBox.setSize(24)
            checkBox.setColor(
                if (item.status == 1) Theme.colorAccount else Theme.colorAccountDisable,
                ContextCompat.getColor(context, R.color.white)
            )
            checkBox.isEnabled = item.status == 1
            checkBox.setChecked(item.checked, true)

            if (item.status == 0) {
                holder.setGone(R.id.errorIV, false)
                checkBox.visibility = View.GONE
            } else {
                checkBox.visibility = View.VISIBLE
                holder.setGone(R.id.errorIV, true)
            }
        }

    }
}