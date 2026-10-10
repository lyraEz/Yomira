# Sincronizar com o AniZen

O Yomira mantém o histórico completo do AniZen, então existe uma base comum real e um `git merge` resolve a maior parte do trabalho.

Base atual registrada em [`.yomira/upstream.json`](../../.yomira/upstream.json): AniZen `master` em `becbe2d` (v0.5.215, 30/09/2026). O branch `preview` do AniZen estava 10 commits à frente, sem release; ele foi analisado mas não incorporado.

## Passo a passo

```sh
git remote add upstream https://github.com/salmanbappi/AniZen.git   # uma vez
git fetch upstream master
git switch -c sync/anizen-AAAA-MM-DD
git merge upstream/master
```

Ao resolver conflitos:

1. Mantenha os blocos marcados com `// YMR -->` e `// YMR <--`: são as mudanças do Yomira. Fora deles, prefira o código do AniZen.
2. Preserve os marcadores herdados (`// ANZ`, `// ANK`, `// KMK`, `// SY`, `// AY`).
3. Strings próprias do Yomira ficam no módulo `i18n-ymr`, que o AniZen não tem; ele não gera conflitos.
4. Depois do merge, reaplique o nome do app nas traduções herdadas:
   ```sh
   python3 scripts/yomira/rebrand_strings.py
   ```
5. Confira se surgiram novas URLs, workflows ou credenciais do autor original:
   ```sh
   git diff --stat HEAD@{1} -- .github/workflows
   grep -rn "salmanbappi\|anizen" --include=*.kt app/src/main/java | grep -v "ANZ"
   ```
   Workflows novos do AniZen que publicam releases ou postam no Discord devem ser removidos.
6. Rode `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest spotlessCheck` e abra um pull request.
7. Atualize `baseCommit`, `baseVersion` e `baseDate` em `.yomira/upstream.json`.

## Convenções do fork

- Código novo do Yomira: pacote `yomira.*` em `app/src/main/java/yomira`, ou blocos `// YMR` dentro de arquivos herdados.
- O `spotlessCheck` verifica só arquivos adicionados desde a base registrada, porque o código herdado não passa na formatação e reformatá-lo inteiro criaria conflitos em toda sincronização (`-Pyomira.spotless.all=true` verifica tudo).
- O `AGENTS.md` descreve o processo do AniZen (branches `preview`/`master`, workflows de release). No Yomira esses workflows foram substituídos; vale o processo de [RELEASES.md](RELEASES.md).
