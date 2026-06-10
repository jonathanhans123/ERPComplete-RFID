package com.erpcomplete.rfid.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun String.isBenignCancellationMessage(): Boolean =
    contains("left the composition", ignoreCase = true) ||
        contains("Job was cancelled", ignoreCase = true)

fun Throwable.isBenignCancellation(): Boolean =
    this is CancellationException || message?.isBenignCancellationMessage() == true

/**
 * Launch work tied to a composable [CoroutineScope] without surfacing navigation/dispose
 * cancellations as user-visible errors.
 */
fun CoroutineScope.launchWorkflow(
    setLoading: ((Boolean) -> Unit)? = null,
    onError: (String) -> Unit,
    onSuccess: ((String) -> Unit)? = null,
    block: suspend CoroutineScope.() -> String?,
): Job = launch {
    setLoading?.invoke(true)
    try {
        block()?.let { onSuccess?.invoke(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!e.isBenignCancellation()) {
            onError(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong")
        }
    } finally {
        setLoading?.invoke(false)
    }
}
