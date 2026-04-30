package com.krystelligence.solipsism.utils

object Preconditions {

    /**
     * Ensure that an object is not null and throw a RuntimeException if it is null.
     *
     * @param value check nullness on this object.
     */
    @JvmStatic
    fun checkNonNull(value: Any?) {
        if (value == null) {
            throw RuntimeException("Object must not be null")
        }
    }
}
