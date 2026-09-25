package com.dicoding.atbquickscan.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.transition.Fade
import android.view.View
import android.view.Window
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bumptech.glide.Glide
import com.dicoding.atbquickscan.R
import com.dicoding.atbquickscan.data.model.AtbResponse
import com.dicoding.atbquickscan.data.network.ApiConfig
import com.dicoding.atbquickscan.databinding.ActivityMainBinding
import com.dicoding.atbquickscan.databinding.BottomSheetSumberGambarBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
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
 * 1. Pengguna mengetuk kotak upload, lalu muncul lembar pilihan: Galeri atau Kamera.
 * 2. Setelah gambar dipilih/difoto, gambar tampil sebagai pratinjau dan
 *    tombol "Analisis Gambar" menjadi aktif.
 * 3. Pengguna menekan "Analisis Gambar", lalu gambar dikirim ke server ML.
 * 4. Jika berhasil : halaman hasil (ResultActivity) dibuka dengan animasi,
 *                    gambar pratinjau "terbang" ke halaman hasil.
 *    Jika gagal    : kartu error muncul dengan penjelasan yang jelas.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding: cara aman untuk mengakses view di layout tanpa findViewById.
    private lateinit var binding: ActivityMainBinding

    // Alamat (Uri) gambar yang dipilih pengguna. Bernilai null jika belum memilih.
    private var gambarYangDipilih: Uri? = null

    // Alamat (Uri) file tempat aplikasi kamera menyimpan foto.
    // Diisi sesaat sebelum kamera dibuka (lihat fungsi bukaKamera).
    private var uriFotoKamera: Uri? = null

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

    /**
     * PENGAMBIL FOTO (KAMERA)
     *
     * TakePicture membuka aplikasi kamera bawaan HP.
     * Kode di dalam { } dijalankan SETELAH pengguna selesai memotret:
     * - berhasil = true  jika foto berhasil disimpan ke uriFotoKamera
     * - berhasil = false jika pengguna membatalkan
     */
    private val pengambilFoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { berhasil ->
        val uriFoto = uriFotoKamera
        if (berhasil && uriFoto != null) {
            tampilkanGambarTerpilih(uriFoto)
        }
    }

    /**
     * PEMBUKA HALAMAN HASIL
     *
     * Halaman hasil dibuka lewat "launcher" ini (bukan startActivity biasa)
     * supaya kita bisa tahu bagaimana pengguna kembali dari halaman hasil:
     * - RESULT_OK : pengguna menekan "Scan Lagi", jadi halaman utama dikosongkan.
     * - lainnya   : pengguna menekan tombol kembali, jadi gambar dibiarkan tetap ada.
     */
    private val pembukaHalamanHasil = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasil ->
        if (hasil.resultCode == RESULT_OK) {
            tampilkanKeadaanAwal()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aktifkan fitur animasi perpindahan halaman.
        // WAJIB dipanggil paling awal, sebelum isi layar dipasang.
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

        // Pasang splash screen (layar pembuka). WAJIB dipanggil sebelum super.onCreate().
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aturAnimasiPerpindahan()
        aturAksiTombol()
        tampilkanKeadaanAwal()

        // Saat HP diputar, Android membuat ulang halaman ini dari awal.
        // Di sini kita mengambil kembali data yang tadi sudah disimpan.
        if (savedInstanceState != null) {
            val uriKameraTersimpan = savedInstanceState.getString(KUNCI_URI_FOTO_KAMERA)
            if (uriKameraTersimpan != null) {
                uriFotoKamera = Uri.parse(uriKameraTersimpan)
            }

            val uriTersimpan = savedInstanceState.getString(KUNCI_GAMBAR_TERPILIH)
            if (uriTersimpan != null) {
                tampilkanGambarTerpilih(Uri.parse(uriTersimpan))
            }
        }
    }

    /**
     * Dipanggil Android sebelum halaman dihancurkan (misalnya karena HP diputar,
     * atau karena memori HP penuh saat aplikasi kamera sedang terbuka).
     * Kita menyimpan alamat gambar supaya tidak hilang.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val gambar = gambarYangDipilih
        if (gambar != null) {
            outState.putString(KUNCI_GAMBAR_TERPILIH, gambar.toString())
        }
        val fotoKamera = uriFotoKamera
        if (fotoKamera != null) {
            outState.putString(KUNCI_URI_FOTO_KAMERA, fotoKamera.toString())
        }
    }

    /**
     * Mengatur animasi memudar (fade) saat berpindah ke halaman hasil dan kembali lagi.
     * Status bar dan navigation bar dikecualikan supaya warnanya tidak ikut berkedip.
     */
    private fun aturAnimasiPerpindahan() {
        val animasiMemudar = Fade()
        animasiMemudar.excludeTarget(android.R.id.statusBarBackground, true)
        animasiMemudar.excludeTarget(android.R.id.navigationBarBackground, true)

        window.exitTransition = animasiMemudar     // saat halaman ini ditinggalkan
        window.reenterTransition = animasiMemudar  // saat kembali ke halaman ini
    }

    // =====================================================================
    // BAGIAN 1: MENGATUR TOMBOL
    // =====================================================================

    /** Menghubungkan setiap tombol dengan fungsi yang dijalankan saat tombol ditekan. */
    private fun aturAksiTombol() {
        binding.kotakUpload.setOnClickListener {
            tampilkanPilihanSumberGambar()
        }
        binding.tombolGantiGambar.setOnClickListener {
            tampilkanPilihanSumberGambar()
        }
        binding.tombolAnalisis.setOnClickListener {
            mulaiAnalisis()
        }
    }

    /**
     * Menampilkan lembar pilihan (bottom sheet) yang muncul dari bawah layar.
     * Pengguna memilih mengambil gambar dari Galeri atau dari Kamera.
     * Tampilan lembarnya ada di res/layout/bottom_sheet_sumber_gambar.xml.
     */
    private fun tampilkanPilihanSumberGambar() {
        val lembarPilihan = BottomSheetDialog(this)
        val bindingLembar = BottomSheetSumberGambarBinding.inflate(layoutInflater)
        lembarPilihan.setContentView(bindingLembar.root)

        bindingLembar.pilihanGaleri.setOnClickListener {
            lembarPilihan.dismiss()
            bukaPemilihGambar()
        }
        bindingLembar.pilihanKamera.setOnClickListener {
            lembarPilihan.dismiss()
            bukaKamera()
        }

        lembarPilihan.show()
    }

    /** Membuka jendela pemilih gambar. ImageOnly = hanya gambar, video tidak ditampilkan. */
    private fun bukaPemilihGambar() {
        val permintaan = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        pemilihGambar.launch(permintaan)
    }

    /**
     * Membuka aplikasi kamera bawaan HP untuk memotret film rontgen.
     *
     * Aplikasi kamera butuh "tempat" untuk menyimpan foto. Jadi sebelum kamera dibuka,
     * kita siapkan dulu file kosong di folder cache, lalu alamatnya (Uri) kita berikan
     * ke aplikasi kamera.
     *
     * FileProvider = "penjaga pintu" yang mengizinkan aplikasi kamera menulis ke file
     * milik aplikasi kita dengan aman. Pengaturannya ada di AndroidManifest.xml
     * dan res/xml/file_paths.xml.
     *
     * Catatan: kita tidak perlu meminta izin CAMERA, karena yang memotret adalah
     * aplikasi kamera bawaan HP, bukan aplikasi kita.
     */
    private fun bukaKamera() {
        // Siapkan folder "foto_kamera" di dalam cache (namanya sama dengan di file_paths.xml).
        val folderFoto = File(cacheDir, "foto_kamera")
        if (!folderFoto.exists()) {
            folderFoto.mkdirs()
        }
        val fileFoto = File(folderFoto, "kamera_" + UUID.randomUUID().toString() + ".jpg")

        // Ubah File menjadi Uri yang boleh dipakai aplikasi kamera.
        // authority harus sama dengan android:authorities di AndroidManifest.xml.
        val authority = packageName + ".fileprovider"
        val uriFoto = FileProvider.getUriForFile(this, authority, fileFoto)
        uriFotoKamera = uriFoto

        try {
            pengambilFoto.launch(uriFoto)
        } catch (e: ActivityNotFoundException) {
            // Terjadi jika HP tidak punya aplikasi kamera (misalnya di sebagian emulator).
            tampilkanError(
                getString(R.string.error_kamera_judul),
                getString(R.string.error_kamera_pesan)
            )
        }
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
        // Hapus gambar pratinjau (dan batalkan pemuatan gambar yang mungkin masih berjalan).
        Glide.with(this).clear(binding.ivPreview)
        binding.lapisanLoading.visibility = View.GONE
        binding.tombolGantiGambar.visibility = View.GONE

        binding.tombolAnalisis.isEnabled = false

        sembunyikanError()
    }

    /** Menampilkan gambar yang baru dipilih dan mengaktifkan tombol Analisis. */
    private fun tampilkanGambarTerpilih(uri: Uri) {
        gambarYangDipilih = uri

        // Glide memuat gambar dan otomatis mengecilkannya sesuai ukuran kotak.
        // Ini penting untuk foto kamera yang ukurannya bisa sangat besar:
        // jika dimuat langsung dengan setImageURI(), aplikasi bisa kehabisan memori.
        Glide.with(this).load(uri).into(binding.ivPreview)
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
     * Menyalin gambar (dari galeri atau kamera) ke folder cache aplikasi.
     *
     * Kenapa perlu disalin? Uri hanyalah "alamat", bukan file.
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
            // aliranMasuk  = "pipa" untuk MEMBACA gambar dari Uri
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
     *
     * Halaman utama TIDAK langsung dikosongkan di sini. Halaman utama baru
     * dikosongkan jika pengguna menekan "Scan Lagi" (lihat pembukaHalamanHasil).
     */
    private fun bukaHalamanHasil(status: String, fileGambar: File) {
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra(ResultActivity.EXTRA_STATUS, status)
        intent.putExtra(ResultActivity.EXTRA_PATH_GAMBAR, fileGambar.absolutePath)

        // ANIMASI PERPINDAHAN (shared element):
        // gambar pratinjau (ivPreview) akan "terbang" ke posisi gambar di halaman hasil.
        // Syaratnya: kedua gambar punya transitionName yang sama,
        // yaitu @string/transisi_gambar_rontgen di kedua file layout.
        val namaTransisi = getString(R.string.transisi_gambar_rontgen)
        val opsiAnimasi = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this,
            binding.ivPreview,
            namaTransisi
        )

        pembukaHalamanHasil.launch(intent, opsiAnimasi)
    }

    companion object {
        // Kunci untuk menyimpan data saat HP diputar.
        private const val KUNCI_GAMBAR_TERPILIH = "kunci_gambar_terpilih"
        private const val KUNCI_URI_FOTO_KAMERA = "kunci_uri_foto_kamera"
    }
}
