package software.amazon.smithy.intellij

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.lang.annotation.HighlightSeverity.INFORMATION
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.nextLeaf
import com.intellij.psi.util.nextLeafs
import com.intellij.psi.util.siblings
import software.amazon.smithy.intellij.actions.SmithyImportShapeQuickFix
import software.amazon.smithy.intellij.actions.SmithyOptimizeShapeIdQuickFix
import software.amazon.smithy.intellij.actions.SmithyRemoveCommasQuickFix
import software.amazon.smithy.intellij.actions.SmithyRemoveImportQuickFix
import software.amazon.smithy.intellij.actions.SmithyRemoveMemberInitializerQuickFix
import software.amazon.smithy.intellij.actions.SmithyRemoveMemberQuickFix
import software.amazon.smithy.intellij.actions.SmithyRemoveMixinQuickFix
import software.amazon.smithy.intellij.actions.SmithyRemoveResourceReferenceQuickFix
import software.amazon.smithy.intellij.actions.SmithyRemoveUnusedImportsQuickFix
import software.amazon.smithy.intellij.psi.SmithyArray
import software.amazon.smithy.intellij.psi.SmithyBoolean
import software.amazon.smithy.intellij.psi.SmithyContainerBody
import software.amazon.smithy.intellij.psi.SmithyControl
import software.amazon.smithy.intellij.psi.SmithyElidedMember
import software.amazon.smithy.intellij.psi.SmithyEntry
import software.amazon.smithy.intellij.psi.SmithyEnumMember
import software.amazon.smithy.intellij.psi.SmithyImport
import software.amazon.smithy.intellij.psi.SmithyIncompleteAppliedTrait
import software.amazon.smithy.intellij.psi.SmithyIncompleteDefinition
import software.amazon.smithy.intellij.psi.SmithyIncompleteEntry
import software.amazon.smithy.intellij.psi.SmithyIncompleteMember
import software.amazon.smithy.intellij.psi.SmithyIntEnumMember
import software.amazon.smithy.intellij.psi.SmithyKey
import software.amazon.smithy.intellij.psi.SmithyMap
import software.amazon.smithy.intellij.psi.SmithyMemberDefinition
import software.amazon.smithy.intellij.psi.SmithyMemberId
import software.amazon.smithy.intellij.psi.SmithyMemberInitializer
import software.amazon.smithy.intellij.psi.SmithyMemberName
import software.amazon.smithy.intellij.psi.SmithyMetadata
import software.amazon.smithy.intellij.psi.SmithyMixins
import software.amazon.smithy.intellij.psi.SmithyModel
import software.amazon.smithy.intellij.psi.SmithyNull
import software.amazon.smithy.intellij.psi.SmithyOperationProperty
import software.amazon.smithy.intellij.psi.SmithyResourceEntry
import software.amazon.smithy.intellij.psi.SmithyResourceReference
import software.amazon.smithy.intellij.psi.SmithyServiceProperty
import software.amazon.smithy.intellij.psi.SmithyShape
import software.amazon.smithy.intellij.psi.SmithyShapeId
import software.amazon.smithy.intellij.psi.SmithyStatement
import software.amazon.smithy.intellij.psi.SmithyString
import software.amazon.smithy.intellij.psi.SmithyTextBlock
import software.amazon.smithy.intellij.psi.SmithyTrait
import software.amazon.smithy.intellij.psi.SmithyTraitBody
import software.amazon.smithy.intellij.psi.SmithyTypes
import software.amazon.smithy.intellij.psi.SmithyValue
import java.util.*

/**
 * An [Annotator] which provides annotations to [Smithy](https://awslabs.github.io/smithy) model files.
 *
 * All syntax checks which require the higher-level AST (resulting from a complete parse) are handled to complete syntax highlighting from [SmithySyntaxHighlighter].
 *
 * @author Ian Caffey
 * @since 1.0
 */
class SmithyAnnotator : Annotator {
    private val annotations = EnumSet.allOf(Annotation::class.java)
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val version = (element.containingFile as? SmithyFile)?.model?.version
        annotations.forEach {
            if ((it.sinceVersion == null || version != null && SmithyVersion.compare(version, it.sinceVersion) >= 0)
                && (it.untilVersion == null || version != null && SmithyVersion.compare(version, it.untilVersion) <= 0)
            ) {
                it.annotate(element, holder)
            }
        }
    }
}

