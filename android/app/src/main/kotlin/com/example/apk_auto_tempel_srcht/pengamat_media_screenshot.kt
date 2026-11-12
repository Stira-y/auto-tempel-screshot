package com.example.apk_auto_tempel_srcht

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

// [FITUR: Deteksi Screenshot Otomatis] - Memantau MediaStore secara realtime untuk mendeteksi tangkapan layar baru, memfilter IS_PENDING, menerapkan debounce 400ms, dan memindahkan file ke brankas privat
class PengamatMediaScreenshot(
    private val konteks: Context,
    private val padaScreenshotDideteksi: (Uri, String?) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "PengamatScreenshot"
        private const val JEDA_DEBOUNCE_MS = 400L

        // Kata kunci penanda bahwa gambar merupakan tangkapan layar
        private val KATA_KUNCI_SCREENSHOT = arrayOf(
            "screenshot",
            "tangkapan layar",
            "screen_shot",
            "screencap",
            "capture"
        )
    }

    private var waktuTerakhirDiproses = 0L
    private var tugasDebounceTerakhir: Runnable? = null
    private val penanganUtama = Handler(Looper.getMainLooper())

    // [FITUR: Pemicu Perubahan MediaStore] - Dipanggil otomatis oleh sistem Android saat ada media gambar baru
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        // Batalkan tugas debounce sebelumnya jika ada perubahan cepat beruntun
        tugasDebounceTerakhir?.let { penanganUtama.removeCallbacks(it) }

        val tugasBaru = Runnable {
            val sekarang = System.currentTimeMillis()
            if (sekarang - waktuTerakhirDiproses < JEDA_DEBOUNCE_MS) {
                return@Runnable
            }
            waktuTerakhirDiproses = sekarang
            periksaDanProsesGambarTerbaru()
        }

        tugasDebounceTerakhir = tugasBaru
        penanganUtama.postDelayed(tugasBaru, JEDA_DEBOUNCE_MS)
    }

    // [FITUR: Kueri MediaStore Gambar Terbaru] - Mengambil dan memvalidasi gambar terbaru yang masuk
    private fun periksaDanProsesGambarTerbaru() {
        val proyeksi = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            proyeksi.add(MediaStore.Images.Media.IS_PENDING)
            proyeksi.add(MediaStore.Images.Media.RELATIVE_PATH)
        }

        val uriKueri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val urutanSortir = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            konteks.contentResolver.query(
                uriKueri,
                proyeksi.toTypedArray(),
                null,
                null,
                urutanSortir
            )?.use { kursor ->
                if (kursor.moveToFirst()) {
                    val indeksId = kursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val indeksNama = kursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val indeksData = kursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                    val idGambar = kursor.getLong(indeksId)
                    val namaGambar = kursor.getString(indeksNama) ?: ""
                    val jalurGambar = kursor.getString(indeksData) ?: ""

                    // [VALIDASI: Filter IS_PENDING == 0 di Android 10+]
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val indeksPending = kursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                        if (indeksPending != -1 && kursor.getInt(indeksPending) != 0) {
                            Log.d(TAG, "Gambar masih berstatus IS_PENDING == 1, lewati: $namaGambar")
                            return
                        }
                    }

                    // [VALIDASI: Pengecualian Folder Hasil Edit] - Wajib mengabaikan folder AutoTempelEdit
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val indeksJalurRelatif = kursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                        if (indeksJalurRelatif != -1) {
                            val jalurRelatif = kursor.getString(indeksJalurRelatif) ?: ""
                            if (jalurRelatif.contains(PengelolaBrankas.FOLDER_GALERI_HASIL_EDIT, ignoreCase = true)) {
                                Log.d(TAG, "Abaikan berkas dari jalur relatif edit: $jalurRelatif")
                                return
                            }
                        }
                    }

                    if (jalurGambar.contains(PengelolaBrankas.FOLDER_GALERI_HASIL_EDIT, ignoreCase = true) ||
                        namaGambar.contains(PengelolaBrankas.FOLDER_GALERI_HASIL_EDIT, ignoreCase = true)
                    ) {
                        Log.d(TAG, "Abaikan berkas hasil edit aplikasi: $namaGambar")
                        return
                    }

                    // [VALIDASI: Verifikasi Apakah Gambar Merupakan Screenshot]
                    if (apakahGambarScreenshot(namaGambar, jalurGambar)) {
                        val uriLengkap = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, idGambar)
                        Log.i(TAG, "Tangkapan layar terdeteksi: $namaGambar ($jalurGambar)")
                        padaScreenshotDideteksi.invoke(uriLengkap, jalurGambar)
                    }
                }
            }
        } catch (pengecualian: Exception) {
            Log.e(TAG, "Gagal memindai gambar MediaStore: ${pengecualian.message}")
        }
    }

    // [FITUR: Pengecekan Ciri Screenshot] - Memastikan jalur atau nama berkas cocok dengan pola tangkapan layar Android
    private fun apakahGambarScreenshot(namaBerkas: String, jalurBerkas: String): Boolean {
        val namaKecil = namaBerkas.lowercase()
        val jalurKecil = jalurBerkas.lowercase()

        // Periksa nama berkas
        for (kata in KATA_KUNCI_SCREENSHOT) {
            if (namaKecil.contains(kata)) return true
        }

        // Periksa direktori penyimpanan standar Android
        if (jalurKecil.contains("/screenshots/") ||
            jalurKecil.contains("/screenshot/") ||
            jalurKecil.contains("tangkapan layar")
        ) {
            return true
        }

        return false
    }
}

