package com.example.apk_auto_tempel_srcht

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// [FITUR: Data Model Item Screenshot] - Merepresentasikan data berkas tangkapan layar di dalam brankas
data class ButirScreenshot(
    val idBerkas: String,
    val namaBerkas: String,
    val jalurBerkas: String,
    val waktuDibuatMs: Long,
    val ukuranBerkasByte: Long
)

// [FITUR: Pengelola Brankas Privat] - Mengelola penyimpanan berkas lokal, antrean 10 gambar, pembersihan otomatis, dan clipboard
object PengelolaBrankas {

    private const val TAG = "PengelolaBrankas"
    private const val NAMA_DIREKTORI_BRANKAS = "brankas_screenshot"
    private const val BATAS_MAKSIMAL_ANTREAN = 10
    const val FOLDER_GALERI_HASIL_EDIT = "AutoTempelEdit"

    // [FITUR: Daftar Antrean Memori] - Menyimpan hingga 10 riwayat tangkapan layar aktif
    private val daftarAntrean = mutableListOf<ButirScreenshot>()
    private var pendengarPerubahanAntrean: ((List<ButirScreenshot>) -> Unit)? = null
    private val penanganUtama = Handler(Looper.getMainLooper())

    // [FITUR: Pendaftaran Pendengar Antrean] - Mendaftarkan callback saat antrean berubah
    fun aturPendengarPerubahan(pendengar: (List<ButirScreenshot>) -> Unit) {
        pendengarPerubahanAntrean = pendengar
    }

    // [FITUR: Direktori Brankas Privat] - Mengambil folder penyimpanan privat lokal aplikasi
    fun dapatkanDirektoriBrankas(konteks: Context): File {
        val direktori = File(konteks.filesDir, NAMA_DIREKTORI_BRANKAS)
        if (!direktori.exists()) {
            direktori.mkdirs()
        }
        return direktori
    }

    // [FITUR: Sinkronisasi Antrean Lokal] - Membaca berkas fisik yang ada di brankas ke dalam antrean memori
    fun sinkronkanAntreanDariPenyimpanan(konteks: Context): List<ButirScreenshot> {
        val direktori = dapatkanDirektoriBrankas(konteks)
        val daftarBerkas = direktori.listFiles { berkas ->
            berkas.isFile && (berkas.name.endsWith(".png", true) || berkas.name.endsWith(".jpg", true))
        } ?: emptyArray()

        // Urutkan berdasarkan waktu terakhir diubah (terbaru di depan)
        daftarBerkas.sortByDescending { it.lastModified() }

        synchronized(daftarAntrean) {
            daftarAntrean.clear()
            for (berkas in daftarBerkas.take(BATAS_MAKSIMAL_ANTREAN)) {
                daftarAntrean.add(
                    ButirScreenshot(
                        idBerkas = berkas.name,
                        namaBerkas = berkas.name,
                        jalurBerkas = berkas.absolutePath,
                        waktuDibuatMs = berkas.lastModified(),
                        ukuranBerkasByte = berkas.length()
                    )
                )
            }
            // Hapus berkas berlebih jika melebihi batas 10
            if (daftarBerkas.size > BATAS_MAKSIMAL_ANTREAN) {
                for (indeks in BATAS_MAKSIMAL_ANTREAN until daftarBerkas.size) {
                    daftarBerkas[indeks].delete()
                }
            }
        }

        beriTahuPerubahanAntrean()
        return dapatkanDaftarAntreanSaatIni()
    }

    // [FITUR: Dapatkan Salinan Antrean Saat Ini] - Mengembalikan daftar butir screenshot yang aktif
    fun dapatkanDaftarAntreanSaatIni(): List<ButirScreenshot> {
        synchronized(daftarAntrean) {
            return ArrayList(daftarAntrean)
        }
    }

    // [FITUR: Konversi Antrean ke Map] - Mempermudah serialisasi ke platform channel Flutter
    fun dapatkanAntreanSebagaiPeta(): List<Map<String, Any>> {
        return dapatkanDaftarAntreanSaatIni().map { butir ->
            mapOf(
                "idBerkas" to butir.idBerkas,
                "namaBerkas" to butir.namaBerkas,
                "jalurBerkas" to butir.jalurBerkas,
                "waktuDibuatMs" to butir.waktuDibuatMs,
                "ukuranBerkasByte" to butir.ukuranBerkasByte
            )
        }
    }

