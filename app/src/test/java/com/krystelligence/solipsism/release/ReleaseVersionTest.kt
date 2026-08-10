package com.krystelligence.solipsism.release

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ReleaseVersionTest {
    @Test fun `tags are normalised with v prefix`() {
        assertThat(ReleaseVersion.tag("6.1.0")).isEqualTo("v6.1.0")
        assertThat(ReleaseVersion.tag("v6.1.0")).isEqualTo("v6.1.0")
    }

    @Test fun `numeric release versions compare naturally`() {
        assertThat(ReleaseVersion.compare("6.1.10", "6.1.2")).isGreaterThan(0)
        assertThat(ReleaseVersion.compare("v6.1.0", "6.1.0")).isZero()
    }

    @Test fun `stable releases are newer than prereleases`() {
        assertThat(ReleaseVersion.compare("6.1.0", "6.1.0-rc1")).isGreaterThan(0)
    }
}
