package com.example.apk_auto_tempel_srcht

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

// [FITUR: Jembatan Antarmuka Flutter dan Native Android] - Menghubungkan logika antarmuka Flutter dengan layanan sistem Android 16 melalui MethodChannel dan EventChannel
class MainActivity : FlutterActivity() {

    companion object {
        private const val SALURAN_METODE_UTAMA = "com.example.apk_auto_tempel_srcht/saluran_utama"
        private const val SALURAN_PERISTIWA_STREAM = "com.example.apk_auto_tempel_srcht/saluran_peristiwa"

        private var pemancarPeristiwa: EventChannel.EventSink? = null
        private val penanganUtama = Handler(Looper.getMainLooper())

        // [FITUR: Pemancar Peristiwa ke Flutter UI]
        fun kirimPeristiwaAntreanDiperbarui(antrean: List<Map<String, Any>>) {
            penanganUtama.post {
                pemancarPeristiwa?.success(
                    mapOf(
                        "jenis" to "antrean_diperbarui",
                        "antrean" to antrean
                    )
                )
            }
        }

        fun kirimPeristiwaStatusLayanan(aktif: Boolean) {
            penanganUtama.post {
                pemancarPeristiwa?.success(
                    mapOf(
                        "jenis" to "status_layanan",
                        "aktif" to aktif
                    )
                )
            }
        }

        fun kirimPeristiwaBukaEditor(jalurBerkas: String) {
            penanganUtama.postDelayed({
                pemancarPeristiwa?.success(
                    mapOf(
                        "jenis" to "buka_editor",
                        "jalurBerkas" to jalurBerkas
                    )
                )
            }, 300)
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tanganiNiatEdit(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tanganiNiatEdit(intent)
    }

    private fun tanganiNiatEdit(niat: Intent?) {
        val jalurBerkas = niat?.getStringExtra("jalur_berkas_edit")
        if (!jalurBerkas.isNullOrEmpty()) {
            kirimPeristiwaBukaEditor(jalurBerkas)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // [FITUR: Konfigurasi Saluran Metode Flutter]
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SALURAN_METODE_UTAMA)
            .setMethodCallHandler { panggilan, hasil ->
                when (panggilan.method) {
                    "periksaIzin" -> {
                        hasil.success(dapatkanStatusIzin())
                    }
                    "mintaIzinAksesSemuaBerkas" -> {
                        mintaIzinAksesSemuaBerkas()
                        hasil.success(true)
                    }
                    "mintaIzinNotifikasi" -> {
                        mintaIzinNotifikasi()
                        hasil.success(true)
                    }
                    "mintaIzinOverlay" -> {
                        mintaIzinOverlay()
                        hasil.success(true)
                    }
                    "mintaAbaikanPenghematBaterai" -> {
                        mintaAbaikanPenghematBaterai()
                        hasil.success(true)
                    }
                    "mulaiLayanan" -> {
                        mulaiLayananLatarDepan()
                        hasil.success(true)
                    }
                    "hentikanLayanan" -> {
                        hentikanLayananLatarDepan()
                        hasil.success(true)
                    }
                    "statusLayanan" -> {
                        hasil.success(LayananTangkapanLayar.layananSedangBerjalan)
                    }
                    "dapatkanDaftarAntrean" -> {
                        PengelolaBrankas.sinkronkanAntreanDariPenyimpanan(this)
                        hasil.success(PengelolaBrankas.dapatkanAntreanSebagaiPeta())
                    }
                    "salinGambarKePapanKlip" -> {
                        val jalurBerkas = panggilan.argument<String>("jalurBerkas") ?: ""
                        val sukses = PengelolaBrankas.salinGambarKePapanKlip(this, jalurBerkas)
                        hasil.success(sukses)
                    }
                    "salinBanyakGambarKePapanKlip" -> {
                        val daftarJalur = panggilan.argument<List<String>>("daftarJalur") ?: emptyList()
                        val sukses = PengelolaBrankas.salinBanyakGambarKePapanKlip(this, daftarJalur)
                        hasil.success(sukses)
                    }
                    "simpanGambarKeGaleriPublik" -> {
                        val jalurBerkas = panggilan.argument<String>("jalurBerkas") ?: ""
                        val sukses = PengelolaBrankas.simpanGambarKeGaleriPublik(this, jalurBerkas)
                        hasil.success(sukses)
                    }
                    "bagikanSemuaAntrean" -> {
                        val sukses = PengelolaBrankas.bagikanSemuaAntrean(this)
                        hasil.success(sukses)
                    }
                    "simpanHasilEditKeGaleri" -> {
                        val dataByte = panggilan.argument<ByteArray>("dataByte")
                        val namaBerkas = panggilan.argument<String>("namaBerkas")
                        if (dataByte != null) {
                            val uriHasil = PengelolaBrankas.simpanHasilEditKeGaleri(this, dataByte, namaBerkas)
                            hasil.success(uriHasil)
                        } else {
                            hasil.error("DATA_KOSONG", "Data byte gambar tidak boleh kosong", null)
                        }
                    }
                    "hapusGambarDariAntrean" -> {
                        val jalurBerkas = panggilan.argument<String>("jalurBerkas") ?: ""
                        val sukses = PengelolaBrankas.hapusGambarDariAntrean(this, jalurBerkas)
                        hasil.success(sukses)
                    }
                    "kosongkanSemuaAntrean" -> {
                        PengelolaBrankas.kosongkanBrankas(this)
                        hasil.success(true)
                    }
                    "bukaTautanWeb" -> {
                        val url = panggilan.argument<String>("url") ?: "https://github.com/Stira-y/auto-tempel-screshot"
                        bukaTautanBrowser(url)
                        hasil.success(true)
                    }
                    "aturDurasiTimer" -> {
                        hasil.success(true)
                    }
                    "dapatkanSisaWaktu" -> {
                        hasil.success(0)
                    }
                    else -> hasil.notImplemented()
                }
            }

        // [FITUR: Konfigurasi Saluran Peristiwa Stream Flutter]
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, SALURAN_PERISTIWA_STREAM)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    pemancarPeristiwa = events
                    kirimPeristiwaStatusLayanan(LayananTangkapanLayar.layananSedangBerjalan)
                    kirimPeristiwaAntreanDiperbarui(PengelolaBrankas.dapatkanAntreanSebagaiPeta())

                    PengelolaBrankas.aturPendengarPerubahan { antrean ->
                        kirimPeristiwaAntreanDiperbarui(PengelolaBrankas.dapatkanAntreanSebagaiPeta())
                    }
                }

                override fun onCancel(arguments: Any?) {
                    pemancarPeristiwa = null
                }
            })
    }

    // [FITUR: Status Seluruh Izin Android 16] - Memeriksa kepatuhan izin sistem yang dibutuhkan
    private fun dapatkanStatusIzin(): Map<String, Boolean> {
        val izinAksesSemuaBerkas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val izinNotifikasi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val izinOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        val pengelolaDaya = getSystemService(Context.POWER_SERVICE) as PowerManager
        val izinAbaikanBaterai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pengelolaDaya.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        return mapOf(
            "aksesSemuaBerkas" to izinAksesSemuaBerkas,
            "notifikasi" to izinNotifikasi,
            "overlay" to izinOverlay,
            "penghematBaterai" to izinAbaikanBaterai
        )
    }

    // [FITUR: Permintaan Izin Akses Semua Berkas] - Membuka layar pengaturan All Files Access Android 11+
    private fun mintaIzinAksesSemuaBerkas() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val niat = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(niat)
            } catch (e: Exception) {
                val niatSemua = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(niatSemua)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                101
            )
        }
    }

    // [FITUR: Permintaan Izin Notifikasi] - Meminta izin POST_NOTIFICATIONS untuk Android 13+
    private fun mintaIzinNotifikasi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                102
            )
        }
    }

    // [FITUR: Permintaan Izin Overlay Window] - Membuka halaman izin Tampilkan di Atas Aplikasi Lain
    private fun mintaIzinOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val niat = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(niat)
        }
    }

    // [FITUR: Permintaan Pengabaian Optimasi Baterai] - Mencegah sistem mematikan background service
    private fun mintaAbaikanPenghematBaterai() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val niat = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(niat)
            } catch (e: Exception) {
                val niatUmum = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(niatUmum)
            }
        }
    }

    // [FITUR: Mulai Layanan Foreground] - Mengirim Intent START ke LayananTangkapanLayar
    private fun mulaiLayananLatarDepan() {
        val niat = Intent(this, LayananTangkapanLayar::class.java).apply {
            action = LayananTangkapanLayar.AKSI_MULAI
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(niat)
        } else {
            startService(niat)
        }
    }

    // [FITUR: Hentikan Layanan Foreground] - Mengirim Intent STOP ke LayananTangkapanLayar
    private fun hentikanLayananLatarDepan() {
        val niat = Intent(this, LayananTangkapanLayar::class.java).apply {
            action = LayananTangkapanLayar.AKSI_HENTIKAN
        }
        startService(niat)
    }

    // [FITUR: Buka Tautan Browser] - Membuka tautan repositori GitHub di browser pengguna
    private fun bukaTautanBrowser(tautan: String) {
        try {
            val niat = Intent(Intent.ACTION_VIEW, Uri.parse(tautan)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(niat)
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal membuka browser: ${e.message}")
        }
    }
}
