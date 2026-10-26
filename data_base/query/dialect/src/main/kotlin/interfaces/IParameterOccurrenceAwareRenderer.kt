package gog.my_project.data_base.query.renderer.interfaces

/**
 * Opt-in contract for renderers used by parameter-aware rendering.
 *
 * Implementations must route every emitted parameter occurrence and legacy placeholder source
 * through [IParameterRenderContext], and render the same AST/configuration deterministically in
 * the named and positional passes.
 */
interface IParameterOccurrenceAwareRenderer
