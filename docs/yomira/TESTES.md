# Testes

## Verificação automática (a cada versão)

| Verificação | Comando |
| --- | --- |
| Compilação | `./gradlew :app:compileDebugKotlin` |
| Testes unitários | `./gradlew :app:testDebugUnitTest` |
| Formatação dos arquivos novos | `./gradlew spotlessCheck` |
| APKs de release assinados | `./gradlew :app:assembleRelease -Penable-updater=true` |
| Inspeção dos APKs (pacote, SDK, ABIs, launcher) | `scripts/yomira/inspect-apks.sh app/build/outputs/apk/release` |
| Assinatura conferida com o certificado do projeto | `scripts/yomira/package-release.sh app/build/outputs/apk/release dist` |

Testes cobertos no app: navegação entre episódios (`EpisodeNavigationTest`), roteamento de teclas físicas no player (`PlayerKeyRouterTest`) e normalização de endereços de repositório (`RepoUrlNormalizerTest`), além dos herdados.

## Roteiro no celular

| # | Teste | Resultado esperado |
| --- | --- | --- |
| 1 | Instalar por cima da versão anterior | Biblioteca, histórico e configurações mantidos |
| 2 | Adicionar o repositório Rede e instalar uma extensão | Extensão aparece em Fontes |
| 3 | Buscar, abrir um anime e tocar um episódio | Vídeo toca; controles de vidro aparecem e somem |
| 4 | Próximo episódio várias vezes seguidas | O player nunca fecha sozinho; em erro mostra as opções |
| 5 | Assistir 20 minutos com as estatísticas ligadas (Fluidez → Contador de quadros) | VO, DEC e LATE parados; TEMP em ok/warm |
| 6 | Restaurar padrões do player | Ajustes voltam ao padrão leve |
| 7 | Rolar Biblioteca, Atualizações, Histórico, Navegar e Mais | Sem engasgos |

Para medir quadros da interface: `adb shell dumpsys gfxinfo com.lyraez.yomira`.
