# Anime4K offline: Enhanced Downloads e Smart Anime4K

Estado em outubro de 2026, branch `anime4k-enhanced`. Nada disso foi testado num celular ainda: o
motor foi validado no PC (abaixo) e o resto compila e passa nos testes automáticos.

## Auditoria (antes de mexer)

| Parte | O que existe | Como foi usada |
| --- | --- | --- |
| Player | mpv (libmpv do aniyomi), com shaders Anime4K em tempo real (cópias ajustadas em `assets/shaders`, `mediump`) | Intocado. O processamento offline usa outra pasta. |
| Downloads | `Downloader`/`DownloadManager`, ffmpeg-kit 1.18 (HLS/DASH/torrent), pastas por episódio, SAF | Gancho depois de `DOWNLOADED`; `buildVideo` prefere o arquivo `.anime4k.mkv`. |
| Segundo plano | WorkManager 2.10 com serviço em primeiro plano `dataSync` | Novo worker; o serviço do WorkManager ganha `mediaProcessing` (Android 15+). |
| Mídia | Sem Media3. ffmpeg-kit sem libplacebo/Vulkan; mpv sem encoder | Media3 Transformer 1.8.0 (decode, efeito de GPU, encode por hardware). |
| GPU | OpenGL ES 3.2 em Mali (A15: Mali-G57 MC2), `EXT_color_buffer_float` | Texturas RGBA16F como o mpv. |

Alternativas avaliadas:

- **mpv `--o` + `vf=gpu`**: o encoder do mpv do aniyomi não vem compilado e o `vf_gpu` precisa de
  EGL de desktop. Descartado.
- **FFmpeg com libplacebo**: seria o caminho mais direto, mas o ffmpeg-kit do app não tem
  libplacebo nem Vulkan, e recompilar o ffmpeg-kit inteiro está fora do escopo. Descartado no app;
  usado no PC como referência.
- **Vulkan próprio**: mais trabalho e sem vantagem para shaders de fragmento como os do Anime4K.
- **OpenGL ES + Media3 Transformer**: escolhido. O Transformer cuida de decodificar, recortar
  trechos com precisão de quadro, codificar em HEVC/H.264 por hardware e lidar com variações dos
  codecs; o Anime4K entra como um `GlEffect`.

## Arquitetura

Um motor só, usado pelos dois recursos:

| Camada | Arquivos |
| --- | --- |
| Motor de shaders (C++, GLES 3) | `app/src/main/cpp/anime4k/hook_shader.*` (parser do formato do mpv), `hook_pipeline.*` (passes, tamanhos, WHEN, texturas) |
| Ponte JNI e efeito Media3 | `anime4k_jni.cpp`, `yomira/anime4k/Anime4KNative.kt`, `Anime4KEffect.kt` |
| Exportação | `Anime4KExporter.kt`: trechos de 20 s, junção e remux com ffmpeg-kit, validação |
| Tarefas | `EnhanceJob.kt`, `EnhanceJobs.kt` (lista salva em JSON), `Anime4KQueue.kt`, `Anime4KWorker.kt`, `Anime4KNotifier.kt` |
| Presets e preferências | `Anime4KPresets.kt`, `Anime4KPlanning.kt`, `Anime4KPreferences.kt` |
| Downloads | `EnhancedDownloads.kt`, `Anime4KDownloadDialog.kt`, ganchos em `Downloader`, `DownloadManager`, `AnimeScreenModel` |
| Player | `PlayerAnime4K.kt`, `Anime4KPlayerRow.kt` (aba Mais) |
| Configurações | `SettingsAnime4KScreen.kt`, `Anime4KQueueScreen.kt` (fila e teste de desempenho) |

