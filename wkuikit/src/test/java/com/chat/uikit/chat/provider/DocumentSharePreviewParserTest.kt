package com.chat.uikit.chat.provider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentSharePreviewParserTest {
    @Test fun parsesNestedDocumentHeadingAndParagraphsWithProducerShape() {
        val body = JSONObject().put("doc", JSONObject().put("content", JSONArray()
            .put(JSONObject().put("type", "heading").put("content", JSONArray().put(JSONObject().put("text", "Project ")).put(JSONObject().put("content", JSONArray().put(JSONObject().put("text", "plan"))))))
            .put(JSONObject().put("type", "paragraph").put("content", JSONArray().put(JSONObject().put("text", "First paragraph"))))
            .put(JSONObject().put("type", "paragraph").put("content", JSONArray().put(JSONObject().put("text", "Second paragraph"))))))
        assertEquals(DocumentSharePreviewParser.Preview.Doc("Project plan", listOf("First paragraph", "Second paragraph")), DocumentSharePreviewParser.parseDoc(body))
    }

    @Test fun parsesSparseSheetCoordinatesAndSelectsMostPopulatedSheet() {
        val cells = JSONObject()
            .put("Sparse!0:0", JSONObject().put("v", "Name"))
            .put("Sparse!0:3", JSONObject().put("v", "City"))
            .put("Sparse!2:0", JSONObject().put("v", "Lin"))
            .put("Sparse!2:3", JSONObject().put("v", "Suzhou"))
            .put("Sparse!1:3", JSONObject().put("v", ""))
            .put("Sparse!99:0", JSONObject().put("v", "outside rows"))
            .put("Sparse!0:99", JSONObject().put("v", "outside columns"))
            .put("Other!0:0", JSONObject().put("v", "only one"))
        val parsed = DocumentSharePreviewParser.parseSheet(JSONObject().put("sheetCells", cells))
        assertEquals(DocumentSharePreviewParser.Preview.Sheet(listOf("Name", "City"), listOf(listOf("Lin", "Suzhou"))), parsed)
    }

    @Test fun parsesBoardAndHtmlPreviewWithTheirActualPayloadShapes() {
        val board = DocumentSharePreviewParser.parseBoard(JSONObject().put("scene", JSONObject().put("elements", JSONArray()
            .put(JSONObject().put("text", "  Shape  ")).put("Connector").put(JSONObject().put("text", "")))))
        assertEquals(DocumentSharePreviewParser.Preview.Board(listOf("Shape", "Connector")), board)
        val html = DocumentSharePreviewParser.parseHtml(JSONObject().put("preview", JSONObject()
            .put("heading", " Page ").put("paragraphs", JSONArray().put(" First ").put(7).put("Second").put("Third").put("Ignored"))))
        assertEquals(DocumentSharePreviewParser.Preview.Doc("Page", listOf("First", "Second", "Third")), html)
    }

    @Test fun parserBoundsDocumentDepthVisitsAndTextLength() {
        var nested = JSONObject().put("text", "deep")
        repeat(25) { nested = JSONObject().put("content", JSONArray().put(nested)) }
        val body = JSONObject().put("doc", JSONObject().put("content", JSONArray()
            .put(JSONObject().put("type", "heading").put("content", JSONArray().put(nested)))
            .put(JSONObject().put("type", "paragraph").put("content", JSONArray().put(JSONObject().put("text", "x".repeat(700)))))))
        val parsed = DocumentSharePreviewParser.parseDoc(body) as DocumentSharePreviewParser.Preview.Doc
        assertNull(parsed.heading)
        assertEquals(500, parsed.paragraphs.single().length)

        val tooManyBlocks = JSONArray()
        repeat(200) { tooManyBlocks.put(JSONObject().put("type", "other")) }
        tooManyBlocks.put(JSONObject().put("type", "paragraph").put("content", JSONArray().put(JSONObject().put("text", "outside scan bound"))))
        assertNull(DocumentSharePreviewParser.parseDoc(JSONObject().put("doc", JSONObject().put("content", tooManyBlocks))))

        val visitFixture = JSONArray()
        repeat(109) {
            val shallowChildren = JSONArray()
            repeat(10) { shallowChildren.put(JSONObject().put("text", "")) }
            visitFixture.put(JSONObject().put("content", shallowChildren))
        }
        visitFixture.put(JSONObject().put("content", JSONArray().put(JSONObject().put("text", "unvisited sentinel"))))
        val visitsBody = JSONObject().put("doc", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "heading").put("content", visitFixture))))
        assertNull(DocumentSharePreviewParser.parseDoc(visitsBody))
    }

    @Test fun parserBoundsSheetValuesBoardNodesAndHtmlParagraphCount() {
        val long = "v".repeat(700)
        val cells = JSONObject().put("Sheet!0:0", JSONObject().put("v", long))
        val sheet = DocumentSharePreviewParser.parseSheet(JSONObject().put("sheetCells", cells)) as DocumentSharePreviewParser.Preview.Sheet
        assertEquals(500, sheet.headers.single().length)
        val elements = JSONArray(); repeat(10) { elements.put(JSONObject().put("text", "node$it")) }
        val board = DocumentSharePreviewParser.parseBoard(JSONObject().put("elements", elements)) as DocumentSharePreviewParser.Preview.Board
        assertEquals(6, board.texts.size)
        val paragraphs = JSONArray(); repeat(8) { paragraphs.put("p$it") }
        val html = DocumentSharePreviewParser.parseHtml(JSONObject().put("preview", JSONObject().put("paragraphs", paragraphs))) as DocumentSharePreviewParser.Preview.Doc
        assertEquals(3, html.paragraphs.size)
    }
}
