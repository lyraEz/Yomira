# Controle remoto

## No app

| Tecla | Ação |
| --- | --- |
| Setas | Movem o foco. O item focado ganha destaque. |
| OK | Abre ou ativa o item focado. Segurar OK equivale ao toque longo (seleção, opções). |
| Menu (≡) | Mesmo que segurar OK no item focado. Pode ser desligado em Configurações → Aparência → TV. |
| Voltar | Fecha teclado, menu ou diálogo; depois volta para a tela anterior. |

Ao voltar de uma tela de detalhes, o foco retorna ao item que estava selecionado na biblioteca, no histórico, nas atualizações e na lista de episódios.

No WebView (verificações de site), as setas movem um ponteiro, o OK clica e empurrar contra a borda de cima ou de baixo rola a página. O menu do WebView alterna entre ponteiro e navegação normal da página.

## No player

A regra é: **se há um menu ou os controles estão na tela, as setas navegam; se não, controlam a reprodução.**

| Situação | Setas ← → | Setas ↑ ↓ | OK | Voltar |
| --- | --- | --- | --- | --- |
| Controles ocultos | Voltar/avançar (segurar repete) | Mostram os controles | Mostra os controles | Sai do player |
| Controles visíveis | Movem o foco | Movem o foco | Ativa o botão focado | Esconde os controles |
| Menu, painel, diálogo ou erro aberto | Navegam no menu | Navegam no menu | Escolhe | Fecha o menu |
| Barra de progresso focada | Voltar/avançar | Movem o foco | — | Esconde os controles |

Ao abrir os controles, o foco começa em Reproduzir/Pausar.

| Tecla de mídia | Ação (vale em qualquer situação) |
| --- | --- |
| ⏯ Reproduzir/Pausar | Alterna reprodução |
| ⏪ / ⏩ | Voltar / avançar |
| ⏮ / ⏭ (ou canal −/+) | Episódio anterior / próximo |
| ⏹ Parar | Sai do player |
| Menu (≡) | Abre o menu do player (velocidade, filtros, capturas e outros) |

Trilha de áudio, legendas, velocidade, qualidade e servidor ficam nos botões dos controles e no menu. Teclas de teclado não listadas aqui continuam usando os atalhos do `input.conf` do mpv.

Se um episódio falhar ao carregar (servidor fora do ar, link expirado), o player mostra o erro com **Tentar de novo**, **Servidores**, **Episódios**, **Próximo episódio** e **Sair do player**.
