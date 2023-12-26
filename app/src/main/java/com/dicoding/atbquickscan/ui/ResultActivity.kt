package com.dicoding.atbquickscan.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.dicoding.atbquickscan.R

class ResultActivity : AppCompatActivity() {

    private lateinit var imageResult: ImageView
    private lateinit var textResult: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        imageResult = findViewById(R.id.iv_result)
        textResult = findViewById(R.id.tv_result)

        val text = intent.getStringExtra("text")
        val imageUrl = intent.getStringExtra("imageUrl")

        textResult.text = text
        Glide.with(this)
            .load(imageUrl)
            .into(imageResult)

        supportActionBar?.apply {
            title = "Result"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}