package com.example.apk_auto_tempel_srcht

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat

// [FITUR: Layanan Latar Depan Pemantauan Screenshot] - Menjaga aplikasi tetap aktif di latar belakang dengan foreground service khusus Android 16 untuk memantau screenshot dan mengelola timer kadaluarsa
class LayananTangkapanLayar : Service() {

    companion object {
        private const val TAG = "LayananScreenshot"
        private const val ID_SALURAN_NOTIFIKASI = "saluran_layanan_tangkapan_layar"
        private const val ID_NOTIFIKASI_FOREGROUND = 1001

        const val AKSI_MULAI = "AKSI_MULAI"
        const val AKSI_HENTIKAN = "AKSI_HENTIKAN"
        const val AKSI_ATUR_DURASI = "AKSI_ATUR_DURASI"
        const val EKSTRA_DURASI_DETIK = "EKSTRA_DURASI_DETIK"

        private const val NAMA_PREFERENSI = "preferensi_auto_tempel"
        private const val KUNCI_DURASI_TIMER = "durasi_timer_detik"
        const val DURASI_DEFAULT_DETIK = 180 // 3 Menit

        var layananSedangBerjalan = false
            private set

        var sisaWaktuDetikSaatIni = DURASI_DEFAULT_DETIK
            private set
    }

    private var pengamatMedia: PengamatMediaScreenshot? = null
    private var penghitungMundur: CountDownTimer? = null
    private var durasiTimerMaksimalDetik = DURASI_DEFAULT_DETIK
    private lateinit var preferensi: SharedPreferences
    private lateinit var pengelolaNotifikasi: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    // [FITUR: Inisialisasi Layanan] - Menyiapkan kanal notifikasi dan preferensi lokal
    override fun onCreate() {
        super.onCreate()
        preferensi = getSharedPreferences(NAMA_PREFERENSI, Context.MODE_PRIVATE)
        durasiTimerMaksimalDetik = preferensi.getInt(KUNCI_DURASI_TIMER, DURASI_DEFAULT_DETIK)
        sisaWaktuDetikSaatIni = durasiTimerMaksimalDetik
        pengelolaNotifikasi = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        buatSaluranNotifikasi()
        Log.i(TAG, "Layanan Tangkapan Layar berhasil dibuat")
    }

    // [FITUR: Perintah Memulai/Mengatur Layanan] - Menerima intent aksi dari Flutter UI
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val aksi = intent?.action ?: AKSI_MULAI

        when (aksi) {
            AKSI_HENTIKAN -> {
                Log.i(TAG, "Menerima perintah penghentian layanan")
                hentikanLayanan()
                return START_NOT_STICKY
            }
            AKSI_ATUR_DURASI -> {
                val durasiBaru = intent?.getIntExtra(EKSTRA_DURASI_DETIK, DURASI_DEFAULT_DETIK) ?: DURASI_DEFAULT_DETIK
                aturDurasiTimerDetik(durasiBaru)
                return START_STICKY
            }
            AKSI_MULAI -> {
                val durasiKustom = intent?.getIntExtra(EKSTRA_DURASI_DETIK, durasiTimerMaksimalDetik) ?: durasiTimerMaksimalDetik
                durasiTimerMaksimalDetik = durasiKustom
                mulaiLayananLatarDepan()
            }
        }

