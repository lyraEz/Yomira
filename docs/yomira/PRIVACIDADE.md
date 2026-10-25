# Privacidade

O Yomira não tem telemetria, anúncios nem contas próprias, e não envia dados de uso a quem mantém o projeto.

O app só se conecta a:

- **Repositórios de extensões** que você adicionar, para listar e baixar extensões.
- **Sites das fontes**, por meio das extensões que você instalar. O que cada site recebe depende da extensão e do próprio site.
- **GitHub**, para verificar se há uma versão nova do Yomira (no máximo a cada dois dias; o download automático da atualização pode ser desligado nas configurações).
- **Serviços opcionais** que você ativar: rastreadores (AniList, MyAnimeList e outros), SyncYomi, provedores do assistente de IA com a sua chave.
- **Metadados e pular abertura** (Configurações → Player → *Metadados e pular abertura*, ligados por padrão e desligáveis):
  - **TMDB** recebe o título do anime (para achar a série) e a chave de API, e devolve nomes, descrições e imagens dos episódios;
  - **AniList** recebe o título do anime quando ele não está rastreado, para achar os ids e a data de estreia;
  - **AniSkip** e **Anime Skip** recebem o id do anime no MyAnimeList/AniList e o número do episódio.

  Nada disso inclui dados da sua conta ou do aparelho. O que vem do TMDB fica guardado no próprio celular e pode ser apagado em *Limpar dados do TMDB*.

- **Discord** (Configurações → Conexões → Discord, desligado por padrão): enquanto o player está aberto, o Yomira passa para o app do Discord instalado no celular o nome do anime e do episódio, a capa, o tempo e os botões escolhidos. O Discord mostra isso no seu perfil para quem pode vê-lo. O Yomira não pede nem guarda login, senha ou token da conta. Modo anônimo, animes escondidos, extensões +18 (por padrão) e as extensões e categorias fora da lista nunca aparecem.

O recurso *Enviar do celular / Receber do celular* abre um servidor temporário na sua rede local enquanto o diálogo estiver na tela. Ele só aceita pedidos com o código aleatório exibido no QR code e é encerrado quando o diálogo fecha.

Biblioteca, histórico, configurações e backups ficam no próprio aparelho, ou onde você decidir guardar o backup.