O motor lê os arquivos oficiais sem alterar nada (`assets/anime4k`, licença MIT em
`LICENSE-Anime4K.txt`). Ele reproduz a ordem de ganchos do mpv (MAIN e depois PREKERNEL), os nomes
de textura (`HOOKED`, `MAIN`, `NATIVE`, `OUTPUT`), as expressões de tamanho e as condições `WHEN`,
e reaproveita texturas pela análise de tempo de vida (não guarda quadros na memória). As
identificações de resultado usam fonte + URL do episódio + preset + saída + codec + qualidade +
versão do motor.

## Validação do motor (PC, renderização por software)

Mesmos shaders, mesmo quadro (screenshot 1080p de anime dos resultados do Anime4K), comparando o
motor do Yomira com o libplacebo (o renderizador do mpv) no ffmpeg, com lavapipe:

| Preset | 1x | 1,5x | 2x |
| --- | --- | --- | --- |
| A Fast | 61,1 dB / 0,9995 | 61,0 / 0,9995 | 61,6 / 0,9995 |
| A+A Fast | 60,8 / 0,9995 | 60,9 / 0,9994 | 61,3 / 0,9995 |
| A High | 61,2 / 0,9995 | 60,9 / 0,9994 | 61,4 / 0,9994 |
| **A+A High** | **60,9 / 0,9995** | **60,7 / 0,9994** | **60,9 / 0,9994** |
| B+B High | 60,9 / 0,9995 | 60,6 / 0,9994 | 61,0 / 0,9994 |
| C High | idêntico (não roda em 1x) | 54,7 / 0,9981 | 54,0 / 0,9974 |
| C+A High | 61,1 / 0,9995 | 54,8 / 0,9982 | 54,3 / 0,9977 |

(PSNR / SSIM; tabela completa com os 36 casos em `scripts/yomira/anime4k`, rode `compare.py`.)
Acima de 50 dB a diferença é só arredondamento; os modos C ficam em ~54 dB porque a interpolação
bilinear do llvmpipe e do lavapipe arredonda de forma um pouco diferente.

Custo por quadro (GFLOP), calculado pelo próprio motor a partir dos passes que rodam:

| Preset | 1080p 1:1 | 1080p→2160p | 720p 1:1 | 720p→1080p | 480p→1080p |
| --- | --- | --- | --- | --- | --- |
| A Fast | 8,7 | 17,4 | 3,9 | 7,7 | 3,4 |
| A+A Fast | 12,9 | 34,1 | 5,7 | 15,2 | 6,7 |
| A High | 36,5 | 71,9 | 16,2 | 32,0 | 14,2 |
| **A+A High** | **45,2** | **106,7** | **20,1** | **47,4** | **21,1** |
| C Fast | 0 | 8,7 | 0 | 3,9 | 1,7 |

Tempo medido no PC (llvmpipe, 4 vCPUs, ~30 GFLOPS efetivos): A Fast 720p 1:1 = 0,13 s/quadro;
A High 1080p 1:1 = 1,12 s; A+A High 1080p 1:1 = 1,49 s; A+A High 720p→1080p = 1,47 s.

## Galaxy A15 (Mali-G57 MC2): o que esperar

Não há aparelho aqui; o app tem um **teste de desempenho** (Configurações → Anime4K → Fila de
processamento) que mede no próprio celular e passa a usar o número nas estimativas.

Estimativa (GPU de 70 a 140 GFLOPS de pico, 15 a 35 GFLOPS sustentados nesse tipo de shader, com
aquecimento): episódio de 24 min ≈ 34.500 quadros.

| Preset | Fonte → saída | Tempo estimado por episódio |
| --- | --- | --- |
| A Fast | 720p 1:1 | 1 a 2,5 h |
| A Fast | 1080p 1:1 | 2,5 a 5,5 h |
| A+A High | 720p 1:1 | 5,5 a 13 h |
| A+A High | 1080p 1:1 | 12 a 29 h |
| A+A High | 720p → 1080p | 13 a 30 h |

Isso bate com a observação de que o A15 não roda nem o Modo C Fast em tempo real (precisaria de
~95 GFLOPS sustentados para 720p→1080p a 24 fps).

Outros limites do aparelho:

