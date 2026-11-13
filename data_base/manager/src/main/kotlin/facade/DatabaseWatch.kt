package gog.my_project.data_base_manager.facade

import gog.my_project.data_base.core.events.DatabaseEventListener
import gog.my_project.data_base.core.events.DatabaseWatchSubscription

/**
 * Registers a Watch on the concrete Handle represented by this API value. Existing custom
 * DatabaseHandleApi implementations remain source-compatible and fail closed when they do not
 * expose the built-in Handle capability.
 */
fun DatabaseHandleApi.watch(listener: DatabaseEventListener): DatabaseWatchSubscription {
    val managedHandle = this as? DatabaseHandle
        ?: throw UnsupportedOperationException(
            "This DatabaseHandleApi implementation does not expose a managed Database Watch",
        )
    return managedHandle.watch(listener)
}
