# Testes e estado atual

Legenda: ✅ executado e passou · 🟡 parcial · ⬜ **ainda não realizado**

| # | Teste obrigatório | Estado | Como / observação |
|---|---|---|---|
| 1 | Compilar o mod para Minecraft 1.21.1 | ⬜ | O ambiente de desenvolvimento não alcança `maven.fabricmc.net`/Mojang, então não foi compilado aqui. O código foi escrito conferindo cada nome contra o Yarn 1.21.1. O workflow `build.yml` compila no GitHub; **o resultado desse CI é a primeira compilação real.** |
| 2 | Iniciar o Minecraft com Fabric | ⬜ | Depende do item 1. |
| 3 | Criar e colocar o PC | ⬜ | Depende do item 2. |
| 4 | Fabricar o CD | ⬜ | Receita JSON escrita (`recipe/cd.json`); não validada pelo jogo. |
| 5 | Inserir/ejetar CD sem duplicar/perder | 🟡 | Lógica atômica na thread do servidor (`PcBlockEntity`), CD cai no chão se o PC é quebrado. Não executada no jogo. |
| 6 | Criar os diretórios | ✅ | `SelfCheck`: `game/ config/ logs/` + `LEIA-ME.txt`, sem sobrescrever o existente. |
| 7 | Verificar instalação válida | ✅ | `SelfCheck` com instalações sintéticas (ELF + 6 `.DAT` com cabeçalho PBG3 + fonte). |
| 8 | Rejeitar instalação inválida | ✅ | `SelfCheck`: pasta vazia, sem `.DAT`, `.DAT` truncado, sem fonte, executável de formato desconhecido. |
| 9 | Iniciar o Touhou 6 | 🟡 | O **port portable foi compilado** (binário Linux) e **abre** em Xvfb; **não rodou o jogo** porque não há `.DAT` originais neste ambiente. |
| 10 | Gameplay real no monitor | 🟡 | Pipeline real testado com um aplicativo X de teste no lugar do jogo: Xvfb → ffmpeg x11grab → JPEG → protocolo (60 quadros, ≤ 8,8 KB, ~11 fps, validados). Textura/renderer do Minecraft: não executados. |
| 11 | Encerrar o jogo e recuperar controles | 🟡 | Helper: `QUIT` encerra jogo+ffmpeg+Xvfb (sem processos órfãos). `GameScreen.removed()` solta as teclas; não executado no jogo. |
| 12 | Outro jogador vê o monitor | ⬜ | Código de retransmissão escrito; não executado. |
| 13 | Desconexões e falhas | 🟡 | Helper: EOF no stdin encerra tudo; servidor: timeout de 15 s e dono offline encerram a sessão (código; não executado). |
| 14 | Servidor dedicado não executa o jogo | 🟡 | Por construção: o servidor só usa `ServerSessions`/`PcBlockEntity`; nenhuma classe de cliente nem de `helper` é carregada. Não executado em servidor real. |
| 15 | Distribuição sem arquivos comerciais | ✅ (CI) | O CI lista o JAR e falha se achar `.dat/.exe/.ttc/.wav/.mid/th06`. |

## Verificações automáticas (`./gradlew selfCheck`, 70 checagens, só JDK)
Chances de loot (5000 → 0,02 %, 9000 → ≈0,0111 %), elegibilidade de tabelas, balde de fichas, protocolo (ida/volta, tamanhos absurdos, tipos desconhecidos),
comandos (injeção, teclas fora da lista, argumentos extras), JPEG (cabeçalho, dimensões declaradas × reais, limites), `PathGuard` (`..`, caminho absoluto, symlink para fora, diretório),
diretórios/config, verificação da instalação.

## Testes manuais com o helper (executados)
- Captura real de um aplicativo X em Xvfb → quadros JPEG válidos (imagem conferida visualmente).
- Injeção de teclas com `xdotool` real, verificada com `xev`: `z`, `Left`, `Shift_L` chegam como um press e um release cada. (Isso achou e corrigiu um bug: `--clearmodifiers` deixava o Shift preso.)
- Comandos maliciosos (`KEY DOWN F4`, `KEY DOWN Z; touch /tmp/pwn`, `EXEC id`) recusados; nada executado.

## Falta para considerar o projeto concluído (critério da seção 18)
Compilar no CI, abrir no Minecraft, jogar o Touhou 6 real com seus `.DAT`, e ver outro jogador assistindo.
