package gog.my_project.data_base_session.dialect.exceptions

import kotlin.reflect.KClass

sealed class SessionRenderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class RendererNotFound(astType: KClass<*>) :
        SessionRenderException("No Session renderer is registered for ${astType.qualifiedName}.")

    class AmbiguousRenderer(
        astType: KClass<*>,
        candidates: List<KClass<*>>,
    ) : SessionRenderException(
        "Multiple Session renderers match ${astType.qualifiedName}: " +
            candidates.mapNotNull { it.qualifiedName }.sorted().joinToString(", ") + ".",
    )

    class InvalidAst(detail: String) :
        SessionRenderException("Invalid Session command AST: $detail")

    class RendererReturnedNull(astType: KClass<*>) :
        SessionRenderException("Session renderer for ${astType.qualifiedName} returned no SQL.")

    class RendererFailed(
        astType: KClass<*>,
        cause: Throwable,
    ) : SessionRenderException(
        "Session renderer for ${astType.qualifiedName} failed: ${cause.message ?: cause::class.qualifiedName}.",
        cause,
    )
}