        return START_STICKY
    }

    // [FITUR: Mulai Layanan Foreground Android 16] - Mengaktifkan notifikasi foreground, pengamat MediaStore, dan gelembung melayang
    private fun mulaiLayananLatarDepan() {
        layananSedangBerjalan = true

        val notifikasi = bangunNotifikasi("Layanan Aktif", "Memantau tangkapan layar otomatis...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 14 / 16 Foreground Service Special Use
            startForeground(ID_NOTIFIKASI_FOREGROUND, notifikasi)
        } else {
            startForeground(ID_NOTIFIKASI_FOREGROUND, notifikasi)
        }

        // Daftarkan pengamat media MediaStore
        if (pengamatMedia == null) {
            pengamatMedia = PengamatMediaScreenshot(applicationContext) { uriScreenshot, jalurScreenshot ->
                val butirBaru = PengelolaBrankas.prosesDanPindahkanScreenshot(
                    applicationContext,
                    uriScreenshot,
                    jalurScreenshot
                )
                if (butirBaru != null) {
                    // Reset hitung mundur setiap kali ada screenshot baru masuk
                    mulaiHitungMundur(durasiTimerMaksimalDetik)
                    MainActivity.kirimPeristiwaAntreanDiperbarui(PengelolaBrankas.dapatkanAntreanSebagaiPeta())
                }
            }

            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                pengamatMedia!!
            )
        }

        // Tampilkan gelembung melayang mini
        JendelaGelembungMelayang.tampilkanGelembung(applicationContext)

        // Sinkronkan antrean lokal dan mulai hitung mundur
        PengelolaBrankas.sinkronkanAntreanDariPenyimpanan(applicationContext)
        mulaiHitungMundur(durasiTimerMaksimalDetik)

        MainActivity.kirimPeristiwaStatusLayanan(true)
    }

    // [FITUR: Pengaturan Durasi Timer Kadaluarsa] - Memperbarui durasi kadaluarsa secara realtime dari Flutter
    fun aturDurasiTimerDetik(detikBaru: Int) {
        durasiTimerMaksimalDetik = if (detikBaru > 0) detikBaru else DURASI_DEFAULT_DETIK
        preferensi.edit().putInt(KUNCI_DURASI_TIMER, durasiTimerMaksimalDetik).apply()
        mulaiHitungMundur(durasiTimerMaksimalDetik)
        Log.i(TAG, "Durasi timer diperbarui menjadi: $durasiTimerMaksimalDetik detik")
    }

    // [FITUR: Hitung Mundur Kadaluarsa Otomatis] - Menghitung sisa waktu sebelum brankas dibersihkan dan clipboard dikosongkan
    private fun mulaiHitungMundur(detikAwal: Int) {
        penghitungMundur?.cancel()
        sisaWaktuDetikSaatIni = detikAwal

        val totalMs = detikAwal * 1000L
        penghitungMundur = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                sisaWaktuDetikSaatIni = (millisUntilFinished / 1000L).toInt()
                val jumlahAntrean = PengelolaBrankas.dapatkanDaftarAntreanSaatIni().size
                val formatMenit = String.format("%02d:%02d", sisaWaktuDetikSaatIni / 60, sisaWaktuDetikSaatIni % 60)

                // Perbarui notifikasi foreground
                val teksNotifikasi = "Sisa Waktu: $formatMenit | $jumlahAntrean/10 Gambar Aktif"
                perbaruiNotifikasi("Auto-Tempel Berjalan", teksNotifikasi)

                // Beritahu Flutter UI
                MainActivity.kirimPeristiwaDetikBerkurang(sisaWaktuDetikSaatIni)
            }

            override fun onFinish() {
                sisaWaktuDetikSaatIni = 0
                Log.i(TAG, "Timer kadaluarsa! Mengosongkan brankas dan papan klip...")

                // [FITUR: Pembersihan Total Saat Kadaluarsa]
                PengelolaBrankas.kosongkanBrankas(applicationContext)
                MainActivity.kirimPeristiwaTimerKadaluarsa()

                val formatMenitDefault = String.format("%02d:%02d", durasiTimerMaksimalDetik / 60, durasiTimerMaksimalDetik % 60)
                perbaruiNotifikasi(
                    "Brankas Dikosongkan",
                    "Waktu habis ($formatMenitDefault). Siap memantau screenshot baru."
                )

                // Mulai ulang siklus timer baru dalam mode siaga
                mulaiHitungMundur(durasiTimerMaksimalDetik)
            }
        }.start()
    }

    // [FITUR: Hentikan Layanan] - Melepaskan semua listener, mematikan gelembung, dan membersihkan memori
    private fun hentikanLayanan() {
        layananSedangBerjalan = false
        penghitungMundur?.cancel()
        penghitungMundur = null

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
                "Layanan Pemantauan Auto-Tempel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status pemantauan screenshot dan hitung mundur kadaluarsa"
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

    // [FITUR: Pembaruan Notifikasi Realtime] - Mengupdate isi teks notifikasi foreground
    private fun perbaruiNotifikasi(judul: String, pesan: String) {
        val notifikasi = bangunNotifikasi(judul, pesan)
        pengelolaNotifikasi.notify(ID_NOTIFIKASI_FOREGROUND, notifikasi)
    }

    override fun onDestroy() {
        hentikanLayanan()
        super.onDestroy()
    }
}
