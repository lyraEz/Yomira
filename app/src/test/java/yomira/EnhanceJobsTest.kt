package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import yomira.anime4k.EnhanceJobs
import yomira.anime4k.JobState

class EnhanceJobsTest {

    private fun job(id: String, kind: String, state: String, extra: String = "") = """
        {"id":"$id","kind":"$kind","animeId":1,"episodeId":2,"sourceId":3,"episodeUrl":"/ep/$id",
         "animeTitle":"Anime","episodeName":"Episode","presetId":"AA_HQ","output":"original",
         "codec":"hevc","quality":"high","state":"$state"$extra}
    """.trimIndent()

    @Test
    fun `jobs saved before 1_13_0 keep the downloads and drop Smart Anime4K`() {
        val saved = "[" + listOf(
            job("smart", "SMART", "DOWNLOADING", ""","streamUrl":"https://example.org/v.m3u8","streamHeaders":{"Referer":"x"}"""),
            job("download", "DOWNLOAD", "PROCESSING", ""","sourceUri":"file:///video.mkv""""),
            job("queued", "DOWNLOAD", "QUEUED"),
        ).joinToString(",") + "]"

        val jobs = EnhanceJobs.decode(saved)

        assertEquals(listOf("download", "queued"), jobs.map { it.id })
        assertEquals(JobState.PROCESSING, jobs.first().state)
        assertEquals("file:///video.mkv", jobs.first().sourceUri)
    }

    @Test
    fun `a job that cannot be read is dropped alone`() {
        val broken = """{"id":"broken","state":"QUEUED"}"""
        val saved = "[" + broken + "," + job("ok", "DOWNLOAD", "PAUSED") + "]"

        assertEquals(listOf("ok"), EnhanceJobs.decode(saved).map { it.id })
    }
}
