package com.dicoding.atbquickscan.data.network

import com.dicoding.atbquickscan.data.model.AtbResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * DAFTAR ENDPOINT SERVER ML
 *
 * Endpoint = "alamat halaman" di server yang kita panggil.
 * Alamat lengkapnya = ML_API_URL (di ApiConfig.kt) + "predict".
 * Jika nama endpoint di server berbeda, cukup ganti teks "predict" di bawah.
 */
interface ApiService {

    /**
     * Mengirim satu gambar ke server untuk dianalisis.
     *
     * @Multipart = data dikirim dalam bentuk "form" yang berisi file,
     *              sama seperti saat kita meng-upload file lewat website.
     * Call<AtbResponse> = panggilan yang nanti dijalankan dengan enqueue(),
     *                     dan jawabannya berbentuk AtbResponse.
     */
    @Multipart
    @POST("predict")
    fun uploadImage(
        @Part file: MultipartBody.Part
    ): Call<AtbResponse>
}
