# Serviços que dependem de credenciais

Alguns recursos herdados do AniZen usam contas, aplicativos ou chaves registradas por outra pessoa. No Yomira eles estão desligados ou documentados aqui.

| Recurso | Situação no Yomira | Como habilitar |
| --- | --- | --- |
| Discord Rich Presence | Desligado. Usava o aplicativo Discord registrado pelo autor do AniZen. A tela de conexões mostra o aviso. | Registre um aplicativo em <https://discord.com/developers/applications> e compile com `-Pyomira.discordAppId=ID_DO_APP`. |
| Sincronização com Google Drive | Desligada. O `client_secrets.json` era do projeto Google do autor do AniZen e foi removido. A opção some da lista de serviços de sincronização. | Crie um cliente OAuth do tipo *Desktop* no Google Cloud, coloque o JSON em `app/src/main/assets/client_secrets.json` (ignorado pelo Git) e compile. |
| SyncYomi | Disponível; usa um servidor indicado por você. | — |
| Assistente de IA | Disponível e desligado por padrão; usa a chave de API que você informar. A verificação remota de bloqueio, que consultava um repositório do autor do AniZen, foi removida. | Configurações do assistente, com sua própria chave. |
| Rastreadores (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl, Trakt, TMDB) | Mantidos como herdados. O login usa os registros OAuth e chaves de API dos projetos de origem (Aniyomi/AniZen), com retorno pelos esquemas `tachiyomi://` e `anizen://`. Podem deixar de funcionar se os donos desses registros os mudarem, e, com o AniZen instalado no mesmo aparelho, o Android pode perguntar qual app deve receber o retorno do login. O login também exige um navegador no aparelho. | Para credenciais próprias, registre um aplicativo em cada serviço e altere os `clientId`/`REDIRECT_URL` em `app/src/main/java/eu/kanade/tachiyomi/data/track/*`. |
| Atualizador do app | Consulta as releases públicas de `lyraEz/Yomira` no GitHub. | — |
| Telemetria (Firebase) | Não incluída. Só seria compilada com `-Pinclude-telemetry=true` e um `google-services.json`, que o projeto não traz. | — |
