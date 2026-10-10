# Relatório de testes

## 1. O que foi verificado nesta versão

Ambiente: contêiner Linux sem aceleração de virtualização (não foi possível rodar emulador Android TV) e **sem acesso a um Fire TV Stick**. Tudo abaixo foi verificado fora do aparelho.

| Verificação | Resultado |
| --- | --- |
| Compilação Kotlin (`:app:compileDebugKotlin`) | OK |
| Testes unitários novos do player (`EpisodeNavigationTest`, 12 casos; `PlayerKeyRouterTest`, 11 casos) | 23/23 OK |
| Testes unitários novos de repositório (`RepoUrlNormalizerTest`, 6 casos) | 6/6 OK |
| Testes do app (`:app:testDebugUnitTest`, herdados + novos) | 52/52 OK |
| Testes herdados do domínio | 2 falhas que já existem na base do AniZen (`LibraryFlagsTest`, `FetchIntervalTest`); um terceiro (`FetchIntervalScheduledTest`) não compilava e foi corrigido |
| `spotlessCheck` (arquivos novos do Yomira) | OK |
| `detekt` | a tarefa da raiz do projeto herdado não tem fontes configuradas e não analisa nada (já era assim no AniZen) |
| APKs de release assinados e inspecionados | OK (seção 4) |
| Repositórios Rede e Project Nox Anime | `repo.json` e `index.min.json` presentes e válidos (verificado por HTTP); normalização de URL coberta por teste |

O que **não** foi possível verificar aqui: qualquer comportamento em tela, foco do D-pad, instalação de extensão pelo PackageInstaller, reprodução de vídeo, desempenho e memória. Esses itens dependem do aparelho (seção 2).

## 2. Matriz de testes no Fire TV (pendente)

Antes de começar, registre o aparelho:

```sh
adb connect IP_DO_FIRE_TV
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
adb shell cat /proc/meminfo | head -1
```

| Campo | Valor |
| --- | --- |
| Modelo | |
| Fire OS / Android | |
| API (`ro.build.version.sdk`, precisa ser ≥ 26) | |
| ABIs | |
| RAM | |
| APK instalado | |

Para acompanhar cada teste, deixe um log aberto:

```sh
adb logcat -c
adb logcat -v time | grep -iE "yomira|AndroidRuntime|PackageInstaller|ExtensionLoader|Playback error|mpv"
```

| # | Cenário | Como testar | Esperado | Resultado |
| --- | --- | --- | --- | --- |
| 1 | Instalar e abrir pelo launcher | `adb install -r Yomira-v1.0.0-armeabi-v7a.apk`; abrir em *Seus apps e canais* | Banner do Yomira aparece; o app abre | Pendente |
| 2 | Primeira configuração só com o controle | Seguir o assistente: permissões, pasta, tema | Todos os botões alcançáveis; nenhuma tela de permissão fecha o app | Pendente |
| 3a | Adicionar Rede | Repositórios → Adicionar → sugestão Rede → Adicionar | Mensagem "Repositório Rede adicionado (extensões: 1)"; RedeToons aparece em Extensões | Pendente |
| 3b | Adicionar Project Nox Anime | Mesmo fluxo com a outra sugestão | Extensões do repositório listadas por idioma | Pendente |
| 3c | Endereço store-v2 | Colar `https://raw.githubusercontent.com/lyraEz/Rede/repo/store-v2.json` | Repositório adicionado pelo `index.min.json` da mesma pasta (ou aviso, se já existir) | Pendente |
| 3d | Enviar do celular | Adicionar → Enviar do celular → ler QR no celular → enviar URL | Campo preenchido na TV | Pendente |
| 4 | Instalar, confiar e carregar | Instalar RedeToons; confirmar no sistema | Estados "Aguardando confirmação" → "Carregada · 1 fontes"; fonte em Navegar → Fontes. Se pedir confiança, aceitar e ver a fonte carregar | Pendente |
| 4b | Extensão com problema | Desligar fontes adultas e instalar uma extensão marcada 18+ (ou outra incompatível) | Aparece em "Instaladas com problemas" com motivo e diagnóstico copiável | Pendente |
| 4c | Reinstalação e atualização | Instalar a versão anterior da extensão e depois a nova (mesma assinatura); depois tentar uma com assinatura diferente | Atualiza por cima; com outra assinatura, mensagem pedindo para desinstalar antes | Pendente |
| 5 | Buscar e abrir | Busca global ou na fonte; abrir título; escolher episódio | Teclado da TV abre; foco visível; lista de episódios recebe o foco | Pendente |
| 6a | Reproduzir anime | Abrir episódio | Vídeo toca; OK mostra controles com foco em Reproduzir/Pausar | Pendente |
| 6b | Reproduzir série e filme | Idem com fontes de série/filme | Idem | Pendente |
| 6c | Controles | ←/→ com controles ocultos, ⏯, trocar legenda e áudio, velocidade, servidor | Cada ação funciona; menus navegáveis; Voltar fecha menu, depois esconde controles, depois sai | Pendente |
| 6d | Sair e retomar | Sair no meio e abrir de novo | Retoma de onde parou; histórico atualizado | Pendente |
| 6e | Formatos | Fontes que entregam MP4, HLS (m3u8) e DASH (mpd) | Todos tocam | Pendente |
| 7 | **Próximo episódio** | Botão ⏭ na tela e tecla ⏭ do controle, várias vezes seguidas; também ao terminar um episódio com reprodução automática | O player **não fecha**; se o próximo falhar, aparece a tela de erro com Tentar de novo / Servidores / Episódios | Pendente |
| 7b | Link expirado | Pausar por bastante tempo e continuar, ou simular sem rede | Tela de erro no player, sem fechar; "Tentar de novo" recarrega | Pendente |
| 8a | Criar backup | Dados e armazenamento → Criar backup | Arquivo salvo; "Enviar para o celular" baixa o arquivo no celular | Pendente |
| 8b | Restaurar backup | Restaurar backup → Receber do celular (ou arquivo da lista) | Biblioteca, histórico, categorias, repositórios e configurações restaurados | Pendente |
| 8c | Migrar do AniZen | Backup criado no AniZen restaurado no Yomira | Mesmos dados; AniZen continua intacto | Pendente |
| 9 | Atualizar o app por cima | Instalar 1.0.0, usar, depois instalar um APK com versionCode maior assinado com a mesma chave | Dados mantidos | Pendente |
| 10 | Desempenho | Durante 6a, `adb shell dumpsys meminfo com.lyraez.yomira`; ativar estatísticas do player (página 1) e observar quadros perdidos | Sem travamentos; memória estável | Pendente |
| 11 | WebView | Abrir uma fonte com verificação Cloudflare no WebView | Ponteiro move com as setas; OK marca a caixa; volta ao app com a fonte funcionando | Pendente |

