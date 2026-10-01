package com.example.rsvpreader

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubTocParserTest {
    @Test
    fun parsesEpub3NavigationHierarchyAndTargets() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <nav epub:type="toc">
                  <ol>
                    <li><a href="chapter1.xhtml#start">1장</a></li>
                    <li>
                      <a href="chapter2.xhtml">2장</a>
                      <ol><li><a href="chapter2.xhtml#part-a">2.1절</a></li></ol>
                    </li>
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent().toByteArray()

        val links = EpubTocParser.parseNavigationDocument(xml, "OPS/nav.xhtml")
        assertEquals(
            listOf(
                EpubTocLink("1장", 0, "OPS/chapter1.xhtml"),
                EpubTocLink("2장", 0, "OPS/chapter2.xhtml"),
                EpubTocLink("2.1절", 1, "OPS/chapter2.xhtml")
            ),
            links
        )
    }

    @Test
    fun parsesEpub2NcxHierarchyAndTargets() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
              <navMap>
                <navPoint id="n1">
                  <navLabel><text>Chapter One</text></navLabel>
                  <content src="text/ch1.xhtml"/>
                  <navPoint id="n1a">
                    <navLabel><text>Section A</text></navLabel>
                    <content src="text/ch1.xhtml#a"/>
                  </navPoint>
                </navPoint>
                <navPoint id="n2">
                  <navLabel><text>Chapter Two</text></navLabel>
                  <content src="text/ch2.xhtml"/>
                </navPoint>
              </navMap>
            </ncx>
        """.trimIndent().toByteArray()

        val links = EpubTocParser.parseNcx(xml, "OPS/toc.ncx")
        assertEquals(
            listOf(
                EpubTocLink("Chapter One", 0, "OPS/text/ch1.xhtml"),
                EpubTocLink("Section A", 1, "OPS/text/ch1.xhtml"),
                EpubTocLink("Chapter Two", 0, "OPS/text/ch2.xhtml")
            ),
            links
        )
    }
}
