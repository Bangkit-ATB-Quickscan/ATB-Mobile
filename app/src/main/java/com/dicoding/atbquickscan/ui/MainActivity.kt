package com.dicoding.atbquickscan.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dicoding.atbquickscan.R
import com.dicoding.atbquickscan.data.model.AtbResponse
import com.dicoding.atbquickscan.data.network.ApiConfig
import com.dicoding.atbquickscan.databinding.ActivityMainBinding
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * HALAMAN UTAMA
 *
 * Alur halaman ini:
 * 1. Pengguna mengetuk kotak upload, lalu galeri (Photo Picker) terbuka.
 * 2. Setelah gambar dipilih, gambar tampil sebagai pratinjau dan
 *    tombol "Analisis Gambar" menjadi aktif.
 * 3. Pengguna menekan "Analisis Gambar", lalu gambar dikirim ke server ML.
 * 4. Jika berhasil : halaman hasil (ResultActivity) dibuka.
 *    Jika gagal    : kartu error muncul dengan penjelasan yang jelas.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding: cara aman untuk mengakses view di layout tanpa findViewById.
    private lateinit var binding: ActivityMainBinding

    // Alamat (Uri) gambar yang dipilih pengguna. Bernilai null jika belum memilih.
    private var gambarYangDipilih: Uri? = null

    /**
     * PEMILIH GAMBAR (PHOTO PICKER)
     *
     * registerForActivityResult menyiapkan "jendela pemilih gambar" bawaan Android.
     * Kode di dalam kurung kurawal { } dijalankan SETELAH pengguna selesai memilih.
     * - uri berisi alamat gambar jika pengguna memilih gambar
     * - uri bernilai null jika pengguna menutup pemilih tanpa memilih apa pun
     */
    private val pemilihGambar = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            tampilkanGambarTerpilih(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pasang splash screen (layar pembuka). WAJIB dipanggil sebelum super.onCreate().
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aturAksiTombol()
        tampilkanKeadaanAwal()

        // Saat HP diputar, Android membuat ulang halaman ini dari awal.
        // Di sini kita mengambil kembali gambar yang tadi sudah dipilih pengguna.
        if (savedInstanceState != null) {
            val uriTersimpan = savedInstanceState.getString(KUNCI_GAMBAR_TERPILIH)
            if (uriTersimpan != null) {
                tampilkanGambarTerpilih(Uri.parse(uriTersimpan))
            }
        }
    }

    /**
     * Dipanggil Android sebelum halaman dihancurkan (misalnya karena HP diputar).
     * Kita menyimpan alamat gambar yang dipilih supaya tidak hilang.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val gambar = gambarYangDipilih
        if (gambar != null) {
            outState.putString(KUNCI_GAMBAR_TERPILIH, gambar.toString())
        }
    }

    // =====================================================================
    // BAGIAN 1: MENGATUR TOMBOL
    // =====================================================================

    /** Menghubungkan setiap tombol dengan fungsi yang dijalankan saat tombol ditekan. */
    private fun aturAksiTombol() {
        binding.kotakUpload.setOnClickListener {
            bukaPemilihGambar()
        }
        binding.tombolGantiGambar.setOnClickListener {
            bukaPemilihGambar()
        }
        binding.tombolAnalisis.setOnClickListener {
            mulaiAnalisis()
        }
    }

    /** Membuka jendela pemilih gambar. ImageOnly = hanya gambar, video tidak ditampilkan. */
    private fun bukaPemilihGambar() {
        val permintaan = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        pemilihGambar.launch(permintaan)
    }

    // =====================================================================
    // BAGIAN 2: MENGATUR TAMPILAN LAYAR
    // =====================================================================

    /**
     * Mengembalikan layar ke keadaan awal:
     * belum ada gambar, tidak ada error, tombol Analisis nonaktif.
     */
    private fun tampilkanKeadaanAwal() {
        gambarYangDipilih = null

        binding.placeholderUpload.visibility = View.VISIBLE
        binding.ivPreview.visibility = View.GONE
        binding.ivPreview.setImageDrawable(null)
        binding.lapisanLoading.visibility = View.GONE
        binding.tombolGantiGambar.visibility = View.GONE

        binding.tombolAnalisis.isEnabled = false

        sembunyikanError()
    }

    /** Menampilkan gambar yang baru dipilih dan mengaktifkan tombol Analisis. */
    private fun tampilkanGambarTerpilih(uri: Uri) {
        gambarYangDipilih = uri

        binding.ivPreview.setImageURI(uri)
        binding.ivPreview.visibility = View.VISIBLE
        binding.placeholderUpload.visibility = View.GONE
        binding.tombolGantiGambar.visibility = View.VISIBLE

        binding.tombolAnalisis.isEnabled = true

        // Gambar baru dipilih, jadi error sebelumnya (kalau ada) tidak relevan lagi.
        sembunyikanError()
    }

    /** Menampilkan animasi loading dan mengunci tombol supaya tidak ditekan dua kali. */
    private fun tampilkanLoading() {
        binding.lapisanLoading.visibility = View.VISIBLE
        binding.tombolAnalisis.isEnabled = false
        binding.tombolGantiGambar.isEnabled = false
        binding.kotakUpload.isEnabled = false
    }

    /** Menyembunyikan animasi loading dan membuka kembali tombol-tombol. */
    private fun sembunyikanLoading() {
        binding.lapisanLoading.visibility = View.GONE
        binding.tombolGantiGambar.isEnabled = true
        binding.kotakUpload.isEnabled = true

        // Tombol Analisis hanya aktif kalau memang sudah ada gambar.
        if (gambarYangDipilih != null) {
            binding.tombolAnalisis.isEnabled = true
        } else {
            binding.tombolAnalisis.isEnabled = false
        }
    }

    /** Menampilkan kartu error berisi judul dan penjelasan masalah. */
    private fun tampilkanError(judul: String, pesan: String) {
        binding.tvJudulError.text = judul
        binding.tvPesanError.text = pesan
        binding.kartuError.visibility = View.VISIBLE

        // Gulir layar ke kartu error supaya pengguna pasti melihatnya.
        // post { } = jalankan setelah layar selesai digambar ulang.
        binding.scrollIsi.post {
            binding.scrollIsi.smoothScrollTo(0, binding.kartuError.top)
        }
    }

    /** Menyembunyikan kartu error. */
    private fun sembunyikanError() {
        binding.kartuError.visibility = View.GONE
    }

    // =====================================================================
    // BAGIAN 3: PROSES ANALISIS GAMBAR
    // =====================================================================

    /**
     * Dijalankan saat tombol "Analisis Gambar" ditekan.
     * Setiap langkah dicek satu per satu. Jika ada yang gagal,
     * kita tampilkan error lalu berhenti (return).
     */
    private fun mulaiAnalisis() {
        sembunyikanError()

        // Langkah 1: pastikan pengguna sudah memilih gambar.
        val uriGambar = gambarYangDipilih
        if (uriGambar == null) {
            return
        }

        // Langkah 2: pastikan alamat server ML sudah diisi di ApiConfig.kt.
        if (!ApiConfig.isServerSudahDiatur()) {
            tampilkanError(
                getString(R.string.error_server_belum_diatur_judul),
                getString(R.string.error_server_belum_diatur_pesan)
            )
            return
        }

        // Langkah 3: cari tahu jenis file (misal "image/jpeg" atau "image/png").
        var jenisFile = contentResolver.getType(uriGambar)
        if (jenisFile == null) {
            jenisFile = "image/jpeg"
        }

        // Langkah 4: salin gambar ke file sementara supaya bisa dikirim.
        val fileGambar = salinGambarKeFileSementara(uriGambar, jenisFile)
        if (fileGambar == null) {
            tampilkanError(
                getString(R.string.error_gambar_judul),
                getString(R.string.error_gambar_pesan)
            )
            return
        }

        // Langkah 5: kirim gambar ke server.
        kirimGambarKeServer(fileGambar, jenisFile)
    }

    /**
     * Menyalin gambar dari galeri ke folder cache aplikasi.
     *
     * Kenapa perlu disalin? Uri dari galeri hanyalah "alamat", bukan file.
     * Untuk dikirim ke server dan ditampilkan di halaman hasil,
     * kita butuh file sungguhan yang bisa dibaca kapan saja.
     *
     * Mengembalikan File jika berhasil, atau null jika gagal.
     */
    private fun salinGambarKeFileSementara(uri: Uri, jenisFile: String): File? {
        // Tentukan akhiran nama file sesuai jenisnya.
        var akhiranFile = ".jpg"
        if (jenisFile == "image/png") {
            akhiranFile = ".png"
        }

        // UUID = kode acak yang unik, supaya nama file tidak pernah bentrok.
        val namaFile = "rontgen_" + UUID.randomUUID().toString() + akhiranFile
        val fileSementara = File(cacheDir, namaFile)

        try {
            // aliranMasuk  = "pipa" untuk MEMBACA gambar dari galeri
            // aliranKeluar = "pipa" untuk MENULIS gambar ke file sementara
            val aliranMasuk = contentResolver.openInputStream(uri)
            if (aliranMasuk == null) {
                return null
            }
            val aliranKeluar = FileOutputStream(fileSementara)

            // Salin semua isi gambar dari pipa masuk ke pipa keluar.
            aliranMasuk.copyTo(aliranKeluar)

            // Tutup kedua pipa setelah selesai supaya memori tidak bocor.
            aliranMasuk.close()
            aliranKeluar.close()
            return fileSementara
        } catch (e: IOException) {
            // Terjadi jika gambar gagal dibaca atau file gagal ditulis.
            return null
        } catch (e: SecurityException) {
            // Terjadi jika aplikasi tidak lagi punya izin membaca gambar tersebut.
            return null
        }
    }

    /**
     * Mengirim file gambar ke server ML memakai Retrofit.
     *
     * enqueue() menjalankan pengiriman di "latar belakang", jadi layar tidak macet.
     * Setelah selesai, Retrofit memanggil salah satu dari:
     * - onResponse : server membalas (bisa sukses, bisa juga kode error seperti 400/500)
     * - onFailure  : server tidak bisa dihubungi sama sekali (tidak ada internet, alamat salah, dll)
     */
    private fun kirimGambarKeServer(fileGambar: File, jenisFile: String) {
        // Bungkus file menjadi bagian "form" bernama "file".
        val isiFile = fileGambar.asRequestBody(jenisFile.toMediaType())
        val dataGambar = MultipartBody.Part.createFormData("file", fileGambar.name, isiFile)

        tampilkanLoading()

        val apiService = ApiConfig.getApiService()
        val panggilan = apiService.uploadImage(dataGambar)

        panggilan.enqueue(object : Callback<AtbResponse> {
            override fun onResponse(call: Call<AtbResponse>, response: Response<AtbResponse>) {
                sembunyikanLoading()
                tanganiJawabanServer(response, fileGambar)
            }

            override fun onFailure(call: Call<AtbResponse>, t: Throwable) {
                sembunyikanLoading()
                tanganiGagalTerhubung(t)
            }
        })
    }

    /** Membaca jawaban dari server lalu memutuskan apa yang ditampilkan. */
    private fun tanganiJawabanServer(response: Response<AtbResponse>, fileGambar: File) {
        // Kasus A: server membalas dengan sukses (kode 200-299).
        if (response.isSuccessful) {
            val hasil = response.body()
            var prediksi: String? = null
            if (hasil != null) {
                prediksi = hasil.prediksi
            }

            if (prediksi == null) {
                tampilkanError(
                    getString(R.string.error_jawaban_tidak_dikenal_judul),
                    getString(R.string.error_jawaban_tidak_dikenal_pesan, "kosong")
                )
                return
            }

            // lowercase() supaya "Positif" dan "positif" dianggap sama.
            val prediksiHurufKecil = prediksi.lowercase()
            if (prediksiHurufKecil == "positif") {
                bukaHalamanHasil(ResultActivity.STATUS_POSITIF, fileGambar)
            } else if (prediksiHurufKecil == "negatif") {
                bukaHalamanHasil(ResultActivity.STATUS_NEGATIF, fileGambar)
            } else {
                tampilkanError(
                    getString(R.string.error_jawaban_tidak_dikenal_judul),
                    getString(R.string.error_jawaban_tidak_dikenal_pesan, prediksi)
                )
            }
            return
        }

        // Kasus B: server membalas dengan kode error.
        val kodeError = response.code()

        // Server ML tim kita membalas kode 400 dengan pesan "paru tidak terdeteksi"
        // jika model tidak cukup yakin membaca gambar. Ini bukan error aplikasi,
        // melainkan salah satu hasil analisis, jadi kita tampilkan di halaman hasil.
        if (kodeError == 400 && apakahParuTidakTerdeteksi(response)) {
            bukaHalamanHasil(ResultActivity.STATUS_TIDAK_VALID, fileGambar)
            return
        }

        tampilkanError(
            getString(R.string.error_server_judul),
            getString(R.string.error_server_pesan, kodeError)
        )
    }

    /** Mengecek apakah isi pesan error dari server mengandung "paru tidak terdeteksi". */
    private fun apakahParuTidakTerdeteksi(response: Response<AtbResponse>): Boolean {
        val bodyError = response.errorBody()
        if (bodyError == null) {
            return false
        }
        val isiPesan = bodyError.string()
        return isiPesan.contains("paru tidak terdeteksi")
    }

    /** Menampilkan error yang sesuai ketika server tidak bisa dihubungi. */
    private fun tanganiGagalTerhubung(t: Throwable) {
        // IOException = masalah jaringan: tidak ada internet, alamat server salah,
        // server mati, atau waktu tunggu habis.
        if (t is IOException) {
            tampilkanError(
                getString(R.string.error_koneksi_judul),
                getString(R.string.error_koneksi_pesan)
            )
        } else {
            var detail = t.message
            if (detail == null) {
                detail = t.javaClass.simpleName
            }
            tampilkanError(
                getString(R.string.error_umum_judul),
                getString(R.string.error_umum_pesan, detail)
            )
        }
    }

    // =====================================================================
    // BAGIAN 4: PINDAH KE HALAMAN HASIL
    // =====================================================================

    /**
     * Membuka halaman hasil sambil membawa 2 data:
     * status hasil analisis dan lokasi file gambar.
     */
    private fun bukaHalamanHasil(status: String, fileGambar: File) {
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra(ResultActivity.EXTRA_STATUS, status)
        intent.putExtra(ResultActivity.EXTRA_PATH_GAMBAR, fileGambar.absolutePath)
        startActivity(intent)

        // Kosongkan halaman utama, supaya saat pengguna kembali
        // halaman ini sudah siap untuk scan berikutnya.
        tampilkanKeadaanAwal()
    }

    companion object {
        // Kunci untuk menyimpan gambar terpilih saat HP diputar.
        private const val KUNCI_GAMBAR_TERPILIH = "kunci_gambar_terpilih"
    }
}
