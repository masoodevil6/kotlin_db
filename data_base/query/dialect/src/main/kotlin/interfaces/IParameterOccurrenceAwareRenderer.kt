package gog.my_project.data_base.query.renderer.interfaces

/**
 * Opt-in contract for renderers used by identity-aware dual rendering.
 *
 * Implementations must route every emitted [gog.my_project.data_base.core.query.reader.SqlParameter]
 * through [IParameterRenderContext.renderParameter] and render the same AST/configuration
 * deterministically in the named and positional passes.
 */
interface IParameterOccurrenceAwareRenderer
