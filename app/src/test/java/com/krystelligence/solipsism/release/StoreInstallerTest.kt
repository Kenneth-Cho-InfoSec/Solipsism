package com.krystelligence.solipsism.release

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StoreInstallerTest {
    @Test fun `fdroid clients are treated as store installers`() {
        assertThat(isStoreInstaller("org.fdroid.fdroid")).isTrue()
        assertThat(isStoreInstaller("org.fdroid.basic")).isTrue()
        assertThat(isStoreInstaller("org.fdroid.fdroid.privileged")).isTrue()
    }

    @Test fun `sideloads and other installers still get update prompts`() {
        assertThat(isStoreInstaller(null)).isFalse()
        assertThat(isStoreInstaller("")).isFalse()
        assertThat(isStoreInstaller("com.android.vending")).isFalse()
        assertThat(isStoreInstaller("com.google.android.packageinstaller")).isFalse()
    }
}
