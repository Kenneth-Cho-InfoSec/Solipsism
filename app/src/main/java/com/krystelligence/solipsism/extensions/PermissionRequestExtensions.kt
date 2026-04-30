package com.krystelligence.solipsism.extensions

import android.Manifest
import android.webkit.PermissionRequest

/**
 * Returns the permissions retrieved from [Manifest.permission] which are required by the requested
 * resources. If none of the resources require a permission, the list will be empty.
 */
fun PermissionRequest.requiredPermissions(): Set<String> {
    return allowedWebRtcResources().flatMap {
        when (it) {
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            )

            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> listOf(
                Manifest.permission.CAMERA
            )

            else -> emptyList()
        }
    }.toHashSet()
}

fun PermissionRequest.allowedWebRtcResources(): Array<String> {
    return resources.filter {
        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
    }.toTypedArray()
}
