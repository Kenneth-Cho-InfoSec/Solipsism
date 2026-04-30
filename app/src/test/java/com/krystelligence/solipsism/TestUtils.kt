@file:Suppress("NOTHING_TO_INLINE")

package com.krystelligence.solipsism

/**
 * Use to implement an unimplemented method.
 */
inline fun unimplemented(): Nothing {
    throw NotImplementedError("Not implemented")
}
