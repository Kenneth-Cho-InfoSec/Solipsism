package com.krystelligence.solipsism.browser.webrtc

import com.krystelligence.solipsism.extensions.allowedWebRtcResources
import com.krystelligence.solipsism.extensions.requiredPermissions
import com.krystelligence.solipsism.preference.SitePermissionDecision
import com.krystelligence.solipsism.preference.SitePermissionKey
import com.krystelligence.solipsism.preference.SitePermissionStore
import android.webkit.PermissionRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The model that manages permission requests originating from a web page.
 */
@Singleton
class WebRtcPermissionsModel @Inject constructor(
    private val sitePermissionStore: SitePermissionStore
) {

    private val resourceGrantMap = mutableMapOf<String, HashSet<String>>()

    /**
     * Request a permission from the user to use certain device resources. Will call either
     * [PermissionRequest.grant] or [PermissionRequest.deny] based on the response received from the
     * user.
     *
     * @param permissionRequest the request being made.
     * @param view the view that will delegate requesting permissions or resources from the user.
     */
    fun requestPermission(permissionRequest: PermissionRequest, view: WebRtcPermissionsView) {
        val origin = permissionRequest.origin.toString()
        val requiredResources = permissionRequest.allowedWebRtcResources()
        val requiredPermissions = permissionRequest.requiredPermissions()

        val decisions = buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requiredResources) {
                add(sitePermissionStore.decision(origin, SitePermissionKey.CAMERA))
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requiredResources) {
                add(sitePermissionStore.decision(origin, SitePermissionKey.MICROPHONE))
            }
        }

        if (decisions.any { it == SitePermissionDecision.DENY }) {
            permissionRequest.deny()
            return
        }
        val siteAllowsAll = decisions.isNotEmpty() &&
            decisions.all { it == SitePermissionDecision.ALLOW }

        if (requiredResources.isEmpty() || requiredResources.size != permissionRequest.resources.size) {
            permissionRequest.deny()
            return
        }

        if (!decisions.any { it == SitePermissionDecision.ASK } &&
            (siteAllowsAll ||
            resourceGrantMap[origin]?.containsAll(requiredResources.asList()) == true)) {
            view.requestPermissions(requiredPermissions) { permissionsGranted ->
                if (permissionsGranted) {
                    permissionRequest.grant(requiredResources)
                } else {
                    permissionRequest.deny()
                }
            }
        } else {
            view.requestResources(origin, requiredResources) { resourceGranted ->
                val siteDecision = if (resourceGranted) {
                    SitePermissionDecision.ALLOW
                } else {
                    SitePermissionDecision.DENY
                }
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requiredResources) {
                    sitePermissionStore.setDecision(origin, SitePermissionKey.CAMERA, siteDecision)
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requiredResources) {
                    sitePermissionStore.setDecision(origin, SitePermissionKey.MICROPHONE, siteDecision)
                }
                if (resourceGranted) {
                    view.requestPermissions(requiredPermissions) { permissionsGranted ->
                        if (permissionsGranted) {
                            resourceGrantMap[origin]?.addAll(requiredResources)
                                ?: resourceGrantMap.put(origin, requiredResources.toHashSet())
                            permissionRequest.grant(requiredResources)
                        } else {
                            permissionRequest.deny()
                        }
                    }
                } else {
                    permissionRequest.deny()
                }
            }
        }
    }

}
