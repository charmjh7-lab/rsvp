package com.example.rsvpreader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class FileImporter(private val context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    data class Extracted(val text: String, val sourceType: SourceType, val originalName: String)

    fun extract(uri: Uri): Result<Extracted> = runCatching {
        val name = queryName(uri) ?: "가져온 문서"
        val ext = name.substringAfterLast('.', "").lowercase()
        val type = context.contentResolver.getType(uri).orEmpty().lowercase()
        when {
            ext == "pdf" || type == "application/pdf" -> Extracted(extractPdf(uri), SourceType.PDF, name)
            ext == "epub" || type == "application/epub+zip" -> Extracted(extractEpub(uri), SourceType.EPUB, name)
            ext == "txt" || type.startsWith("text/") || type.isBlank() -> Extracted(extractTxt(uri), SourceType.TXT, name)
            else -> error("지원하지 않는 파일 형식입니다. TXT, PDF, EPUB 파일을 선택해 주세요.")
        }
    }

    private fun extractTxt(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("파일을 열 수 없습니다.")
        return decodeText(bytes)
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun extractPdf(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("PDF 파일을 열 수 없습니다.")
        val text = PDDocument.load(bytes).use { document ->
            PDFTextStripper().apply { sortByPosition = true }.getText(document)
        }
        if (text.isBlank()) error("이 PDF에서는 텍스트를 추출할 수 없습니다. 스캔 이미지 PDF의 OCR은 현재 버전에서 지원하지 않습니다.")
        return text
    }

    private fun extractEpub(uri: Uri): String {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("EPUB 파일을 열 수 없습니다.")

        val container = entries["META-INF/container.xml"] ?: error("올바른 EPUB 구조를 찾지 못했습니다.")
        val containerDoc = parseXml(container)
        val rootfile = containerDoc.getElementsByTagName("rootfile").item(0) as? Element
            ?: error("EPUB 패키지 경로를 찾지 못했습니다.")
        val opfPath = rootfile.getAttribute("full-path")
        val opfBytes = entries[opfPath] ?: error("EPUB 패키지 파일을 찾지 못했습니다.")
        val opfDoc = parseXml(opfBytes)
        val opfDir = opfPath.substringBeforeLast('/', "")

        val manifest = mutableMapOf<String, Pair<String, String>>()
        val itemNodes = opfDoc.getElementsByTagName("item")
        for (i in 0 until itemNodes.length) {
            val el = itemNodes.item(i) as? Element ?: continue
            manifest[el.getAttribute("id")] = el.getAttribute("href") to el.getAttribute("media-type")
        }

        val ordered = mutableListOf<String>()
        val refs = opfDoc.getElementsByTagName("itemref")
        for (i in 0 until refs.length) {
            val el = refs.item(i) as? Element ?: continue
            val idref = el.getAttribute("idref")
            val item = manifest[idref] ?: continue
            if (item.second.contains("html") || item.first.endsWith(".xhtml", true) || item.first.endsWith(".html", true)) {
                val path = resolvePath(opfDir, item.first)
                val bytes = entries[path] ?: continue
                val html = bytes.toString(Charset.forName("UTF-8"))
                val plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                if (plain.isNotBlank()) ordered += plain
            }
        }
        if (ordered.isEmpty()) error("EPUB 본문을 추출할 수 없습니다.")
        return ordered.joinToString("\n\n")
    }

    private fun resolvePath(baseDir: String, href: String): String {
        val stack = mutableListOf<String>()
        (if (baseDir.isBlank()) href else "$baseDir/$href").split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack += part
            }
        }
        return stack.joinToString("/")
    }

    private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun queryName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment
    }
}
