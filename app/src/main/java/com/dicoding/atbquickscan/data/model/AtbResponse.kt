package com.dicoding.atbquickscan.data.model

import com.google.gson.annotations.SerializedName

data class AtbResponse(
    @field:SerializedName("prediksi")
    val prediksi: String
)