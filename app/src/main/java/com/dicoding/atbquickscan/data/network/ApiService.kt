package com.dicoding.atbquickscan.data.network

import com.dicoding.atbquickscan.data.model.AtbResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("endpoint")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
    ):AtbResponse

}