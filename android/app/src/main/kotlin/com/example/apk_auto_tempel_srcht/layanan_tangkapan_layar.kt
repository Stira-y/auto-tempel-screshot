package com.example.apk_auto_tempel_srcht

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat

// [FITUR: Layanan Latar Depan Pemantauan Screenshot] - Menjaga aplikasi tetap aktif di latar belakang dengan foreground service khusus Android 16 untuk memantau screenshot baru dan retensi 30 hari
class LayananTangkapanLayar : Service() {

    companion object {
        private const val TAG = "LayananScreenshot"
        private const val ID_SALURAN_NOTIFIKASI = "saluran_layanan_tangkapan_layar"
        private const val ID_NOTIFIKASI_FOREGROUND = 1001

        const val AKSI_MULAI = "AKSI_MULAI"
        const val AKSI_HENTIKAN = "AKSI_HENTIKAN"

        var layananSedangBerjalan = false
            private set
    }

    private var pengamatMedia: PengamatMediaScreenshot? = null
    private lateinit var pengelolaNotifikasi: NotificationManager
    private val penanganUtama = Handler(Looper.getMainLooper())

    // Tugas berkala setiap 6 jam untuk membersihkan screenshot usang (>30 hari)
    private val tugasPembersihanBerkala = object : Runnable {
        override fun run() {
            PengelolaBrankas.sinkronkanAntreanDariPenyimpanan(applicationContext)
            perbaruiTampilanNotifikasi()
            penanganUtama.postDelayed(this, 6 * 60 * 60 * 1000L) // 6 Jam
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // [FITUR: Inisialisasi Layanan] - Menyiapkan kanal notifikasi sistem
    override fun onCreate() {
        super.onCreate()
        pengelolaNotifikasi = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        buatSaluranNotifikasi()
        Log.i(TAG, "Layanan Tangkapan Layar berhasil dibuat")
    }

    // [FITUR: Perintah Memulai/Menghentikan Layanan] - Menerima intent aksi dari Flutter UI
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val aksi = intent?.action ?: AKSI_MULAI

        when (aksi) {
            AKSI_HENTIKAN -> {
                Log.i(TAG, "Menerima perintah penghentian layanan")
                hentikanLayanan()
                return START_NOT_STICKY
            }
            AKSI_MULAI -> {
                mulaiLayananLatarDepan()
            }
        }

        return START_STICKY
    }

    // [FITUR: Mulai Layanan Foreground Android 16] - Mengaktifkan notifikasi foreground, pengamat MediaStore, dan gelembung melayang
    private fun mulaiLayananLatarDepan() {
        layananSedangBerjalan = true

        val jumlahAwal = PengelolaBrankas.sinkronkanAntreanDariPenyimpanan(applicationContext).size
        val notifikasi = bangunNotifikasi(
            "Take & paste Aktif",
            "$jumlahAwal Foto Tersimpan (Masa Simpan 30 Hari)"
        )

        startForeground(ID_NOTIFIKASI_FOREGROUND, notifikasi)

        // Daftarkan pengamat media MediaStore
        if (pengamatMedia == null) {
            pengamatMedia = PengamatMediaScreenshot(applicationContext) { uriScreenshot, jalurScreenshot ->
                val butirBaru = PengelolaBrankas.prosesDanPindahkanScreenshot(
                    applicationContext,
                    uriScreenshot,
                    jalurScreenshot
                )
                if (butirBaru != null) {
                    perbaruiTampilanNotifikasi()
                    MainActivity.kirimPeristiwaAntreanDiperbarui(PengelolaBrankas.dapatkanAntreanSebagaiPeta())
                }
            }

            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                pengamatMedia!!
            )
        }

        // Tampilkan gelembung melayang mini (ala Game Turbo dengan auto-hide)
        JendelaGelembungMelayang.tampilkanGelembung(applicationContext)

        // Jadwalkan pembersihan berkala
        penanganUtama.removeCallbacks(tugasPembersihanBerkala)
        penanganUtama.postDelayed(tugasPembersihanBerkala, 6 * 60 * 60 * 1000L)

        MainActivity.kirimPeristiwaStatusLayanan(true)
    }

    // [FITUR: Pembaruan Tampilan Notifikasi] - Memperbarui status jumlah foto aktif pada bilah notifikasi sistem
    private fun perbaruiTampilanNotifikasi() {
        val jumlahFoto = PengelolaBrankas.dapatkanDaftarAntreanSaatIni().size
        val pesan = "$jumlahFoto Foto Tersimpan (Masa Simpan 30 Hari)"
        perbaruiNotifikasi("Take & paste Berjalan", pesan)
    }

    // [FITUR: Hentikan Layanan] - Melepaskan semua listener, mematikan gelembung, dan membersihkan memori
    private fun hentikanLayanan() {
        layananSedangBerjalan = false
        penanganUtama.removeCallbacks(tugasPembersihanBerkala)

        pengamatMedia?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Pengamat media sudah terlepas: ${e.message}")
            }
        }
        pengamatMedia = null

        JendelaGelembungMelayang.sembunyikanGelembung()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()

        MainActivity.kirimPeristiwaStatusLayanan(false)
    }

    // [FITUR: Kanal Notifikasi Sistem] - Membuat channel notifikasi sesuai standar Android 8.0+
    private fun buatSaluranNotifikasi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val saluran = NotificationChannel(
                ID_SALURAN_NOTIFIKASI,
                "Layanan Pemantauan Take & paste",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status pemantauan screenshot brankas privat"
                setShowBadge(false)
            }
            pengelolaNotifikasi.createNotificationChannel(saluran)
        }
    }

    // [FITUR: Pembina Notifikasi Foreground] - Membuat objek notifikasi status berjalan
    private fun bangunNotifikasi(judul: String, pesan: String): Notification {
        val niatBukaAplikasi = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val niatTertunda = PendingIntent.getActivity(
            this,
            0,
            niatBukaAplikasi,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ID_SALURAN_NOTIFIKASI)
            .setContentTitle(judul)
            .setContentText(pesan)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(niatTertunda)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // [FITUR: Pembaruan Notifikasi Realtime] - Mengupdate teks notifikasi foreground
    private fun perbaruiNotifikasi(judul: String, pesan: String) {
        val notifikasi = bangunNotifikasi(judul, pesan)
        pengelolaNotifikasi.notify(ID_NOTIFIKASI_FOREGROUND, notifikasi)
    }

    override fun onDestroy() {
        hentikanLayanan()
        super.onDestroy()
    }
}