private enum class Annotation(val sinceVersion: String? = null, val untilVersion: String? = null) : Annotator {
    KEYWORD {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyBoolean
                || element is SmithyNull
                || (element.elementType == SmithyTypes.TOKEN_FOR && element.parent is SmithyResourceReference)
                || (element.elementType == SmithyTypes.TOKEN_WITH && element.parent is SmithyMixins)
                || getParentOfType(element, SmithyStatement::class.java)?.let { element == it.typeIdentifier } == true
            ) {
                holder.highlight(SmithyColorSettings.KEYWORD)
            }
        }
    },
    CONTROL {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if ((element is SmithyKey || element.elementType == SmithyTypes.TOKEN_DOLLAR_SIGN) && element.parent is SmithyControl) {
                holder.highlight(SmithyColorSettings.CONTROL)
            }
        }
    },
    KEY {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyKey && element.parent !is SmithyControl) {
                holder.highlight(SmithyColorSettings.KEY)
            }
        }
    },
    MEMBER {
        val operationProperties = setOf(
            SmithyTypes.TOKEN_ERRORS,
            SmithyTypes.TOKEN_INPUT,
            SmithyTypes.TOKEN_OUTPUT,
        )
        val resourceEntries = setOf(
            SmithyTypes.TOKEN_COLLECTION_OPERATIONS,
            SmithyTypes.TOKEN_CREATE,
            SmithyTypes.TOKEN_DELETE,
            SmithyTypes.TOKEN_IDENTIFIERS,
            SmithyTypes.TOKEN_LIST,
            SmithyTypes.TOKEN_OPERATIONS,
            SmithyTypes.TOKEN_PROPERTIES,
            SmithyTypes.TOKEN_PUT,
            SmithyTypes.TOKEN_READ,
            SmithyTypes.TOKEN_RESOURCES,
            SmithyTypes.TOKEN_UPDATE
        )
        val serviceProperties = setOf(
            SmithyTypes.TOKEN_VERSION,
            SmithyTypes.TOKEN_OPERATIONS,
            SmithyTypes.TOKEN_RESOURCES,
            SmithyTypes.TOKEN_ERRORS,
            SmithyTypes.TOKEN_RENAME,
        )

        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyMemberName
                || (element.elementType == SmithyTypes.TOKEN_DOLLAR_SIGN && element.parent is SmithyElidedMember)
                || (element.elementType in operationProperties && element.parent is SmithyOperationProperty)
                || (element.elementType in resourceEntries && element.parent is SmithyResourceEntry)
                || (element.elementType in serviceProperties && element.parent is SmithyServiceProperty)
            ) {
                holder.highlight(SmithyColorSettings.SHAPE_MEMBER)
            }
        }
    },
    TRAIT_NAME {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if ((element is SmithyShapeId || element.elementType == SmithyTypes.TOKEN_AT) && element.parent is SmithyTrait) {
                holder.highlight(SmithyColorSettings.TRAIT_NAME)
            }
        }
    },
    ESCAPE_SEQUENCES {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if ((element is SmithyString || element is SmithyTextBlock) && element.text.contains("\\")) {
                val valid = mutableListOf<TextRange>()
                val invalid = mutableListOf<TextRange>()
                var i = 0
                while (i < element.textLength) {
                    if (element.text[i] != '\\') {
                        i++
                        continue
                    }
                    val length = if (i < element.textLength - 5 && element.text[i + 1] == 'u' && (2..5).all {
                            val digit = element.text[i + it]
                            (digit in '0'..'9') || (digit in 'a'..'f') || (digit in 'A'..'F')
                        }) 6 else 2
                    if (length == 2 && element.text[i + 1] !in "\"'bfnrt/\\\n") {
                        invalid.add(TextRange.from(i, length))
                    } else {
                        valid.add(TextRange.from(i, length))
                    }
                    i += length
                }
                val range = element.textRange
                valid.forEach { holder.highlight(SmithyColorSettings.VALID_ESCAPE_SEQUENCE, range.cutOut(it)) }
                invalid.forEach {
                    holder.highlight(
                        ERROR,
                        "Invalid escape sequence: '${it.substring(element.text)}'",
                        SmithyColorSettings.INVALID_ESCAPE_SEQUENCE,
                        range.cutOut(it)
                    )
                }
            }
        }
    },
    TRAILING_NEW_LINE(untilVersion = "1.0") {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element.elementType in SmithyFormattingModelBuilder.TOKENS_REQUIRING_TRAILING_NEW_LINE && element.nextLeaf() != null) {
                val trailingWhiteSpace = element.nextLeafs.takeWhile { it is PsiComment || it is PsiWhiteSpace }
                if (trailingWhiteSpace.none { it.textContains('\n') }) {
                    holder.highlight(ERROR, "Expecting trailing line break '\\n'")
                }
            }
        }
    },
    MISSING_COMMA(untilVersion = "1.0") {
        val tokensRequiringCommaDelimiter = setOf(
            SmithyTypes.CONTAINER_MEMBER,
            SmithyTypes.ENTRY,
            SmithyTypes.ENUM_MEMBER,
            SmithyTypes.INT_ENUM_MEMBER,
            SmithyTypes.VALUE
        )

        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            val type = element.elementType
            if (type in tokensRequiringCommaDelimiter) {
                val nextComma = element.siblings(withSelf = false).indexOfFirst {
                    it.elementType == SmithyTypes.TOKEN_COMMA
                }
                val nextSibling = element.siblings(withSelf = false).indexOfFirst { it.elementType == type }
                if (nextSibling != -1 && (nextComma == -1 || nextSibling < nextComma)) {
                    holder.highlight(ERROR, "Expecting trailing comma ','")
                }
            }
        }
    },
    UNNECESSARY_COMMAS(sinceVersion = "2.0") {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element.elementType == SmithyTypes.TOKEN_COMMA) {
                holder.newAnnotation(INFORMATION, "Remove unnecessary commas")
                    .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                    .withFix(SmithyRemoveCommasQuickFix)
                    .create()
            }
        }
    },
    INVALID_DEFAULT_VALUE {
        val validEnclosingShapes = setOf("enum", "intEnum", "structure")
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyMemberInitializer) {
                val enclosingShapeType = element.enclosingMember.enclosingShape.type
                if (enclosingShapeType !in validEnclosingShapes) {
                    holder.newAnnotation(ERROR, "'${element.enclosingMember.name}' cannot have a default value")
                        .withFix(SmithyRemoveMemberInitializerQuickFix(element))
                        .create()
                }
            }
        }
    },
    INVALID_ENUM_MEMBER {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyValue) {
                if (element.parent is SmithyEnumMember && element.asString() == null) {
                    holder.highlight(ERROR, "Expected a string value")
                }
                if (element.parent is SmithyIntEnumMember && element.asNumber() == null) {
                    holder.highlight(ERROR, "Expected an integer value")
                }
            }
        }
    },
    INVALID_RESOURCE_REFERENCE {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyResourceReference) {
                val targetType = element.shapeId.resolve()?.type
                if (targetType != null && targetType != "resource") {
                    holder.newAnnotation(ERROR, "Structures can only target resource shapes")
                        .withFix(SmithyRemoveResourceReferenceQuickFix(element))
                        .create()
                }
            }
        }
    },
    CIRCULAR_MIXIN_REFERENCE {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyMixins && SmithyShapeAggregator.hasCycle(element.enclosingShape)) {
                holder.highlight(ERROR, "Mixins cannot introduce circular references")
            }
        }
    },
    INVALID_MIXIN {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyShapeId) {
                val mixins = element.parent as? SmithyMixins ?: return
                element.resolve()?.let {
                    val error = when {
                        !it.hasTrait("smithy.api", "mixin") -> "${element.shapeName} cannot be used as a mixin"
                        it.type != mixins.enclosingShape.type -> "${element.shapeName} cannot be applied to '${mixins.enclosingShape.type}' shapes"
                        else -> null
                    }
                    if (error != null) {
                        holder.newAnnotation(ERROR, error)
                            .withFix(SmithyRemoveMixinQuickFix(element))
                            .create()
                    }
                }
            }
        }
    },
    INVALID_MIXIN_USAGE {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyShapeId
                && element.parent.let { it !is SmithyImport && it !is SmithyMixins }
                && element.resolve()?.findTrait("smithy.api", "mixin") != null
            ) {
                holder.highlight(ERROR, "Mixins cannot be referenced other than as mixins to other shapes")
            }
        }
    },
    INCOMPLETE_STRING {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element.elementType == SmithyTypes.TOKEN_INCOMPLETE_STRING) {
                holder.highlight(ERROR, "Expected closing quote '\"'")
            }
            if (element.elementType == SmithyTypes.TOKEN_INCOMPLETE_TEXT_BLOCK) {
                holder.highlight(ERROR, "Expected closing quotes '\"\"\"'")
            }
        }
    },
    INCOMPLETE_DEFINITION {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyIncompleteDefinition) {
                val message = when (element.parent) {
                    is SmithyContainerBody -> "Expected member declaration"
                    else -> "Expected shape declaration"
                }
                holder.highlight(ERROR, message)
            }
        }
    },
    INCOMPLETE_MEMBER {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyIncompleteEntry || element is SmithyIncompleteMember) {
                holder.highlight(ERROR, "Expected shape id")
            }
        }
    },
    INCOMPLETE_TRAIT {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyIncompleteAppliedTrait) {
                holder.highlight(ERROR, "Expected trait")
            }
        }
    },
    MISSING_MEMBERS {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyShape) {
                val missingMembers = element.requiredMembers.filter { name -> element.members.none { it.name == name } }
                val message = when (missingMembers.size) {
                    0 -> null
                    1 -> "Missing '${missingMembers.first()}'"
                    2 -> "Missing '${missingMembers[0]}' and '${missingMembers[1]}'"
                    else -> missingMembers.subList(0, missingMembers.size - 1).joinToString(
                        ", ", "Missing ", ", and '${missingMembers.last()}'"
                    ) { "'$it'" }
                }
                if (message != null) {
                    holder.highlight(ERROR, message)
                }
            }
        }
    },
    UNSUPPORTED_MEMBER {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            when (element) {
                is SmithyMemberDefinition -> element.name to element.parent //all complex shapes (enum, intEnum, and all containers)
                is SmithyEntry -> element.name to element.parent.parent //service, resource, operation
                else -> null
            }?.takeIf { (_, parent) -> parent is SmithyShape }?.let { (name, parent) ->
                val enclosingShape = parent as SmithyShape
                enclosingShape.supportedMembers?.toList()?.let {
                    if (name !in it) {
                        val message = when (it.size) {
                            0 -> "${enclosingShape.type} does not have members"
                            1 -> "${enclosingShape.type} expects '${it.first()}'"
                            2 -> "${enclosingShape.type} expects '${it[0]}' or '${it[1]}'"
                            else -> it.subList(0, it.size - 1).joinToString(
                                ", ", "${enclosingShape.type} expects ", ", or '${it.last()}'"
                            ) { member -> "'$member'" }
                        }
                        holder.highlight(ERROR, message)
                    }
                }
            }
        }
    },
    DUPLICATE_MEMBER {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyMemberDefinition && element.parent.children.any {
                    it is SmithyMemberDefinition && it != element && it.name == element.name
                }) {
                holder.newAnnotation(ERROR, "'${element.name}' is already defined")
                    .withFix(SmithyRemoveMemberQuickFix(element))
                    .create()
            }
        }
    },
    MAP_KEY_TARGET {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyMemberDefinition && element.name == "key" && element.enclosingShape is SmithyMap) {
                val target = element.resolve()
                if (target != null && target.type != "string" && target.type != "enum") {
                    holder.highlight(ERROR, "'key' must target a string or enum shape")
                }
            }
        }
    },
    INVALID_TRAIT {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyTrait) {
                val target = element.resolve()
                if (target != null && !target.hasTrait("smithy.api", "trait")) {
                    holder.newAnnotation(ERROR, "${element.shape.shapeName} cannot be used as a trait")
                        .range(element.shape)
                        .highlightType(ProblemHighlightType.ERROR)
                        .create()
                }
            }
        }
    },
    IMPORT_OPTIMIZER {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyShapeId
                && element.parent !is SmithyImport
                && getParentOfType(element, SmithyMetadata::class.java) == null
            ) {
                element.namespaceId?.let { namespaceId ->
                    val conflicts = getParentOfType(element, SmithyModel::class.java)?.shapes?.any {
                        it.name == element.shapeName && it.namespace != namespaceId.id
                    } == true
                    if (!conflicts) {
                        val fix = SmithyOptimizeShapeIdQuickFix(element.project, element)
                        holder.newAnnotation(
                            INFORMATION,
                            if (fix.requiresImport) "Add import for: ${element.shapeName}" else "Remove unnecessary qualifier"
                        ).range(namespaceId.textRange)
                            .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                            .withFix(fix)
                            .create()
                    }
                }
            }
        }
    },
    IMPORT_CONFLICT {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyShape) {
                val conflictingImport = element.model.imports.find {
                    element.shapeName == it.shapeId.shapeName
                }
                if (conflictingImport != null) {
                    holder.newAnnotation(
                        ERROR,
                        "'${element.shapeName}' conflicts with the imported shape '${conflictingImport.shapeId.text}'"
                    )
                        .range(element.nameIdentifier.textRange)
                        .withFix(SmithyRemoveImportQuickFix(conflictingImport))
                        .create()
                }
            }
        }
    },
    UNUSED_IMPORT {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyImport && element in SmithyImportOptimizer.unusedImports(element.containingFile)) {
                holder.newAnnotation(INFORMATION, "Unused import")
                    .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                    .withFix(SmithyRemoveUnusedImportsQuickFix)
                    .create()
            }
        }
    },
    REFERENCE_INSPECTION {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element is SmithyElidedMember && element.declaration == null) {
                holder.newAnnotation(ERROR, "Unresolved member: ${element.name}")
                    .range(element.nameIdentifier.textRange)
                    .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                    .create()
            }
            when (val reference = element.reference) {
                is SmithyKeyReference -> {
                    if (!reference.isSoft && reference.resolve() == null) {
                        holder.newAnnotation(ERROR, "Unresolved member: ${reference.key.text}")
                            .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                            .create()
                    }
                }
                is SmithyMemberReference -> {
                    if (!reference.isSoft && reference.resolve() == null) {
                        holder.newAnnotation(ERROR, "Unresolved member: ${reference.id.text}")
                            .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                            .create()
                    }
                }
                is SmithyShapeReference -> {
                    val target = reference.resolve()
                    if (target == null) {
                        if (!reference.isSoft) {
                            holder.newAnnotation(ERROR, "Unresolved shape: ${element.text}")
                                .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                                .also {
                                    if (element is SmithyShapeId && getParentOfType(
                                            element,
                                            SmithyMetadata::class.java
                                        ) == null
                                    ) {
                                        it.withFix(SmithyImportShapeQuickFix(element.shapeName, element.containingFile))
                                    }
                                }
                                .create()
                        }
                    } else if (element is SmithyShapeId) {
                        if (requiresPrivateAccessCheck(element.parent)) {
                            val enclosingNamespace = (element.containingFile as? SmithyFile)?.model?.namespace
                            if (target.namespace != enclosingNamespace && target.hasTrait("smithy.api", "private")) {
                                holder.newAnnotation(
                                    ERROR,
                                    "${element.shapeName} cannot be referenced outside ${target.namespace}"
                                ).create()
                            }
                        }
                        if (target.hasTrait("smithy.api", "deprecated")) {
                            holder.newAnnotation(
                                HighlightSeverity.WARNING,
                                "${element.shapeName} is marked as @deprecated and could be removed in the future"
                            ).highlightType(ProblemHighlightType.LIKE_DEPRECATED).create()
                        }
                        if (target.hasTrait("smithy.api", "unstable")) {
                            holder.newAnnotation(
                                HighlightSeverity.WARNING,
                                "${element.shapeName} is marked as @unstable and could change in the future"
                            ).highlightType(ProblemHighlightType.WARNING).create()
                        }
                    }
                }
            }
        }

        private fun requiresPrivateAccessCheck(element: PsiElement): Boolean {
            //Note: these parent checks represent the situations where relationships are not added, so
            //private shape access is not validated during build
            if (element is SmithyMemberId) return requiresPrivateAccessCheck(element.parent)
            //Node values do not introduce relationships
            return element !is SmithyArray && element !is SmithyEntry && element !is SmithyTraitBody
                    //Imports are ignored as all their usages will be annotated
                    && element !is SmithyImport
                    //Traits are currently ignored (https://github.com/awslabs/smithy/issues/1369)
                    && element !is SmithyTrait
        }
    }
}

private fun AnnotationHolder.highlight(key: TextAttributesKey) =
    newSilentAnnotation(INFORMATION).textAttributes(key).create()

private fun AnnotationHolder.highlight(key: TextAttributesKey, range: TextRange) =
    newSilentAnnotation(INFORMATION).range(range).textAttributes(key).create()

private fun AnnotationHolder.highlight(severity: HighlightSeverity, message: String) =
    newAnnotation(severity, message).create()

private fun AnnotationHolder.highlight(
    severity: HighlightSeverity, message: String, key: TextAttributesKey, range: TextRange
) = newAnnotation(severity, message).textAttributes(key).range(range).create()