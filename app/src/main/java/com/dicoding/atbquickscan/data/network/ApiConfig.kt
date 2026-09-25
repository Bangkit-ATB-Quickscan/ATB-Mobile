package com.dicoding.atbquickscan.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * PENGATURAN KONEKSI KE SERVER ML
 *
 * Ini SATU-SATUNYA tempat yang perlu diubah untuk menyambungkan aplikasi
 * ke server ML. Selama ML_API_URL masih kosong, aplikasi akan menampilkan
 * pesan "Server analisis belum terhubung" ketika tombol Analisis ditekan.
 */
object ApiConfig {

    /**
     * Alamat server ML.
     * - Biarkan kosong ("") jika server belum ada.
     * - Jika sudah ada, isi dengan alamat server dan AKHIRI dengan garis miring "/".
     *   Contoh: "https://nama-server-ml-kamu.a.run.app/"
     */
    const val ML_API_URL = ""

    /**
     * Mengecek apakah alamat server sudah diisi.
     * Mengembalikan true jika sudah diisi, false jika masih kosong.
     */
    fun isServerSudahDiatur(): Boolean {
        return ML_API_URL.isNotBlank()
    }

    /**
     * Membuat objek ApiService yang siap dipakai untuk memanggil server.
     * Panggil fungsi ini HANYA jika isServerSudahDiatur() bernilai true.
     */
    fun getApiService(): ApiService {
        // Interceptor ini mencatat setiap request ke Logcat, berguna saat debugging.
        // Level BASIC hanya mencatat alamat & kode status, tidak mencatat isi gambar
        // (isi gambar sangat besar dan membuat Logcat berantakan).
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)

        // OkHttpClient = "kurir" yang mengantar data ke server.
        // Batas waktu 30 detik: jika server tidak membalas selama itu, dianggap gagal.
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Retrofit = library yang mengubah interface ApiService menjadi panggilan internet.
        // GsonConverterFactory = mengubah jawaban JSON dari server menjadi objek AtbResponse.
        val retrofit = Retrofit.Builder()
            .baseUrl(ML_API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
