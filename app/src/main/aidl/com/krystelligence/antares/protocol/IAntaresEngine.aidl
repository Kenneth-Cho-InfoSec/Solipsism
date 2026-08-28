/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.krystelligence.antares.protocol;

import android.os.Bundle;
import com.krystelligence.antares.protocol.IAntaresSession;
import com.krystelligence.antares.protocol.IAntaresSessionCallback;

/** Stable, versioned boundary exposed by the separately installed Antares engine. */
interface IAntaresEngine {
    int getProtocolVersion();
    String getEngineVersion();
    Bundle getCapabilities();
    IAntaresSession createSession(in Bundle configuration, IAntaresSessionCallback callback);
}
