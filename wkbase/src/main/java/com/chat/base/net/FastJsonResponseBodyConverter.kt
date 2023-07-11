package com.chat.base.net
import com.alibaba.fastjson.JSON
import okhttp3.ResponseBody
import retrofit2.Converter
import java.lang.reflect.Type

class FastJsonResponseBodyConverter<T>(
    private val type: Type
) : Converter<ResponseBody, T> {

    override fun convert(value: ResponseBody): T? {
        return value.use {
            JSON.parseObject(value.string(), type)
        }
    }
}