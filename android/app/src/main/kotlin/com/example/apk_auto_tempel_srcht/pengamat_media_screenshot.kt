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
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

// [FITUR: Deteksi Screenshot Otomatis] - Memantau MediaStore secara realtime untuk mendeteksi screenshot baru, memfilter IS_PENDING, menerapkan debounce 400ms, mencegah rekursi penghapusan, dan memindahkan file ke brankas privat
class PengamatMediaScreenshot(
    private val konteks: Context,
    private val padaScreenshotDideteksi: (Uri, String?) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "PengamatScreenshot"
        private const val JEDA_DEBOUNCE_MS = 400L
        private const val BATAS_USIA_MAKSIMAL_DETIK = 25L // Hanya terima screenshot dalam 25 detik terakhir

        // Kata kunci penanda bahwa gambar merupakan tangkapan layar
        private val KATA_KUNCI_SCREENSHOT = arrayOf(
            "screenshot",
            "tangkapan layar",
            "screen_shot",
            "screencap",
            "capture"
        )

        // [PENCEGAHAN BUG]: Riwayat ID MediaStore yang sudah pernah diproses agar tidak terulang
        private val kumpulanIdTerproses = Collections.synchronizedSet(HashSet<Long>())

        // [PENCEGAHAN BUG]: Menandai saat aplikasi sedang memproses & menghapus file agar tidak terjadi cascading loop
        val sedangMemprosesPenghapusan = AtomicBoolean(false)
    }

    private var waktuTerakhirDiproses = 0L
    private var tugasDebounceTerakhir: Runnable? = null
    private val penanganUtama = Handler(Looper.getMainLooper())
    private val waktuMulaiPemantauanDetik = System.currentTimeMillis() / 1000L

    // [FITUR: Pemicu Perubahan MediaStore] - Dipanggil otomatis oleh sistem Android saat ada media gambar baru
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        // Jika perubahan ini dipicu oleh penghapusan internal aplikasi, abaikan seketika
        if (sedangMemprosesPenghapusan.get()) {
            return
        }

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

    // [FITUR: Kueri MediaStore Gambar Terbaru] - Mengambil dan memvalidasi gambar terbaru yang benar-benar baru diambil
    private fun periksaDanProsesGambarTerbaru() {
        if (sedangMemprosesPenghapusan.get()) return

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
                    val indeksWaktuTambah = kursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                    val idGambar = kursor.getLong(indeksId)
                    val namaGambar = kursor.getString(indeksNama) ?: ""
                    val jalurGambar = kursor.getString(indeksData) ?: ""
                    val waktuTambahDetik = kursor.getLong(indeksWaktuTambah)

                    // [PENCEGAHAN BUG 1]: Cek apakah ID gambar sudah pernah diproses sebelumnya
                    if (kumpulanIdTerproses.contains(idGambar)) {
                        return
                    }

                    // [PENCEGAHAN BUG 2]: Cek umur gambar! Gambar yang diambil beberapa hari lalu WAJIB diabaikan!
                    val waktuSekarangDetik = System.currentTimeMillis() / 1000L
                    val selisihWaktuDetik = waktuSekarangDetik - waktuTambahDetik

                    // Jika gambar dibuat lebih dari 25 detik yang lalu atau sebelum pemantau dimulai, lewati
                    if (selisihWaktuDetik > BATAS_USIA_MAKSIMAL_DETIK || waktuTambahDetik < (waktuMulaiPemantauanDetik - 5L)) {
                        Log.d(TAG, "Abaikan gambar lama (selisih $selisihWaktuDetik detik): $namaGambar")
                        return
                    }

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
                                return
                            }
                        }
                    }

                    if (jalurGambar.contains(PengelolaBrankas.FOLDER_GALERI_HASIL_EDIT, ignoreCase = true) ||
                        namaGambar.contains(PengelolaBrankas.FOLDER_GALERI_HASIL_EDIT, ignoreCase = true)
                    ) {
                        return
                    }

                    // [VALIDASI: Verifikasi Apakah Gambar Merupakan Screenshot Asli]
                    if (apakahGambarScreenshot(namaGambar, jalurGambar)) {
                        kumpulanIdTerproses.add(idGambar)
                        val uriLengkap = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, idGambar)
                        Log.i(TAG, "Screenshot baru segar terdeteksi: $namaGambar ($jalurGambar)")

                        // Bangunkan bilah melayang samping (munculkan gelembung)
                        JendelaGelembungMelayang.bangunkanDariBilahSamping(konteks)

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
