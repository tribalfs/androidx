package org.jetbrains.kotlin.r4a.analysis

import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.Renderers

object R4ADefaultErrorMessages : DefaultErrorMessages.Extension {
    private val MAP = DiagnosticFactoryToRendererMap("R4A")
    override fun getMap() = MAP

    init {
        MAP.put(
            R4AErrors.DUPLICATE_ATTRIBUTE,
            "Duplicate attribute; Attributes must appear at most once per tag."
        )
        MAP.put(
            R4AErrors.OPEN_COMPONENT,
            "Component is open. Components cannot be an open or abstract class."
        )
        MAP.put(
            R4AErrors.MISMATCHED_ATTRIBUTE_TYPE,
            "Attribute {0} expects type ''{1}'', found ''{2}''",
            Renderers.STRING,
            Renderers.RENDER_TYPE,
            Renderers.RENDER_TYPE
        )
        MAP.put(
            R4AErrors.UNRESOLVED_ATTRIBUTE_KEY,
            "No valid attribute on ''{0}'' found with key ''{1}'' and type ''{2}''",
            Renderers.COMPACT,
            Renderers.STRING,
            Renderers.RENDER_TYPE
        )
        MAP.put(
            R4AErrors.MISMATCHED_ATTRIBUTE_TYPE_NO_SINGLE_PARAM_SETTER_FNS,
            "Setters with multiple arguments are currently unsupported. Found: ''{0}''",
            Renderers.COMPACT
        )
        MAP.put(
            R4AErrors.MISSING_REQUIRED_ATTRIBUTES,
            "Missing required attributes: {0}",
            Renderers.commaSeparated(Renderers.COMPACT)
        )
        MAP.put(
            R4AErrors.INVALID_TAG_TYPE,
            "Invalid KTX tag type. Found ''{0}'', Expected ''{1}''",
            Renderers.RENDER_TYPE,
            Renderers.commaSeparated(Renderers.RENDER_TYPE)
        )
        MAP.put(
            R4AErrors.SUSPEND_FUNCTION_USED_AS_SFC,
            "Suspend functions are not allowed to be used as R4A Components"
        )
        MAP.put(
            R4AErrors.INVALID_TYPE_SIGNATURE_SFC,
            "Only Unit-returning functions are allowed to be used as R4A Components"
        )
        MAP.put(
                R4AErrors.INVALID_TAG_DESCRIPTOR,
                "Invalid KTX tag type. Expected ''{0}''",
                Renderers.commaSeparated(Renderers.RENDER_TYPE)
        )
        MAP.put(
            R4AErrors.SVC_INVOCATION,
            "Stateless Functional Components (SFCs) should not be invoked, use <{0} /> syntax instead",
            Renderers.STRING
        )
        MAP.put(
            R4AErrors.CHILDREN_INVOCATION,
            "Suspicious invocation; consider using the <{0} /> syntax instead",
            Renderers.STRING
        )
        MAP.put(
            R4AErrors.CHILDREN_NOT_COMPOSABLE,
            "Suspicious definition; consider making {0} composable by adding an @Composable annotation to the type.  Recommended type: `@Composable() ()->Unit`",
            Renderers.STRING
        )
        MAP.put(
            R4AErrors.NON_COMPOSABLE_INVOCATION,
            "{0} `{1}` must be marked as @Composable in order to be used as a KTX tag",
            Renderers.STRING,
            Renderers.STRING
        )
        MAP.put(
                R4AErrors.KTX_IN_NON_COMPOSABLE,
                "Stateless Functional Components (SFCs) containing KTX Tags should be marked with the @Composable annotation"
        )
        MAP.put(
            R4AErrors.UNRESOLVED_TAG,
            "Unresolved tag"
        )
    }
}