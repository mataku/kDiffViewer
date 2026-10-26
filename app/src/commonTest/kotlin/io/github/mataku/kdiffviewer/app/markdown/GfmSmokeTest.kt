package io.github.mataku.kdiffviewer.app.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GfmSmokeTest {
  private val source: CharSequence =
    """
    ~~gone~~

    - [x] done

    ```kotlin
    val a = 1
    ```

    see https://example.com
    """.trimIndent()

  private val tree = MarkdownParser(GFMFlavourDescriptor(), true, CancellationToken.NonCancellable).buildMarkdownTreeFromString(source)

  private fun find(
    node: ASTNode,
    type: IElementType,
  ): ASTNode? {
    if (node.type == type) return node
    return node.children.firstNotNullOfOrNull { find(it, type) }
  }

  @Test
  fun treeContainsTheNodeTypesTheParserReliesOn() {
    assertNotNull(find(tree, GFMElementTypes.STRIKETHROUGH))
    assertNotNull(find(tree, GFMTokenTypes.CHECK_BOX))
    assertNotNull(find(tree, GFMTokenTypes.GFM_AUTOLINK))
    val fence = assertNotNull(find(tree, MarkdownElementTypes.CODE_FENCE))
    val lang = assertNotNull(find(fence, MarkdownTokenTypes.FENCE_LANG))
    assertEquals("kotlin", lang.getTextInNode(source).toString())
  }
}
