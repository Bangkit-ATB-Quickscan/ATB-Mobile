package com.dicoding.atbquickscan.ui

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dicoding.atbquickscan.databinding.ActivityMainBinding
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    var binding: ActivityMainBinding? = null
    var imageUri: Uri? = null
    var storageReference: StorageReference? = null
    var progressDialog: ProgressDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        binding!!.selectImagebtn.setOnClickListener { selectImage() }
        binding!!.uploadimagebtn.setOnClickListener { uploadImage() }
    }

    private fun uploadImage() {
        progressDialog = ProgressDialog(this)
        progressDialog!!.setTitle("Uploading File....")
        progressDialog!!.show()
        val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.CANADA)
        val now = Date()
        val fileName = formatter.format(now)
        storageReference = FirebaseStorage.getInstance().getReference("images/$fileName")
        storageReference!!.putFile(imageUri!!)
            .addOnSuccessListener {
                binding!!.firebaseimage.setImageURI(null)
                Toast.makeText(this@MainActivity, "Successfully Uploaded", Toast.LENGTH_SHORT)
                    .show()
                if (progressDialog!!.isShowing) progressDialog!!.dismiss()
            }.addOnFailureListener {
                if (progressDialog!!.isShowing) progressDialog!!.dismiss()
                Toast.makeText(this@MainActivity, "Failed to Upload", Toast.LENGTH_SHORT).show()
            }
    }

    private fun selectImage() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && data != null && data.data != null) {
            imageUri = data.data
            binding!!.firebaseimage.setImageURI(imageUri)
        }
    }
}