package com.krystelligence.solipsism.browser.webrtc

import com.krystelligence.solipsism.extensions.allowedWebRtcResources
import com.krystelligence.solipsism.extensions.requiredPermissions
import android.webkit.PermissionRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The model that manages permission requests originating from a web page.
 */
@Singleton
class WebRtcPermissionsModel @Inject constructor() {

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

        if (requiredResources.isEmpty() || requiredResources.size != permissionRequest.resources.size) {
            permissionRequest.deny()
            return
        }

        if (resourceGrantMap[origin]?.containsAll(requiredResources.asList()) == true) {
            view.requestPermissions(requiredPermissions) { permissionsGranted ->
                if (permissionsGranted) {
                    permissionRequest.grant(requiredResources)
                } else {
                    permissionRequest.deny()
                }
            }
        } else {
            view.requestResources(origin, requiredResources) { resourceGranted ->
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
