# Serviços que dependem de credenciais

Alguns recursos herdados do AniZen usam contas, aplicativos ou chaves registradas por outra pessoa. No Yomira eles estão desligados ou documentados aqui.

| Recurso | Situação no Yomira | Como habilitar |
| --- | --- | --- |
| Discord Rich Presence (1.10.0) | Pelo Discord Social SDK (`app/libs/discord_partner_sdk.aar`, binário proprietário do Discord, ~28 MB, a variante sem voz) e a ponte `app/src/main/cpp`. Fala com o app do Discord instalado no celular: sem login, sem token. Desligado por padrão; liga em Configurações → Conexões → Discord. Usa o Application ID público da integração do Crunchyroll (card com capa em pé) ou um ID digitado pelo usuário. A versão antiga herdada do AniZen (login com token da conta) continua desligada. | Para a marca própria: crie um app em <https://discord.com/developers/applications>, suba o ícone e digite o ID em *Estilo do card → Meu próprio app*. |
| Sincronização com Google Drive | Desligada. O `client_secrets.json` era do projeto Google do autor do AniZen e foi removido. A opção some da lista de serviços de sincronização. | Crie um cliente OAuth do tipo *Desktop* no Google Cloud, coloque o JSON em `app/src/main/assets/client_secrets.json` (ignorado pelo Git) e compile. |
| SyncYomi | Disponível; usa um servidor indicado por você. | — |
| Assistente de IA | Disponível e desligado por padrão; usa a chave de API que você informar. A verificação remota de bloqueio, que consultava um repositório do autor do AniZen, foi removida. | Configurações do assistente, com sua própria chave. |
| Rastreadores (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl, Trakt, TMDB) | Mantidos como herdados. O login usa os registros OAuth e chaves de API dos projetos de origem (Aniyomi/AniZen), com retorno pelos esquemas `tachiyomi://` e `anizen://`. Podem deixar de funcionar se os donos desses registros os mudarem, e, com o AniZen instalado no mesmo aparelho, o Android pode perguntar qual app deve receber o retorno do login. O login também exige um navegador no aparelho. | Para credenciais próprias, registre um aplicativo em cada serviço e altere os `clientId`/`REDIRECT_URL` em `app/src/main/java/eu/kanade/tachiyomi/data/track/*`. |
| Atualizador do app | Consulta as releases públicas de `lyraEz/Yomira-releases` no GitHub. | — |
| TMDB (nomes, descrições e miniaturas dos episódios) | Usa a chave digitada em Configurações → Player → *Metadados e pular abertura*; sem ela, a chave do rastreador TMDB ou a embutida no APK pelo segredo `YOMIRA_TMDB_API_KEY`. | Crie uma chave em <https://www.themoviedb.org/settings/api>. |
| Anime Skip (tempos de abertura) | Segunda fonte, depois do AniSkip. Usa o client ID digitado no app ou o embutido pelo segredo `YOMIRA_ANIME_SKIP_CLIENT_ID`. | Crie um client ID na sua conta do anime-skip.com. |
| Telemetria (Firebase) | Não incluída. Só seria compilada com `-Pinclude-telemetry=true` e um `google-services.json`, que o projeto não traz. | — |
