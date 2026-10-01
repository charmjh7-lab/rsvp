package com.example.rsvpreader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class FileImporter(private val context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    data class Extracted(
        val text: String,
        val sourceType: SourceType,
        val originalName: String,
        val toc: List<ImportedTocEntry> = emptyList()
    )

    private data class ExtractedBody(
        val text: String,
        val toc: List<ImportedTocEntry> = emptyList()
    )

    private data class EpubManifestItem(
        val href: String,
        val mediaType: String,
        val properties: String
    )

    fun extract(uri: Uri): Result<Extracted> = runCatching {
        val name = queryName(uri) ?: "가져온 문서"
        val ext = name.substringAfterLast('.', "").lowercase()
        val type = context.contentResolver.getType(uri).orEmpty().lowercase()
        when {
            ext == "pdf" || type == "application/pdf" -> extractPdf(uri).let { Extracted(it.text, SourceType.PDF, name, it.toc) }
            ext == "epub" || type == "application/epub+zip" -> extractEpub(uri).let { Extracted(it.text, SourceType.EPUB, name, it.toc) }
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

        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            val koreanFallback = when {
                Charset.isSupported("MS949") -> Charset.forName("MS949")
                Charset.isSupported("EUC-KR") -> Charset.forName("EUC-KR")
                else -> Charsets.UTF_8
            }
            bytes.toString(koreanFallback)
        }
    }

    private fun extractPdf(uri: Uri): ExtractedBody {
        val tempFile = File.createTempFile("rsvp-import-", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("PDF 파일을 열 수 없습니다.")

            return PDDocument.load(tempFile).use { document ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val pageStarts = IntArray(document.numberOfPages)
                val textBuilder = StringBuilder()

                for (pageIndex in 0 until document.numberOfPages) {
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    val pageText = TextProcessor.normalize(stripper.getText(document))
                    if (pageText.isNotBlank() && textBuilder.isNotEmpty()) textBuilder.append("\n\n")
                    pageStarts[pageIndex] = textBuilder.length
                    if (pageText.isNotBlank()) textBuilder.append(pageText)
                }

                val text = textBuilder.toString()
                if (text.isBlank()) error("이 PDF에서는 텍스트를 추출할 수 없습니다. 스캔 이미지 PDF의 OCR은 현재 버전에서 지원하지 않습니다.")
                ExtractedBody(text, extractPdfOutline(document, pageStarts))
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun extractPdfOutline(document: PDDocument, pageStarts: IntArray): List<ImportedTocEntry> {
        val outline = document.documentCatalog.documentOutline ?: return emptyList()
        val result = mutableListOf<ImportedTocEntry>()

        fun visit(first: PDOutlineItem?, level: Int) {
            var item = first
            while (item != null) {
                val title = item.title?.trim().orEmpty()
                val page = runCatching { item.findDestinationPage(document) }.getOrNull()
                val pageIndex = page?.let { document.pages.indexOf(it) } ?: -1
                if (title.isNotBlank() && pageIndex in pageStarts.indices) {
                    result += ImportedTocEntry(title, level, pageStarts[pageIndex])
                }
                visit(item.firstChild, level + 1)
                item = item.nextSibling
            }
        }

        visit(outline.firstChild, 0)
        return result
    }

    private fun extractEpub(uri: Uri): ExtractedBody {
        val container = readZipEntry(uri, "META-INF/container.xml")
            ?: error("올바른 EPUB 구조를 찾지 못했습니다.")
        val containerDoc = parseXml(container)
        val rootfile = containerDoc.getElementsByTagName("rootfile").item(0) as? Element
            ?: error("EPUB 패키지 경로를 찾지 못했습니다.")
        val opfPath = rootfile.getAttribute("full-path")
        val opfBytes = readZipEntry(uri, opfPath)
            ?: error("EPUB 패키지 파일을 찾지 못했습니다.")
        val opfDoc = parseXml(opfBytes)
        val opfDir = opfPath.substringBeforeLast('/', "")

        val manifest = mutableMapOf<String, EpubManifestItem>()
        val itemNodes = opfDoc.getElementsByTagName("item")
        for (i in 0 until itemNodes.length) {
            val el = itemNodes.item(i) as? Element ?: continue
            manifest[el.getAttribute("id")] = EpubManifestItem(
                href = el.getAttribute("href"),
                mediaType = el.getAttribute("media-type"),
                properties = el.getAttribute("properties")
            )
        }

        val spine = opfDoc.getElementsByTagName("spine").item(0) as? Element
        val ncxId = spine?.getAttribute("toc").orEmpty()
        val spinePaths = mutableListOf<String>()
        val refs = opfDoc.getElementsByTagName("itemref")
        for (i in 0 until refs.length) {
            val el = refs.item(i) as? Element ?: continue
            val item = manifest[el.getAttribute("idref")] ?: continue
            if (item.mediaType.contains("html") || item.href.endsWith(".xhtml", true) || item.href.endsWith(".html", true)) {
                spinePaths += resolveEpubPath(opfDir, item.href)
            }
        }
        if (spinePaths.isEmpty()) error("EPUB 본문을 추출할 수 없습니다.")

        val wanted = spinePaths.toSet()
        val bodyByPath = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name in wanted) {
                        val html = zip.readBytes().toString(Charsets.UTF_8)
                        val plain = TextProcessor.normalize(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString())
                        if (plain.isNotBlank()) bodyByPath[entry.name] = plain
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("EPUB 파일을 열 수 없습니다.")

        val textBuilder = StringBuilder()
        val startByPath = mutableMapOf<String, Int>()
        spinePaths.forEach { path ->
            val body = bodyByPath[path] ?: return@forEach
            if (textBuilder.isNotEmpty()) textBuilder.append("\n\n")
            startByPath[path] = textBuilder.length
            textBuilder.append(body)
        }
        val text = textBuilder.toString()
        if (text.isBlank()) error("EPUB 본문을 추출할 수 없습니다.")

        val navItem = manifest.values.firstOrNull { item ->
            item.properties.split(Regex("\\s+")).any { it.equals("nav", ignoreCase = true) }
        }
        val navLinks = navItem?.let { item ->
            val navPath = resolveEpubPath(opfDir, item.href)
            readZipEntry(uri, navPath)?.let { EpubTocParser.parseNavigationDocument(it, navPath) }
        }.orEmpty()

        val ncxItem = manifest[ncxId]
            ?: manifest.values.firstOrNull { it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) }
        val tocLinks = if (navLinks.isNotEmpty()) navLinks else ncxItem?.let { item ->
            val ncxPath = resolveEpubPath(opfDir, item.href)
            readZipEntry(uri, ncxPath)?.let { EpubTocParser.parseNcx(it, ncxPath) }
        }.orEmpty()

        val toc = tocLinks.mapNotNull { link ->
            startByPath[link.targetPath]?.let { offset ->
                ImportedTocEntry(link.title, link.level, offset)
            }
        }
        return ExtractedBody(text, toc)
    }

    private fun readZipEntry(uri: Uri, targetPath: String): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == targetPath) return zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("EPUB 파일을 열 수 없습니다.")
        return null
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