## 3. Teste de regressão do próximo episódio

Automatizado: `app/src/test/java/eu/kanade/tachiyomi/ui/player/EpisodeNavigationTest.kt` cobre a escolha do episódio seguinte/anterior (fim da lista, episódio fora da lista, fillers) e garante que lista de servidores vazia, com erro ou herdada do episódio anterior resulta em "mostrar erro", nunca em fechar o player.

No aparelho (cenário 7), com o log aberto, procure por `Playback error` (erro tratado, player continua aberto) e confirme que não aparece `AndroidRuntime: FATAL` nem a activity do player sendo finalizada.

## 4. Resultados do build de release

### 1.0.0 (versionCode 10000), 10/10/2026

Compilado com `./gradlew :app:assembleRelease -Penable-updater=true` a partir do commit `54fd6b44c` (o código não mudou depois dele; os commits seguintes são só documentação e modelos do GitHub), assinado com a chave do Yomira e empacotado com `scripts/yomira/package-release.sh`.

| Verificação (aapt2 / apksigner) | armeabi-v7a | arm64-v8a | universal |
| --- | --- | --- | --- |
| Pacote / versão | `com.lyraez.yomira` 1.0.0 (10000) | igual | igual |
| minSdk / targetSdk | 26 / 36 | 26 / 36 | 26 / 36 |
| Bibliotecas nativas | só `armeabi-v7a` | só `arm64-v8a` | `armeabi-v7a` e `arm64-v8a` |
| `LEANBACK_LAUNCHER` | sim | sim | sim |
| `android:banner` | sim | sim | sim |
| Touchscreen / leanback obrigatórios | não / não | não / não | não / não |
| Assinatura | v2 + v3, 1 signatário | igual | igual |
| Certificado SHA-256 | `e4d0adb9…d677ce` (confere com `signing-certificate.sha256`) | igual | igual |
| Tamanho | 71 MB | 80 MB | 129 MB |

SHA-256 dos arquivos:

```
c5448f5e97cc2717e93eddd70062290a12905e388aeb94e6223e9da35ac3f539  Yomira-v1.0.0-armeabi-v7a.apk
c1fcd9821cae2d836ceca68d43bc25ea5bfee4e6926f70b9057599919f4bbf38  Yomira-v1.0.0-arm64-v8a.apk
f255de814c1f03e94d12ff06a5a14868a90e327960b4a15d9133a8b347e8ee41  Yomira-v1.0.0-universal.apk
```

Esses APKs ainda **não foram instalados em nenhum aparelho**. Os cenários da seção 2 continuam pendentes.
