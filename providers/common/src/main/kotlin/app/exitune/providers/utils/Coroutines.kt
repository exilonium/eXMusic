package app.exitune.providers.utils

import kotlinx.coroutines.CancellationException

inline fun <T> runCatchingCancellable(block: () -> T) =
    runCatching(block).takeIf { it.exceptionOrNull() !is CancellationException }
