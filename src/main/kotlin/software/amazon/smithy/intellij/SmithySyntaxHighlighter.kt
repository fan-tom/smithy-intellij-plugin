package software.amazon.smithy.intellij

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import software.amazon.smithy.intellij.psi.SmithyTypes

/**
 * A [SyntaxHighlighterFactory] for [SmithySyntaxHighlighter].
 *
 * @author Ian Caffey
 * @since 1.0
 */
class SmithySyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = SmithySyntaxHighlighter()
}

/**
 * A [SyntaxHighlighter] for [Smithy](https://awslabs.github.io/smithy).
 *
 * @author Ian Caffey
 * @since 1.0
 * @see SmithyColorSettingsPage
 */
class SmithySyntaxHighlighter : SyntaxHighlighterBase() {
    companion object {
        //Note: these can only match against tokens, SmithyAnnotator supports the contextual syntax highlighting with the higher-level AST nodes
        private val TOKEN_HIGHLIGHTS = mapOf(
            SmithyTypes.TOKEN_NUMBER to arrayOf(SmithyColorSettingsPage.NUMBER),
            SmithyTypes.TOKEN_STRING to arrayOf(SmithyColorSettingsPage.STRING),
            SmithyTypes.TOKEN_TEXT_BLOCK to arrayOf(SmithyColorSettingsPage.STRING),
            SmithyTypes.TOKEN_COMMENT to arrayOf(SmithyColorSettingsPage.LINE_COMMENT),
            SmithyTypes.TOKEN_OPEN_BRACE to arrayOf(SmithyColorSettingsPage.BRACES),
            SmithyTypes.TOKEN_CLOSE_BRACE to arrayOf(SmithyColorSettingsPage.BRACES),
            SmithyTypes.TOKEN_OPEN_BRACKET to arrayOf(SmithyColorSettingsPage.BRACKETS),
            SmithyTypes.TOKEN_CLOSE_BRACKET to arrayOf(SmithyColorSettingsPage.BRACKETS),
            SmithyTypes.TOKEN_OPEN_PAREN to arrayOf(SmithyColorSettingsPage.PARENS),
            SmithyTypes.TOKEN_CLOSE_PAREN to arrayOf(SmithyColorSettingsPage.PARENS),
            SmithyTypes.TOKEN_COMMA to arrayOf(SmithyColorSettingsPage.COMMA),
            SmithyTypes.TOKEN_PERIOD to arrayOf(SmithyColorSettingsPage.DOT),
            SmithyTypes.TOKEN_SIMPLE_TYPE_NAME to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_BOOLEAN to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_NULL to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_APPLY to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_LIST to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_MAP to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_OPERATION to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_METADATA to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_NAMESPACE to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_RESOURCE to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_SERVICE to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_SET to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_STRUCTURE to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_UNION to arrayOf(SmithyColorSettingsPage.KEYWORD),
            SmithyTypes.TOKEN_USE to arrayOf(SmithyColorSettingsPage.KEYWORD)
        )
    }

    override fun getHighlightingLexer() = SmithyLexer()
    override fun getTokenHighlights(tokenType: IElementType?) = TOKEN_HIGHLIGHTS[tokenType] ?: emptyArray()
}