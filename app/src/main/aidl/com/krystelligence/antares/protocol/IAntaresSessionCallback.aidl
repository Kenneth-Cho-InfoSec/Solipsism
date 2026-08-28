/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.krystelligence.antares.protocol;

import android.os.Bundle;

/** Asynchronous session state delivered to Solipsism. */
oneway interface IAntaresSessionCallback {
    void onReady();
    void onLoadStarted();
    void onLoadEnded();
    void onTitleChanged(String title);
    void onUrlChanged(String url);
    void onHistoryChanged(boolean canGoBack, boolean canGoForward);
    void onAlert(String message);
    void onMediaRequest(in Bundle request);
    void onElementProbeResult(int requestId, String descriptor);
    void onEngineTerminated(String reason);
}
