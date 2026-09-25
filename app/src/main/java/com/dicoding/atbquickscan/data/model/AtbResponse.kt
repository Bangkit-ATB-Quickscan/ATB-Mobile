package com.dicoding.atbquickscan.data.model

import com.google.gson.annotations.SerializedName

/**
 * BENTUK JAWABAN DARI SERVER ML
 *
 * Contoh jawaban yang diharapkan dari server:
 *     { "prediksi": "positif" }   atau   { "prediksi": "negatif" }
 *
 * Kenapa tipenya String? (boleh null)?
 * Kalau server lupa mengirim "prediksi", nilainya akan null.
 * Dengan String? kita wajib mengecek null dulu, jadi aplikasi tidak crash.
 */
data class AtbResponse(
    @field:SerializedName("prediksi")
    val prediksi: String? = null
)
