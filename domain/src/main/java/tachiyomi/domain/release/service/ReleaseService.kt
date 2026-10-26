package tachiyomi.domain.release.service

import tachiyomi.domain.release.model.Release

interface ReleaseService {

    suspend fun latest(repository: String): Release

    suspend fun releaseNotes(repository: String): List<Release>
}
