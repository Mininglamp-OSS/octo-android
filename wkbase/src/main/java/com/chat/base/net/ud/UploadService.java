package com.chat.base.net.ud;


import com.chat.base.net.entity.UploadResultEntity;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.MultipartBody;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Url;

public interface UploadService {
    @Multipart
    @POST
    Observable<UploadResultEntity> upload(@Url String url, @Part MultipartBody.Part file);
}
