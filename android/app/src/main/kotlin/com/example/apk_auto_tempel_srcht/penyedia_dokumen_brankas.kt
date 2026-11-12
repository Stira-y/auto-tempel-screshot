package com.example.apk_auto_tempel_srcht

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

// [FITUR: Integrasi Pemilih File Dokumen Sistem] - Menyediakan brankas privat sebagai sumber penyimpanan dokumen sistem (DocumentsProvider) agar bisa dipilih di WhatsApp dan aplikasi lain tanpa masuk galeri publik
class PenyediaDokumenBrankas : DocumentsProvider() {

    companion object {
        private const val ID_AKAR_DOKUMEN = "akar_brankas_screenshot"
        private const val ID_DOKUMEN_DIREKTORI_UTAMA = "dokumen_direktori_brankas"

        // Proyeksi kolom baku dokumen
        private val KOLOM_BAKU_DOKUMEN = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )

        // Proyeksi kolom baku akar penyimpanan
        private val KOLOM_BAKU_AKAR = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )
    }

    // [FITUR: Inisialisasi Penyedia Dokumen] - Menyiapkan penyedia saat sistem pertama kali memanggilnya
    override fun onCreate(): Boolean {
        return true
    }

    // [FITUR: Kueri Akar Penyimpanan] - Menampilkan "Brankas Screenshot" di panel samping pemilih berkas Android
    override fun queryRoots(proyeksi: Array<out String>?): Cursor {
        val hasilKursor = MatrixCursor(proyeksi ?: KOLOM_BAKU_AKAR)
        val konteksAplikasi = context ?: return hasilKursor

        val barisAkar = hasilKursor.newRow()
        barisAkar.add(DocumentsContract.Root.COLUMN_ROOT_ID, ID_AKAR_DOKUMEN)
        barisAkar.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ID_DOKUMEN_DIREKTORI_UTAMA)
        barisAkar.add(DocumentsContract.Root.COLUMN_TITLE, "Brankas Screenshot")
        barisAkar.add(DocumentsContract.Root.COLUMN_SUMMARY, "Screenshot aktif sementara (auto-delete)")
        barisAkar.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
                    DocumentsContract.Root.FLAG_SUPPORTS_RECENTS
        )
        barisAkar.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "image/*")
        barisAkar.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
        barisAkar.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, konteksAplikasi.filesDir.freeSpace)

        return hasilKursor
    }

    // [FITUR: Kueri Metadata Dokumen] - Mengambil informasi berkas atau folder brankas berdasarkan ID dokumen
    override fun queryDocument(idDokumen: String?, proyeksi: Array<out String>?): Cursor {
        val hasilKursor = MatrixCursor(proyeksi ?: KOLOM_BAKU_DOKUMEN)
        val konteksAplikasi = context ?: return hasilKursor

        if (idDokumen == null || idDokumen == ID_DOKUMEN_DIREKTORI_UTAMA) {
            val barisFolder = hasilKursor.newRow()
            barisFolder.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ID_DOKUMEN_DIREKTORI_UTAMA)
            barisFolder.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Brankas Screenshot")
            barisFolder.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
            barisFolder.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            barisFolder.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
            barisFolder.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
        } else {
            val berkas = File(PengelolaBrankas.dapatkanDirektoriBrankas(konteksAplikasi), idDokumen)
            if (berkas.exists()) {
                tambahkanBarisBerkas(hasilKursor, berkas)
            }
        }

        return hasilKursor
    }

    // [FITUR: Kueri Anak Dokumen / Isi Brankas] - Menampilkan daftar gambar yang saat ini ada di antrean brankas
    override fun queryChildDocuments(
        idDokumenInduk: String?,
        proyeksi: Array<out String>?,
        urutanSortir: String?
    ): Cursor {
        val hasilKursor = MatrixCursor(proyeksi ?: KOLOM_BAKU_DOKUMEN)
        val konteksAplikasi = context ?: return hasilKursor

        val direktori = PengelolaBrankas.dapatkanDirektoriBrankas(konteksAplikasi)
        val daftarBerkas = direktori.listFiles { b -> b.isFile } ?: emptyArray()

        daftarBerkas.sortByDescending { it.lastModified() }

        for (berkas in daftarBerkas) {
            tambahkanBarisBerkas(hasilKursor, berkas)
        }

        return hasilKursor
    }

    // [FITUR: Kueri Dokumen Terkini] - Menampilkan screenshot terbaru pada tab "Recents" pemilih berkas sistem
    override fun queryRecentDocuments(idAkar: String?, proyeksi: Array<out String>?): Cursor {
        val hasilKursor = MatrixCursor(proyeksi ?: KOLOM_BAKU_DOKUMEN)
        val konteksAplikasi = context ?: return hasilKursor

        val direktori = PengelolaBrankas.dapatkanDirektoriBrankas(konteksAplikasi)
        val daftarBerkas = direktori.listFiles { b -> b.isFile } ?: emptyArray()
        daftarBerkas.sortByDescending { it.lastModified() }

        for (berkas in daftarBerkas.take(10)) {
            tambahkanBarisBerkas(hasilKursor, berkas)
        }

        return hasilKursor
    }

    // [FITUR: Buka Dokumen Berkas] - Mengalirkan data byte berkas langsung ke aplikasi pemanggil (seperti WhatsApp)
    override fun openDocument(
        idDokumen: String?,
        mode: String?,
        sinyalBatal: CancellationSignal?
    ): ParcelFileDescriptor {
        val konteksAplikasi = context ?: throw FileNotFoundException("Konteks aplikasi null")
        val berkas = File(PengelolaBrankas.dapatkanDirektoriBrankas(konteksAplikasi), idDokumen ?: "")

        if (!berkas.exists()) {
            throw FileNotFoundException("Berkas brankas tidak ditemukan: $idDokumen")
        }

        val modeAkses = ParcelFileDescriptor.parseMode(mode ?: "r")
        return ParcelFileDescriptor.open(berkas, modeAkses)
    }

    // [FITUR: Bantu Baris Berkas] - Memasukkan data atribut berkas fisik ke kursor dokumen
    private fun tambahkanBarisBerkas(kursor: MatrixCursor, berkas: File) {
        val baris = kursor.newRow()
        baris.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, berkas.name)
        baris.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, berkas.name)
        baris.add(DocumentsContract.Document.COLUMN_SIZE, berkas.length())
        baris.add(DocumentsContract.Document.COLUMN_MIME_TYPE, tentukanTipeMime(berkas.name))
        baris.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, berkas.lastModified())
        baris.add(
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
        )
    }

    // [FITUR: Penentu Tipe Mime] - Mengidentifikasi format berkas (PNG/JPG)
    private fun tentukanTipeMime(namaBerkas: String): String {
        val ekstensi = MimeTypeMap.getFileExtensionFromUrl(namaBerkas)
        return if (ekstensi != null && ekstensi.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ekstensi.lowercase()) ?: "image/png"
        } else {
            "image/png"
        }
    }
}

