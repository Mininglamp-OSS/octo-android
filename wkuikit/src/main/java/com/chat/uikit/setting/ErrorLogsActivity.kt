package com.chat.uikit.setting

import android.Manifest
import android.os.Build
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.SendFileMenu
import com.chat.base.entity.PopupMenuItem
import com.chat.base.utils.DataCleanManager
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKPermissions
import com.chat.base.utils.WKPermissions.IPermissionResult
import com.chat.base.utils.WKReader
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.ActCommonListLayoutBinding
import java.io.File

class ErrorLogsActivity : WKBaseActivity<ActCommonListLayoutBinding>() {
    private lateinit var adapter: FileAdapter
    override fun getViewBinding(): ActCommonListLayoutBinding {
        return ActCommonListLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView?) {
        titleTv!!.setText(R.string.error_data)
    }

    override fun initPresenter() {
        val desc = String.format(
            getString(R.string.file_permissions_des),
            getString(R.string.app_name)
        )
        if (Build.VERSION.SDK_INT < 33) {
            WKPermissions.getInstance().checkPermissions(
                object : IPermissionResult {
                    override fun onResult(result: Boolean) {}

                    override fun clickResult(isCancel: Boolean) {
                        finish()
                    }
                },
                this,
                desc,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            WKPermissions.getInstance().checkPermissions(
                object : IPermissionResult {
                    override fun onResult(result: Boolean) {}

                    override fun clickResult(isCancel: Boolean) {
                        finish()
                    }
                },
                this,
                desc,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        }
    }

    override fun initView() {
        adapter = FileAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
    }

    override fun initListener() {
    }

    override fun initData() {
        val list = getData()
        adapter.setList(list)
    }

    fun getData(): ArrayList<LogEntity> {
        val path = WKFileUtils.getInstance().getNormalFileSavePath("wkCrash")

        val fileList: ArrayList<LogEntity> = ArrayList()

        val file = File(path)
        val tempList: Array<File>? = file.listFiles()

        for (i in tempList!!.indices) {
            if (tempList[i].isFile) {
                val size = WKFileUtils.getInstance().getFileSize(File(tempList[i].toString()))
                val sizeStr = DataCleanManager.getFormatSize(size.toDouble())
                fileList.add(
                    LogEntity(
                        tempList[i].name,
                        tempList[i].toString(),
                        sizeStr,
                        tempList[i].lastModified(),
                        size
                    )
                )
            }
        }
        if (WKReader.isNotEmpty(fileList))
            fileList.sortWith { t: LogEntity, t1: LogEntity -> (t.time - t1.time).toInt() }
        return fileList
    }

    class LogEntity(
        val name: String,
        val path: String,
        val sizeStr: String,
        val time: Long,
        val size: Long
    )

    class FileAdapter : BaseQuickAdapter<LogEntity, BaseViewHolder>(R.layout.item_logger_layout) {
        override fun convert(holder: BaseViewHolder, item: LogEntity) {
            holder.setText(R.id.nameTv, item.name)
            holder.setText(R.id.sizeTv, item.sizeStr)
            holder.setText(R.id.timeTv, WKTimeUtils.getInstance().time2DateStr1(item.time))
            val list: MutableList<PopupMenuItem> = ArrayList()
            list.add(
                PopupMenuItem(context.getString(R.string.forward),
                    R.mipmap.msg_forward, object : PopupMenuItem.IClick {
                        override fun onClick() {
                            EndpointManager.getInstance().invoke(
                                "forward_file",
                                SendFileMenu(item.name, item.path, item.size)
                            )
                        }
                    })
            )
            list.add(
                PopupMenuItem(context.getString(R.string.str_delete), R.mipmap.msg_delete,
                    object : PopupMenuItem.IClick {
                        override fun onClick() {
                            val file = File(item.path)
                            if (file.exists()) {
                                file.delete()
                                removeAt(holder.bindingAdapterPosition)
                            }
                        }
                    })
            )
            WKDialogUtils.getInstance()
                .setViewLongClickPopup(holder.getView(R.id.contentLayout), list)

        }
    }
}