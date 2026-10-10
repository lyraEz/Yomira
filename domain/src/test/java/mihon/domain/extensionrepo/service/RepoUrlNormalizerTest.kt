package mihon.domain.extensionrepo.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// YMR -->
class RepoUrlNormalizerTest {

    @Test
    fun `the Rede legacy index resolves to its repo branch`() {
        RepoUrlNormalizer.candidates("https://raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json") shouldBe
            listOf("https://raw.githubusercontent.com/lyraEz/Rede/repo")
    }

    @Test
    fun `repo json and a trailing slash resolve to the same base`() {
        RepoUrlNormalizer.candidates("https://raw.githubusercontent.com/Awerkori/anime-extensoes/repo/repo.json") shouldBe
            listOf("https://raw.githubusercontent.com/Awerkori/anime-extensoes/repo")
        RepoUrlNormalizer.candidates("https://raw.githubusercontent.com/Awerkori/anime-extensoes/repo/") shouldBe
            listOf("https://raw.githubusercontent.com/Awerkori/anime-extensoes/repo")
    }

    @Test
    fun `a store-v2 address falls back to the directory that holds it`() {
        RepoUrlNormalizer.candidates("https://raw.githubusercontent.com/lyraEz/Rede/repo/store-v2.json") shouldBe
            listOf("https://raw.githubusercontent.com/lyraEz/Rede/repo")
        RepoUrlNormalizer.looksLikeStoreV2("https://raw.githubusercontent.com/lyraEz/Rede/repo/store-v2-1000-7.json") shouldBe true
        RepoUrlNormalizer.looksLikeStoreV2("https://raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json") shouldBe false
    }

    @Test
    fun `a GitHub tree page also tries the raw address`() {
        RepoUrlNormalizer.candidates("https://github.com/Awerkori/anime-extensoes/tree/repo") shouldBe listOf(
            "https://github.com/Awerkori/anime-extensoes/tree/repo",
            "https://raw.githubusercontent.com/Awerkori/anime-extensoes/repo",
        )
    }

    @Test
    fun `a missing scheme defaults to https and whitespace is ignored`() {
        RepoUrlNormalizer.candidates("  raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json \n") shouldBe
            listOf("https://raw.githubusercontent.com/lyraEz/Rede/repo")
    }

    @Test
    fun `text that is not a web address is rejected`() {
        RepoUrlNormalizer.candidates("") shouldBe null
        RepoUrlNormalizer.candidates("ftp://example.org/repo") shouldBe null
        RepoUrlNormalizer.candidates("not a url") shouldBe null
    }
}
// YMR <--