    // [FITUR: Pindahkan Screenshot ke Brankas] - Menyalin gambar mentah ke brankas lalu menghapus file asli dari galeri
    fun prosesDanPindahkanScreenshot(
        konteks: Context,
        uriAsal: Uri?,
        jalurAsal: String?
    ): ButirScreenshot? {
        // [VALIDASI: Pengecualian Folder Edit] - Pastikan hasil simpanan editor TIDAK ikut terpindah/terhapus
        if (jalurAsal != null && jalurAsal.contains(FOLDER_GALERI_HASIL_EDIT, ignoreCase = true)) {
            Log.d(TAG, "Mengabaikan gambar dari folder $FOLDER_GALERI_HASIL_EDIT: $jalurAsal")
            return null
        }

        try {
            val stempelWaktu = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val namaBerkasTujuan = "tangkapan_$stempelWaktu.png"
            val berkasTujuan = File(dapatkanDirektoriBrankas(konteks), namaBerkasTujuan)

            var aliranMasuk: InputStream? = null
            if (uriAsal != null) {
                aliranMasuk = konteks.contentResolver.openInputStream(uriAsal)
            } else if (jalurAsal != null) {
                val berkasAsal = File(jalurAsal)
                if (berkasAsal.exists()) {
                    aliranMasuk = berkasAsal.inputStream()
                }
            }

            if (aliranMasuk == null) {
                Log.e(TAG, "Gagal membuka aliran input untuk screenshot")
                return null
            }

            // Salin berkas ke brankas privat aplikasi
            FileOutputStream(berkasTujuan).use { aliranKeluar ->
                aliranMasuk.copyTo(aliranKeluar)
            }
            aliranMasuk.close()

            // [FITUR: Hapus Senyap File Asli dari Galeri] - Menggunakan akses file penuh agar galeri HP tetap bersih
            hapusBerkasAsliSecaraSenyap(konteks, uriAsal, jalurAsal)

            // Buat butir screenshot baru
            val butirBaru = ButirScreenshot(
                idBerkas = berkasTujuan.name,
                namaBerkas = berkasTujuan.name,
                jalurBerkas = berkasTujuan.absolutePath,
                waktuDibuatMs = berkasTujuan.lastModified(),
                ukuranBerkasByte = berkasTujuan.length()
            )

            // Masukkan ke antrean paling depan (indeks 0)
            synchronized(daftarAntrean) {
                daftarAntrean.add(0, butirBaru)
                // Jika melebihi batas 10 gambar, hapus yang paling usang
                while (daftarAntrean.size > BATAS_MAKSIMAL_ANTREAN) {
                    val butirUsang = daftarAntrean.removeAt(daftarAntrean.size - 1)
                    val berkasUsang = File(butirUsang.jalurBerkas)
                    if (berkasUsang.exists()) {
                        berkasUsang.delete()
                    }
                }
            }

            beriTahuPerubahanAntrean()

            // [FITUR: Auto Tempel ke Clipboard] - Salin gambar baru ke clipboard sistem seketika
            salinGambarKePapanKlip(konteks, butirBaru.jalurBerkas, tampilkanNotifikasi = false)

            return butirBaru
        } catch (pengecualian: Exception) {
            Log.e(TAG, "Terjadi kesalahan saat memindahkan screenshot ke brankas: ${pengecualian.message}")
            return null
        }
    }

