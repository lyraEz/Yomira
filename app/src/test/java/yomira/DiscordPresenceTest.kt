package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yomira.discord.DiscordNative
import yomira.discord.PresenceBuilder
import yomira.discord.PresenceButtonOption
import yomira.discord.PresenceInput
import yomira.discord.PresenceOptions
import yomira.discord.PresencePreferences

class DiscordPresenceTest {

    private val now = 1_800_000_000L

    private fun input(paused: Boolean = false) = PresenceInput(
        extensionTitle = "Attack on Titan (Dublado)",
        tmdbTitle = "Attack on Titan",
        extensionEpisodeName = "Episódio 1",
        tmdbEpisodeName = "To You, in 2000 Years",
        episodeOverview = "Eren sees a Titan for the first time.",
        numberLabel = "Episódio 1",
        season = 1,
        episode = 1,
        posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
        episodeImageUrl = "https://image.tmdb.org/t/p/w780/still.jpg",
        sourceName = "AnimeFire",
        positionSeconds = 300,
        durationSeconds = 1440,
        paused = paused,
        nowSeconds = now,
        aniListUrl = "https://anilist.co/anime/16498",
    )

    @Test
    fun `playing card uses TMDB names, the badge and a progress bar`() {
        val card = PresenceBuilder.build(input(), PresenceOptions())!!
        assertEquals("Crunchyroll", card.fields[DiscordNative.NAME])
        assertEquals("Attack on Titan", card.fields[DiscordNative.DETAILS])
        assertEquals("To You, in 2000 Years", card.fields[DiscordNative.STATE])
        assertEquals("Season 1, Episode 1", card.fields[DiscordNative.LARGE_TEXT])
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", card.fields[DiscordNative.LARGE_IMAGE])
        assertEquals("https://anilist.co/anime/16498", card.fields[DiscordNative.DETAILS_URL])
        assertEquals(now - 300, card.start)
        assertEquals(now - 300 + 1440, card.end)
        assertEquals(3, card.type)
        assertEquals("Watch on Crunchyroll", card.fields[DiscordNative.BUTTON1_LABEL])
        assertTrue(card.fields[DiscordNative.BUTTON1_URL].startsWith("https://www.crunchyroll.com/search?q=Attack%20on%20Titan"))
    }

    @Test
    fun `paused card freezes the time or hides`() {
        val frozen = PresenceBuilder.build(input(paused = true), PresenceOptions())!!
        assertEquals("⏸ 5:00 / 24:00", frozen.fields[DiscordNative.STATE])
        assertEquals(0L, frozen.start)
        assertEquals(0L, frozen.end)
        assertTrue(frozen.paused)
        assertNull(PresenceBuilder.build(input(paused = true), PresenceOptions(pausedMode = "hide")))
        val kept = PresenceBuilder.build(input(paused = true), PresenceOptions(pausedMode = "keep"))!!
        assertEquals("To You, in 2000 Years", kept.fields[DiscordNative.STATE])
    }

    @Test
    fun `extension markers and generic names are cleaned`() {
        val options = PresenceOptions(titleSource = "extension", tmdbEpisodeNames = false)
        val plain = input().copy(extensionEpisodeName = "T2E16 - Forte Vakmakro")
        assertEquals("Attack on Titan", PresenceBuilder.title(plain, options))
        assertEquals("Forte Vakmakro", PresenceBuilder.episodeName(plain, options))
        val generic = input().copy(extensionEpisodeName = "Episódio 7", tmdbEpisodeName = null, numberLabel = "Episódio 7")
        assertEquals("Episódio 7", PresenceBuilder.episodeName(generic, PresenceOptions()))
    }

    @Test
    fun `lines, images, time and status follow the options`() {
        val card = PresenceBuilder.build(
            input(),
            PresenceOptions(
                detailsLine = "episode",
                stateLine = "overview",
                largeImage = "episode",
                smallImage = "poster",
                timeMode = "elapsed",
                statusDisplay = "details",
                badge = false,
                links = false,
                activityType = "playing",
                buttons = listOf(
                    PresenceButtonOption(PresencePreferences.BUTTON_ANILIST),
                    PresenceButtonOption(PresencePreferences.BUTTON_CUSTOM, "My list", "https://example.com/?q={title}"),
                ),
            ),
        )!!
        assertEquals("To You, in 2000 Years", card.fields[DiscordNative.DETAILS])
        assertEquals("Eren sees a Titan for the first time.", card.fields[DiscordNative.STATE])
        assertEquals("https://image.tmdb.org/t/p/w780/still.jpg", card.fields[DiscordNative.LARGE_IMAGE])
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", card.fields[DiscordNative.SMALL_IMAGE])
        assertEquals("Attack on Titan", card.fields[DiscordNative.LARGE_TEXT])
        assertEquals("", card.fields[DiscordNative.DETAILS_URL])
        assertEquals(now - 300, card.start)
        assertEquals(0L, card.end)
        assertEquals(2, card.statusDisplay)
        assertEquals(0, card.type)
        assertEquals("View on AniList", card.fields[DiscordNative.BUTTON1_LABEL])
        assertEquals("https://example.com/?q=Attack%20on%20Titan", card.fields[DiscordNative.BUTTON2_URL])
    }

    @Test
    fun `fields stay inside Discord limits`() {
        assertEquals("", PresenceBuilder.text("a"))
        assertEquals(128, PresenceBuilder.text("x".repeat(400)).length)
        val card = PresenceBuilder.build(
            input().copy(posterUrl = "ftp://nope", episodeImageUrl = null, aniListUrl = null),
            PresenceOptions(buttons = listOf(PresenceButtonOption(PresencePreferences.BUTTON_MAL))),
        )!!
        assertEquals("", card.fields[DiscordNative.LARGE_IMAGE])
        assertEquals("", card.fields[DiscordNative.BUTTON1_LABEL])
    }

    @Test
    fun `small clock drift is the same card`() {
        val a = PresenceBuilder.build(input(), PresenceOptions())!!
        val b = PresenceBuilder.build(input().copy(nowSeconds = now + 1, positionSeconds = 300), PresenceOptions())!!
        assertTrue(a.sameAs(b))
        val c = PresenceBuilder.build(input().copy(positionSeconds = 900), PresenceOptions())!!
        assertTrue(!a.sameAs(c))
    }
}
