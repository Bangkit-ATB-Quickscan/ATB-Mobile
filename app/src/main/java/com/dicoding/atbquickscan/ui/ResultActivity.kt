package com.dicoding.atbquickscan.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.dicoding.atbquickscan.R
import com.dicoding.atbquickscan.databinding.ActivityResultBinding
import java.io.File

/**
 * HALAMAN HASIL
 *
 * Halaman ini menerima 2 data dari MainActivity:
 * 1. EXTRA_STATUS      : hasil analisis (positif, negatif, atau tidak_valid)
 * 2. EXTRA_PATH_GAMBAR : lokasi file gambar yang dianalisis
 *
 * Lalu menampilkan kartu status dengan ikon, warna, dan teks yang sesuai.
 */
class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ambil data yang dikirim dari MainActivity.
        val status = intent.getStringExtra(EXTRA_STATUS)
        val pathGambar = intent.getStringExtra(EXTRA_PATH_GAMBAR)

        aturToolbar()
        tampilkanStatus(status)
        tampilkanGambar(pathGambar)
        aturAksiTombol()
    }

    // =====================================================================
    // BAGIAN 1: TOOLBAR DAN TOMBOL
    // =====================================================================

    /** Tombol panah kembali di toolbar akan menutup halaman ini. */
    private fun aturToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun aturAksiTombol() {
        binding.tombolFaskes.setOnClickListener {
            bukaPetaFaskes()
        }
        // "Scan Lagi" cukup menutup halaman ini. Halaman utama sudah dikosongkan
        // dan siap dipakai untuk scan berikutnya.
        binding.tombolScanLagi.setOnClickListener {
            finish()
        }
    }

    /** Membuka aplikasi peta (misalnya Google Maps) dan mencari puskesmas terdekat. */
    private fun bukaPetaFaskes() {
        val alamatPencarian = Uri.parse("geo:0,0?q=puskesmas")
        val intentPeta = Intent(Intent.ACTION_VIEW, alamatPencarian)
        try {
            startActivity(intentPeta)
        } catch (e: ActivityNotFoundException) {
            // Terjadi jika tidak ada aplikasi peta di HP.
            Toast.makeText(this, R.string.result_maps_tidak_ada, Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================================
    // BAGIAN 2: MENAMPILKAN HASIL
    // =====================================================================

    /** Memilih ikon, warna, dan teks sesuai status hasil analisis. */
    private fun tampilkanStatus(status: String?) {
        if (status == STATUS_POSITIF) {
            aturKartuStatus(
                ikon = R.drawable.ic_peringatan,
                warnaIkon = R.color.status_positif_icon,
                warnaLatar = R.color.status_positif_container,
                warnaTeks = R.color.status_positif_text,
                judul = R.string.status_positif_judul,
                deskripsi = R.string.status_positif_deskripsi,
                langkah = R.string.status_positif_langkah
            )
        } else if (status == STATUS_NEGATIF) {
            aturKartuStatus(
                ikon = R.drawable.ic_cek,
                warnaIkon = R.color.status_negatif_icon,
                warnaLatar = R.color.status_negatif_container,
                warnaTeks = R.color.status_negatif_text,
                judul = R.string.status_negatif_judul,
                deskripsi = R.string.status_negatif_deskripsi,
                langkah = R.string.status_negatif_langkah
            )
        } else {
            // Status "tidak_valid", atau status tidak dikenal.
            aturKartuStatus(
                ikon = R.drawable.ic_error,
                warnaIkon = R.color.status_tidak_valid_icon,
                warnaLatar = R.color.status_tidak_valid_container,
                warnaTeks = R.color.status_tidak_valid_text,
                judul = R.string.status_tidak_valid_judul,
                deskripsi = R.string.status_tidak_valid_deskripsi,
                langkah = R.string.status_tidak_valid_langkah
            )
        }
    }

    /**
     * Mengisi kartu status.
     * Semua parameter berupa ID resource (angka), misalnya R.color.xxx atau R.string.xxx.
     * ContextCompat.getColor() mengubah ID warna menjadi warna sungguhan,
     * dan otomatis memilih versi terang/gelap sesuai mode HP.
     */
    private fun aturKartuStatus(
        ikon: Int,
        warnaIkon: Int,
        warnaLatar: Int,
        warnaTeks: Int,
        judul: Int,
        deskripsi: Int,
        langkah: Int
    ) {
        val warnaIkonAsli = ContextCompat.getColor(this, warnaIkon)
        val warnaLatarAsli = ContextCompat.getColor(this, warnaLatar)
        val warnaTeksAsli = ContextCompat.getColor(this, warnaTeks)

        binding.ivIkonStatus.setImageResource(ikon)
        binding.ivIkonStatus.setColorFilter(warnaIkonAsli)

        binding.kartuStatus.setCardBackgroundColor(warnaLatarAsli)

        binding.tvJudulStatus.setText(judul)
        binding.tvJudulStatus.setTextColor(warnaTeksAsli)

        binding.tvDeskripsiStatus.setText(deskripsi)
        binding.tvDeskripsiStatus.setTextColor(warnaTeksAsli)

        binding.tvLangkah.setText(langkah)
    }

    /**
     * Menampilkan gambar yang dianalisis memakai Glide.
     * placeholder = yang tampil selama gambar dimuat
     * error       = yang tampil jika gambar gagal dimuat
     * Keduanya kita isi dengan kotak berwarna polos (ColorDrawable).
     */
    private fun tampilkanGambar(pathGambar: String?) {
        if (pathGambar == null) {
            return
        }
        val warnaKotakKosong = ContextCompat.getColor(this, R.color.md_surface_variant)
        val kotakKosong = ColorDrawable(warnaKotakKosong)

        Glide.with(this)
            .load(File(pathGambar))
            .placeholder(kotakKosong)
            .error(kotakKosong)
            .into(binding.ivHasil)
    }

    companion object {
        // Nama "kunci" untuk data yang dikirim lewat Intent.
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_PATH_GAMBAR = "extra_path_gambar"

        // Tiga kemungkinan status hasil analisis.
        const val STATUS_POSITIF = "positif"
        const val STATUS_NEGATIF = "negatif"
        const val STATUS_TIDAK_VALID = "tidak_valid"
    }
}
