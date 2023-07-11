package com.chat.base.endpoint.entity

import android.content.Context

class ChooseLocationMenu(val context: Context, val iBack: IBack) {
    interface IBack {
        fun onResult(address: String, title: String, latitude: Double, longitude: Double)
    }
}