    // [FITUR: Hapus File Asli Senyap] - Menghapus file dari MediaStore dan File System tanpa pop-up dialog
    private fun hapusBerkasAsliSecaraSenyap(konteks: Context, uriAsal: Uri?, jalurAsal: String?) {
        try {
            if (uriAsal != null) {
                konteks.contentResolver.delete(uriAsal, null, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghapus via content resolver: ${e.message}")
        }

        try {
            if (jalurAsal != null) {
                val berkasFisik = File(jalurAsal)
                if (berkasFisik.exists()) {
                    berkasFisik.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghapus via file system: ${e.message}")
        }
    }

    // [FITUR: Hapus Satu Gambar dari Brankas] - Menghapus gambar tertentu dari antrean dan disk
    fun hapusGambarDariAntrean(konteks: Context, jalurBerkas: String): Boolean {
        synchronized(daftarAntrean) {
            val indeks = daftarAntrean.indexOfFirst { it.jalurBerkas == jalurBerkas }
            if (indeks != -1) {
                val butir = daftarAntrean.removeAt(indeks)
                val berkas = File(butir.jalurBerkas)
                if (berkas.exists()) {
                    berkas.delete()
                }
                beriTahuPerubahanAntrean()
                return true
            }
        }
        val berkasSisa = File(jalurBerkas)
        if (berkasSisa.exists()) {
            berkasSisa.delete()
        }
        return false
    }

    // [FITUR: Kosongkan Seluruh Brankas] - Menghapus seluruh berkas sementara dan mengosongkan antrean
    fun kosongkanBrankas(konteks: Context) {
        val direktori = dapatkanDirektoriBrankas(konteks)
        val daftarBerkas = direktori.listFiles()
        if (daftarBerkas != null) {
            for (berkas in daftarBerkas) {
                try {
                    berkas.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal menghapus berkas brankas: ${berkas.name}")
                }
            }
        }

        synchronized(daftarAntrean) {
            daftarAntrean.clear()
        }

        // Kosongkan clipboard sistem juga
        try {
            val manajerPapanKlip = konteks.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manajerPapanKlip.clearPrimaryClip()
            } else {
                manajerPapanKlip.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mengosongkan clipboard: ${e.message}")
        }

        beriTahuPerubahanAntrean()
    }

    // [FITUR: Salin Gambar ke Papan Klip] - Menyalin gambar ke clipboard sistem dengan bypass window overlay Android 16
    fun salinGambarKePapanKlip(konteks: Context, jalurBerkas: String, tampilkanNotifikasi: Boolean = true): Boolean {
        val berkas = File(jalurBerkas)
        if (!berkas.exists()) {
            Log.e(TAG, "Berkas tidak ditemukan untuk disalin ke clipboard: $jalurBerkas")
            return false
        }

        try {
            // [BYPASS CLIPBOARD ANDROID 16]: Munculkan overlay transparan 1x1 sesaat agar memiliki window focus
            JendelaGelembungMelayang.tampilkanOverlayFokusSesaat(konteks) {
                try {
                    val otoritas = "${konteks.packageName}.penyedia_berkas"
                    val uriKonten = FileProvider.getUriForFile(konteks, otoritas, berkas)

                    val manajerPapanKlip = konteks.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val klip = ClipData.newUri(konteks.contentResolver, "Screenshot", uriKonten)
                    manajerPapanKlip.setPrimaryClip(klip)

                    if (tampilkanNotifikasi) {
                        penanganUtama.post {
                            Toast.makeText(konteks, "Screenshot tersalin ke clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal menyalin ke clipboard: ${e.message}")
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Terjadi kesalahan saat memicu salin clipboard: ${e.message}")
            return false
        }
    }

    // [FITUR: Bagikan Seluruh Antrean (Multi-Share)] - Membagikan hingga 10 gambar sekaligus ke WhatsApp/aplikasi lain
    fun bagikanSemuaAntrean(konteks: Context): Boolean {
        val antrean = dapatkanDaftarAntreanSaatIni()
        if (antrean.isEmpty()) {
            Toast.makeText(konteks, "Antrean screenshot masih kosong!", Toast.LENGTH_SHORT).show()
            return false
        }

        val otoritas = "${konteks.packageName}.penyedia_berkas"
        val daftarUri = ArrayList<Uri>()

        for (butir in antrean) {
            val berkas = File(butir.jalurBerkas)
            if (berkas.exists()) {
                val uriKonten = FileProvider.getUriForFile(konteks, otoritas, berkas)
                daftarUri.add(uriKonten)
            }
        }

        if (daftarUri.isEmpty()) {
            return false
        }

        val niatKirim = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, daftarUri)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val niatPemilih = Intent.createChooser(niatKirim, "Kirim ${daftarUri.size} Screenshot").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        konteks.startActivity(niatPemilih)
        return true
    }

    // [FITUR: Simpan Hasil Edit ke Galeri] - Mengekspor gambar yang sudah di-crop atau dicoret dari brankas privat ke galeri publik HP via MediaStore
    fun simpanHasilEditKeGaleri(konteks: Context, dataByte: ByteArray, namaBerkasKustom: String?): String? {
        try {
            val stempelWaktu = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val namaBerkas = namaBerkasKustom ?: "AutoTempel_$stempelWaktu.png"

            val nilaiKonten = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, namaBerkas)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER_GALERI_HASIL_EDIT")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uriPenyisipan = konteks.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                nilaiKonten
            ) ?: return null

            konteks.contentResolver.openOutputStream(uriPenyisipan)?.use { aliranKeluar ->
                aliranKeluar.write(dataByte)
                aliranKeluar.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                nilaiKonten.clear()
                nilaiKonten.put(MediaStore.Images.Media.IS_PENDING, 0)
                konteks.contentResolver.update(uriPenyisipan, nilaiKonten, null, null)
            }

            // Salin hasil simpanan ke clipboard juga
            try {
                val manajerPapanKlip = konteks.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val klip = ClipData.newUri(konteks.contentResolver, "Screenshot Edit", uriPenyisipan)
                manajerPapanKlip.setPrimaryClip(klip)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menyalin hasil edit ke clipboard: ${e.message}")
            }

            penanganUtama.post {
                Toast.makeText(konteks, "Hasil edit tersimpan di Galeri: Pictures/$FOLDER_GALERI_HASIL_EDIT", Toast.LENGTH_LONG).show()
            }

            return uriPenyisipan.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan hasil edit ke galeri: ${e.message}")
            return null
        }
    }

    // [FITUR: Pemicu Notifikasi Perubahan] - Memberitahu pemantau antrean saat ada screenshot baru/dihapus
    private fun beriTahuPerubahanAntrean() {
        val salinanAntrean = dapatkanDaftarAntreanSaatIni()
        penanganUtama.post {
            pendengarPerubahanAntrean?.invoke(salinanAntrean)
            JendelaGelembungMelayang.perbaruiTampilanAntrean(salinanAntrean)
        }
    }
}

