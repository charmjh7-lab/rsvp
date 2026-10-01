package com.example.rsvpreader

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class EpubTocLink(
    val title: String,
    val level: Int,
    val targetPath: String
)

object EpubTocParser {
    fun parseNavigationDocument(bytes: ByteArray, navPath: String): List<EpubTocLink> = runCatching {
        val document = parseXml(bytes)
        val navElements = elementsByLocalName(document.documentElement, "nav")
        val tocNav = navElements.firstOrNull { nav ->
            val type = attributeByLocalName(nav, "type")
            val role = nav.getAttribute("role")
            type.split(' ').any { it.equals("toc", ignoreCase = true) } ||
                role.equals("doc-toc", ignoreCase = true)
        } ?: return@runCatching emptyList()

        elementsByLocalName(tocNav, "a").mapNotNull { anchor ->
            val title = anchor.textContent.orEmpty().replace(Regex("\\s+"), " ").trim()
            val href = anchor.getAttribute("href").trim()
            if (title.isBlank() || href.isBlank()) return@mapNotNull null
            val target = href.substringBefore('#')
            val resolved = resolveEpubPath(navPath.substringBeforeLast('/', ""), target.ifBlank { navPath.substringAfterLast('/') })
            if (resolved.isBlank()) return@mapNotNull null

            var level = 0
            var parent = anchor.parentNode
            while (parent != null && parent != tocNav) {
                if (parent is Element && localName(parent) in setOf("ol", "ul")) level++
                parent = parent.parentNode
            }
            EpubTocLink(title, (level - 1).coerceAtLeast(0), resolved)
        }
    }.getOrDefault(emptyList())

    fun parseNcx(bytes: ByteArray, ncxPath: String): List<EpubTocLink> = runCatching {
        val document = parseXml(bytes)
        val navMap = elementsByLocalName(document.documentElement, "navMap").firstOrNull()
            ?: return@runCatching emptyList()
        val result = mutableListOf<EpubTocLink>()

        fun visit(parent: Node, level: Int) {
            directChildElements(parent, "navPoint").forEach { navPoint ->
                val label = elementsByLocalName(navPoint, "navLabel")
                    .firstOrNull()
                    ?.let { elementsByLocalName(it, "text").firstOrNull()?.textContent }
                    .orEmpty()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val src = elementsByLocalName(navPoint, "content")
                    .firstOrNull()
                    ?.getAttribute("src")
                    .orEmpty()
                    .trim()
                if (label.isNotBlank() && src.isNotBlank()) {
                    val target = src.substringBefore('#')
                    val resolved = resolveEpubPath(
                        ncxPath.substringBeforeLast('/', ""),
                        target.ifBlank { ncxPath.substringAfterLast('/') }
                    )
                    if (resolved.isNotBlank()) result += EpubTocLink(label, level, resolved)
                }
                visit(navPoint, level + 1)
            }
        }

        visit(navMap, 0)
        result
    }.getOrDefault(emptyList())

    private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun elementsByLocalName(root: Node, target: String): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(node: Node) {
            if (node is Element && localName(node) == target) result += node
            var child = node.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        walk(root)
        return result
    }

    private fun directChildElements(parent: Node, target: String): List<Element> {
        val result = mutableListOf<Element>()
        var child = parent.firstChild
        while (child != null) {
            if (child is Element && localName(child) == target) result += child
            child = child.nextSibling
        }
        return result
    }

    private fun attributeByLocalName(element: Element, target: String): String {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i)
            val local = attribute.localName ?: attribute.nodeName.substringAfterLast(':')
            if (local == target) return attribute.nodeValue.orEmpty()
        }
        return ""
    }

    private fun localName(node: Node): String =
        node.localName ?: node.nodeName.substringAfterLast(':')
}

fun resolveEpubPath(baseDir: String, href: String): String {
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
