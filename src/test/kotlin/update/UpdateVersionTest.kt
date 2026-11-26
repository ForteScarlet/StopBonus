package update

import config.AppConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UpdateVersionTest {
    /** Numeric app versions take precedence over revisions. Only known revisions can trigger a same-version update. */
    @Test
    fun versionAndRevisionOrdering() {
        val current = UpdateVersion.from("1.9.0", "2")

        assertTrue(UpdateVersion.from("1.10.0", "0").isNewerThan(current))
        assertTrue(UpdateVersion.from("1.9.0", "3").isNewerThan(current))
        assertFalse(UpdateVersion.from("1.9.0", "2").isNewerThan(current))
        assertFalse(UpdateVersion.from("1.9.0", "3").isNewerThan(UpdateVersion.from("1.9.0", null)))
        assertFalse(UpdateVersion.from("1.8.0", "99").isNewerThan(current))
        assertFalse(UpdateVersion.from("1.9.0-rc1", "99").isNewerThan(current))
        assertFailsWith<IllegalArgumentException> { UpdateVersion.from("invalid", "0") }
        assertFailsWith<IllegalArgumentException> { UpdateVersion.from("1.9.0", "-1") }
    }

    /** Settings written before the update preference was introduced use the startup-check default. */
    @Test
    fun olderConfigUsesDefault() {
        val oldConfig = Json.decodeFromString<AppConfig>("""{"timezone":"Asia/Tokyo"}""")
        assertTrue(oldConfig.autoCheckUpdates)
        val changed = Json.encodeToString(oldConfig.copy(autoCheckUpdates = false))
        assertFalse(Json.decodeFromString<AppConfig>(changed).autoCheckUpdates)
    }
}
