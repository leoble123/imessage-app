package com.leo.imessage.util

import com.leo.imessage.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The changelog is written by hand, which means it can be forgotten.
 *
 * The failure mode is quiet and embarrassing rather than loud: a build that
 * bumps its version without adding an entry shows the *previous* release's
 * notes under the new number, and the person reading them has no way to know.
 * These make that a failed build instead.
 */
class ChangelogTest {

    @Test
    fun `this build is described`() {
        val current = Changelog.current(BuildConfig.VERSION_CODE)
        assertTrue(
            "No changelog entry for version code ${BuildConfig.VERSION_CODE}. " +
                "Add one to the top of Changelog.releases.",
            current != null,
        )
        assertEquals(BuildConfig.VERSION_NAME, current!!.versionName)
    }

    @Test
    fun `the newest release is first`() {
        assertEquals(BuildConfig.VERSION_CODE, Changelog.releases.first().versionCode)
    }

    @Test
    fun `releases run newest to oldest`() {
        val codes = Changelog.releases.map { it.versionCode }
        assertEquals(codes.sortedDescending(), codes)
    }

    @Test
    fun `no version is described twice`() {
        val codes = Changelog.releases.map { it.versionCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `every release says something`() {
        Changelog.releases.forEach { release ->
            assertTrue("${release.versionName} has no headline", release.headline.isNotBlank())
            assertTrue("${release.versionName} has no changes", release.changes.isNotEmpty())
            assertTrue("${release.versionName} has no date", release.date.isNotBlank())
            release.changes.forEach {
                assertTrue("${release.versionName} has an empty line", it.text.isNotBlank())
            }
        }
    }
}