- O Android 15+ limita serviços `mediaProcessing`/`dataSync` a 6 h a cada 24 h: um A+A High longo
  passa de um dia para o outro. Os trechos prontos ficam salvos e o trabalho continua.
- Com "Só carregando" e "Pausar no calor" (padrão) ele roda pela metade da velocidade quando morno
  e espera esfriar quando quente.
- Em 1:1 o modo C não faz nada e o A+A pode afiar demais (comportamento oficial do Anime4K).
- O decodificador/encoder de hardware não é o gargalo; a GPU é.

## Enhanced Downloads

Ao baixar (com a opção ligada): Original ou Anime4K Enhanced, com modo e qualidade. Depois do
download: cópia local do original → trechos processados (estados: aguardando, baixando,
processando, aguardando motivo, pausado, juntando, concluído, erro) → junção com o áudio, as
legendas, as fontes e os capítulos originais sem recompressão → validação (vídeo presente, áudio
presente, duração ±2 s) → `"<nome>.anime4k.mkv"` na pasta do episódio. O player toca essa versão
automaticamente. O original é mantido por padrão; se a opção estiver desligada, só é apagado depois
da cópia verificada. Falha ou cancelamento nunca tocam no original.

Pausar/retomar: por trecho (perde no máximo o trecho em andamento). Recuperação: a lista de tarefas
é salva a cada mudança; ao abrir o app, o que estava em andamento volta para a fila.

## Smart Anime4K

Ao abrir um episódio (inclusive o primeiro), se existe a versão pronta no cache, o player avisa e
oferece **Trocar** no mesmo ponto (aba Mais → Smart Anime4K). Senão, a tarefa entra na fila na
frente dos downloads: baixa o stream que está tocando (com os cabeçalhos) e processa. Pode preparar
o próximo episódio. Cache com limite e remoção dos mais antigos.

O que **não** foi feito, de propósito: trocar trecho por trecho durante a reprodução. Com o mpv
isso exigiria misturar arquivos locais e o stream remoto numa lista EDL com sincronia de áudio,
sujeito a travadas e dessincronia, e no A15 o processamento é dezenas de vezes mais lento que a
reprodução, então nunca alcançaria o ponto em que a pessoa está. A troca também não é automática:
a versão preparada pode ser outra variante do episódio (dublada, outra qualidade).

## Cloudflare

Avaliado e **não implementado**; tudo continua local.

- Workers AI roda modelos próprios do catálogo, não shaders arbitrários: não serve.
- Containers não têm GPU. Rodar o Anime4K por CPU (renderização por software) custa, pelo medido
  aqui, ~7,5 GFLOPS por vCPU:
  - A+A High 1080p 1:1: 1,56 PFLOP por episódio ≈ 208.000 vCPU-segundos ≈ 3.470 vCPU-minutos
    (o Workers Paid inclui 375 por mês) e ~14 h numa instância de 4 vCPUs. Fora da franquia: cerca
    de US$ 4 de CPU + US$ 1,5 de memória por episódio (preços publicados em 2025, conferir os
    atuais).
  - A Fast 720p 1:1: ~300 vCPU-minutos por episódio, ou seja, cerca de um episódio por mês dentro
    da franquia.
- Além do custo, enviar streams de terceiros para um servidor levanta questões de direitos que o
  processamento no próprio aparelho evita.

Se um dia valer a pena, o ponto de encaixe é o `Anime4KWorker` (um backend remoto opcional,
desligado por padrão, com autenticação, limites de uso e orçamento).

## Pendências

1. Testar no A15: teste de desempenho, um trecho de A Fast e de A+A High, troca no player,
   recuperação depois de fechar o app, pouco armazenamento.
2. Medir memória de GPU no A+A High com saída 2x (texturas RGBA16F em 4K).
3. Se o Mali tiver timeout de GPU em passes 4K, dividir os passes em blocos.
4. Ajustar os bitrates padrão depois de ver os arquivos reais.
