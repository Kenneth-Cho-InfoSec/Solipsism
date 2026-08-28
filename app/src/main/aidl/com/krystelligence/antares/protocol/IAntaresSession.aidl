/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.krystelligence.antares.protocol;

import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.view.MotionEvent;

/** One isolated browsing session hosted by Antares. */
interface IAntaresSession {
    Bundle attachSurface(int displayId, in Bundle hostConfiguration, int width, int height);
    Bundle surface();
    oneway void resize(int width, int height);
    oneway void loadUrl(String url);
    /** Loads generated offline HTML from a descriptor owned by Solipsism. */
    oneway void loadHtml(in ParcelFileDescriptor htmlFile);
    oneway void goBack();
    oneway void goForward();
    oneway void reload();
    oneway void stop();
    /** Changes the HTTP and JavaScript user-agent identity for subsequent loads. */
    oneway void setUserAgent(String userAgent);
    /** Replaces the request-blocking policy used by this engine process. */
    void setContentBlocking(in ParcelFileDescriptor policyFile, boolean blockAds, boolean blockGifs);
    /**
     * Updates embedded input ownership and returns only after the renderer's main thread has
     * applied the change. Browser chrome uses this acknowledgement before requesting the IME.
     */
    boolean setInputEnabled(boolean enabled);
    /** Forwards touch input when Solipsism's browser chrome is composited above the renderer. */
    oneway void dispatchTouchEvent(in MotionEvent event);
    /** Performs a primary-pointer click at renderer-local coordinates. */
    oneway void click(float x, float y);
    /** Applies a renderer-local one-finger scroll delta. */
    oneway void scroll(int dx, int dy, int x, int y);
    /** Describes the nearest interactive DOM element at renderer-local physical coordinates. */
    oneway void probeElement(int requestId, float x, float y);
    oneway void setForeground(boolean foreground);
    oneway void close();
}
