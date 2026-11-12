package com.example.apk_auto_tempel_srcht

import android.content.ClipData
import android.content.ClipDescription
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

// [FITUR: Pengelola Brankas Privat] - Mengelola penyimpanan tak terbatas dengan retensi 30 hari, pembersihan berkas usang, dan integrasi clipboard
object PengelolaBrankas {

    private const val TAG = "PengelolaBrankas"
    private const val NAMA_DIREKTORI_BRANKAS = "brankas_screenshot"
    const val FOLDER_GALERI_HASIL_EDIT = "AutoTempelEdit"

    // [FITUR: Masa Retensi 30 Hari] - Berkas yang berusia lebih dari 30 hari otomatis dibersihkan
    const val MASA_SIMPAN_30_HARI_MS = 30L * 24 * 60 * 60 * 1000L

    // [FITUR: Daftar Antrean Memori Tak Terbatas] - Menyimpan riwayat tangkapan layar aktif
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

    // [FITUR: Sinkronisasi Antrean & Pembersihan 30 Hari] - Membaca berkas brankas dan menghapus berkas yang lebih dari 30 hari
    fun sinkronkanAntreanDariPenyimpanan(konteks: Context): List<ButirScreenshot> {
        val direktori = dapatkanDirektoriBrankas(konteks)
        val daftarBerkas = direktori.listFiles { berkas ->
            berkas.isFile && (berkas.name.endsWith(".png", true) || berkas.name.endsWith(".jpg", true))
        } ?: emptyArray()

        val batasWaktuKedaluwarsa = System.currentTimeMillis() - MASA_SIMPAN_30_HARI_MS
        val berkasValid = mutableListOf<File>()

        // Hapus file yang sudah berusia lebih dari 30 hari
        for (berkas in daftarBerkas) {
            if (berkas.lastModified() < batasWaktuKedaluwarsa) {
                Log.i(TAG, "Menghapus screenshot usang (>30 hari): ${berkas.name}")
                try {
                    berkas.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal menghapus berkas usang: ${e.message}")
                }
            } else {
                berkasValid.add(berkas)
            }
        }

        // Urutkan berdasarkan waktu terakhir diubah (terbaru di paling atas)
        berkasValid.sortByDescending { it.lastModified() }

        synchronized(daftarAntrean) {
            daftarAntrean.clear()
            for (berkas in berkasValid) {
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
        }

        beriTahuPerubahanAntrean()
        return dapatkanDaftarAntreanSaatIni()
    }

    // [FITUR: Dapatkan Salinan Antrean Saat Ini] - Mengembalikan daftar butir screenshot yang aktif (tanpa batasan)
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

            // [PENCEGAHAN BUG]: Aktifkan flag pencegahan rekursi saat menghapus file asli dari MediaStore
            PengamatMediaScreenshot.sedangMemprosesPenghapusan.set(true)
            try {
                hapusBerkasAsliSecaraSenyap(konteks, uriAsal, jalurAsal)
            } finally {
                // Beri jeda kecil agar ContentObserver mengabaikan event penghapusan
                penanganUtama.postDelayed({
                    PengamatMediaScreenshot.sedangMemprosesPenghapusan.set(false)
                }, 500)
            }

            // Buat butir screenshot baru
            val butirBaru = ButirScreenshot(
                idBerkas = berkasTujuan.name,
                namaBerkas = berkasTujuan.name,
                jalurBerkas = berkasTujuan.absolutePath,
                waktuDibuatMs = berkasTujuan.lastModified(),
                ukuranBerkasByte = berkasTujuan.length()
            )

            // Masukkan ke antrean paling depan (indeks 0) tanpa batasan 10 gambar
            synchronized(daftarAntrean) {
                daftarAntrean.add(0, butirBaru)
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

    // [FITUR: Hapus File Asli Senyap] - Menghapus file dari MediaStore dan File System tanpa dialog konfirmasi
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
                @Suppress("DEPRECATION")
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
                            Toast.makeText(konteks, "Foto berhasil disalin ke papan klip!", Toast.LENGTH_SHORT).show()
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

    // [FITUR: Salin Banyak Gambar Sekaligus ke Papan Klip] - Menyalin sejumlah gambar terpilih ke clipboard (1 gambar = 1 item, banyak gambar = multi-item dengan izin URI penuh)
    fun salinBanyakGambarKePapanKlip(
        konteks: Context,
        daftarJalur: List<String>,
        tampilkanNotifikasi: Boolean = true
    ): Boolean {
        if (daftarJalur.isEmpty()) return false
        val otoritas = "${konteks.packageName}.penyedia_berkas"
        val daftarUri = ArrayList<Uri>()

        for (jalur in daftarJalur) {
            val berkas = File(jalur)
            if (berkas.exists()) {
                val uriKonten = FileProvider.getUriForFile(konteks, otoritas, berkas)
                daftarUri.add(uriKonten)
            }
        }

        if (daftarUri.isEmpty()) return false

        // [FITUR: Pemberian Izin URI ke Aplikasi Target] - Memastikan aplikasi seperti WhatsApp, Telegram, Google Docs dapat membaca seluruh berkas URI
        val daftarPaketPopuler = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.plus",
            "com.google.android.apps.messaging",
            "com.google.android.apps.docs",
            "com.facebook.orca",
            "com.instagram.android"
        )
        for (paket in daftarPaketPopuler) {
            for (uri in daftarUri) {
                try {
                    konteks.grantUriPermission(paket, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
        }

        try {
            JendelaGelembungMelayang.tampilkanOverlayFokusSesaat(konteks) {
                try {
                    val manajerPapanKlip = konteks.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    
                    val klip: ClipData = if (daftarUri.size == 1) {
                        // Jika hanya 1 gambar yang dipilih: salin tepat 1 item ke clipboard
                        ClipData.newUri(konteks.contentResolver, "Screenshot", daftarUri[0])
                    } else {
                        // Jika 3 (atau lebih) gambar yang dipilih: buat ClipData multi-item dengan semua tipe mime gambar
                        val tipeMime = arrayOf("image/png", "image/jpeg", "image/*")
                        val deskripsi = ClipDescription("Screenshot Terpilih", tipeMime)
                        val klipMulti = ClipData(deskripsi, ClipData.Item(daftarUri[0]))
                        for (i in 1 until daftarUri.size) {
                            klipMulti.addItem(ClipData.Item(daftarUri[i]))
                        }
                        klipMulti
                    }
                    manajerPapanKlip.setPrimaryClip(klip)

                    if (tampilkanNotifikasi) {
                        penanganUtama.post {
                            val pesan = if (daftarUri.size == 1) {
                                "1 foto berhasil disalin ke papan klip!"
                            } else {
                                "${daftarUri.size} foto berhasil disalin ke papan klip!"
                            }
                            Toast.makeText(konteks, pesan, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal menyalin gambar ke clipboard: ${e.message}")
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Terjadi kesalahan saat salin banyak: ${e.message}")
            return false
        }
    }

    // [FITUR: Bagikan Gambar Terpilih (Multi-Share)] - Membagikan beberapa gambar pilihan saja ke WhatsApp atau aplikasi lain
    fun bagikanBanyakGambar(konteks: Context, daftarJalur: List<String>): Boolean {
        if (daftarJalur.isEmpty()) {
            Toast.makeText(konteks, "Pilih minimal 1 gambar terlebih dahulu!", Toast.LENGTH_SHORT).show()
            return false
        }

        val otoritas = "${konteks.packageName}.penyedia_berkas"
        val daftarUri = ArrayList<Uri>()

        for (jalur in daftarJalur) {
            val berkas = File(jalur)
            if (berkas.exists()) {
                val uriKonten = FileProvider.getUriForFile(konteks, otoritas, berkas)
                daftarUri.add(uriKonten)
            }
        }

        if (daftarUri.isEmpty()) return false

        val niatKirim = Intent().apply {
            if (daftarUri.size == 1) {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, daftarUri[0])
            } else {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, daftarUri)
            }
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val judul = if (daftarUri.size == 1) "Kirim 1 Foto Terpilih" else "Kirim ${daftarUri.size} Foto Terpilih"
        val niatPemilih = Intent.createChooser(niatKirim, judul).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        konteks.startActivity(niatPemilih)
        return true
    }

    // [FITUR: Simpan Satu Gambar dari Brankas Langsung ke Galeri Publik] - Opsi tekan lama: simpan ke galeri tanpa edit
    fun simpanGambarKeGaleriPublik(konteks: Context, jalurBerkas: String): Boolean {
        val berkas = File(jalurBerkas)
        if (!berkas.exists()) {
            penanganUtama.post {
                Toast.makeText(konteks, "Berkas gambar tidak ditemukan!", Toast.LENGTH_SHORT).show()
            }
            return false
        }

        try {
            val dataByte = berkas.readBytes()
            val stempelWaktu = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val namaBerkasSimpan = "AutoTempel_${stempelWaktu}_${berkas.name}"
            val uriHasil = simpanHasilEditKeGaleri(konteks, dataByte, namaBerkasSimpan)
            val sukses = uriHasil != null

            if (sukses) {
                penanganUtama.post {
                    Toast.makeText(konteks, "Foto berhasil disimpan ke galeri!", Toast.LENGTH_SHORT).show()
                }
            }
            return sukses
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan gambar langsung ke galeri: ${e.message}")
            return false
        }
    }

    // [FITUR: Bagikan Seluruh Antrean (Multi-Share)] - Membagikan seluruh gambar sekaligus ke WhatsApp/aplikasi lain
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

        val niatPemilih = Intent.createChooser(niatKirim, "Kirim ${daftarUri.size} Foto Screenshot").apply {
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
