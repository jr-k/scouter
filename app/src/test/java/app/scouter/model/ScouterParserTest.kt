package app.scouter.model

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScouterParserTest {
    @Test
    fun parsesEveryCanonicalComponent() {
        val document = ScouterParser.parse(
            """
            {
              "version": 1,
              "id": "contract-test",
              "presentation": {"minimizable": true},
              "content": {
                "type": "column",
                "children": [
                  {"type": "column", "children": []},
                  {"type": "row", "children": []},
                  {"type": "grid", "children": []},
                  {"type": "box", "child": {"type": "text", "value": "Box"}},
                  {"type": "card", "child": {"type": "text", "value": "Card"}},
                  {"type": "text", "value": "Text"},
                  {"type": "button", "id": "button", "label": "Button", "action": {"type": "close"}},
                  {"type": "hold_button", "id": "hold", "label": "Hold"},
                  {"type": "slide_action", "id": "slide", "label": "Slide"},
                  {"type": "badge", "label": "Badge"},
                  {"type": "progress", "value": 0.5},
                  {"type": "image", "url": "https://example.com/image.png"},
                  {
                    "type": "checklist",
                    "id": "tasks",
                    "items": [{
                      "id": "task-1",
                      "label": "Task",
                      "action": {"type": "navigate", "address": "Paris"},
                      "actionIcon": "directions"
                    }]
                  },
                  {"type": "link", "id": "link", "label": "Link", "url": "https://example.com"},
                  {
                    "type": "link_list",
                    "id": "links",
                    "checkable": true,
                    "links": [{"id": "link-1", "label": "Link", "url": "https://example.com"}]
                  },
                  {"type": "address", "id": "address", "address": "Paris"},
                  {
                    "type": "address_list",
                    "addresses": [{"id": "address-1", "address": "Paris"}]
                  },
                  {"type": "phone", "id": "phone", "phoneNumber": "+33123456789"},
                  {
                    "type": "phone_list",
                    "phones": [{"id": "phone-1", "phoneNumber": "+33123456789"}]
                  },
                  {
                    "type": "action_list",
                    "items": [
                      {"type": "address", "id": "mixed-address", "address": "Paris"},
                      {"type": "phone", "id": "mixed-phone", "phoneNumber": "+33123456789"},
                      {
                        "type": "link",
                        "id": "mixed-link",
                        "label": "Website",
                        "url": "https://example.com"
                      }
                    ]
                  },
                  {"type": "map", "latitude": 48.8566, "longitude": 2.3522},
                  {"type": "video", "url": "https://example.com/video.mp4"},
                  {"type": "web", "html": "<p>Hello</p>"},
                  {"type": "divider"},
                  {"type": "spacer"}
                ]
              }
            }
            """.trimIndent(),
        )

        assertTrue(document.presentation.minimizable)
        val children = (document.content as UiNode.Column).children
        assertEquals(25, children.size)
        assertEquals(2, (children[2] as UiNode.Grid).columns)
        val checklist = children[12] as UiNode.Checklist
        assertEquals("Navigate", checklist.items.single().buttonLabel)
        assertEquals("directions", checklist.items.single().actionIcon?.name)
    }

    @Test
    fun defaultsMatchThePublishedSchema() {
        val document = ScouterParser.parse(
            """{"version":1,"id":"defaults","content":{"type":"text","value":"Hello"}}""",
        )

        assertTrue(document.presentation.dismissible)
        assertFalse(document.presentation.minimizable)
        assertTrue(document.presentation.showHandle)
        assertEquals(0.82f, document.presentation.maxHeight)
        assertEquals(12f, document.presentation.horizontalMargin)
        assertEquals(18f, document.theme.cornerRadius)
        assertEquals(1f, document.theme.fontScale)
    }

    @Test
    fun rejectsUnsafeOpenUrlAndUnstableInteractiveIds() {
        assertThrows(IllegalArgumentException::class.java) {
            ScouterParser.parse(
                """
                {
                  "version": 1,
                  "id": "unsafe-url",
                  "content": {
                    "type": "button",
                    "id": "open",
                    "label": "Open",
                    "action": {"type": "open_url", "url": "javascript:alert(1)"}
                  }
                }
                """.trimIndent(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScouterParser.parse(
                """{"version":1,"id":"bad-id","content":{"type":"button","id":"not valid","label":"Run"}}""",
            )
        }
    }

    @Test
    fun publishedSchemaListsAllComponentsAndLifecycleDefaults() {
        val schemaFile = sequenceOf(
            File("scouter.schema.json"),
            File("../scouter.schema.json"),
        ).first { it.isFile }
        val schema = JSONObject(schemaFile.readText())
        val definitions = schema.getJSONObject("${'$'}defs")
        val nodeChoices = definitions.getJSONObject("node").getJSONArray("oneOf")
        val presentation = definitions.getJSONObject("presentation").getJSONObject("properties")

        assertEquals(25, nodeChoices.length())
        assertEquals(true, presentation.getJSONObject("dismissible").get("default"))
        assertEquals(false, presentation.getJSONObject("minimizable").get("default"))
        assertEquals(true, presentation.getJSONObject("showHandle").get("default"))
        assertFalse(presentation.has("stackOffset"))
        assertTrue(definitions.getJSONObject("checklist").toString().contains("actionIcon"))
    }
}
