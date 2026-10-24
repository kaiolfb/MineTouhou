# Integração com o Touhou 6

## O que foi investigado
- `GensokyoClub/th06` (master): decompilação do v1.02h, **CC0**. Compila com MSVC 2002 sob Wine e exige o `東方紅魔郷.exe` original — não serve para rodar no Linux.
- `GensokyoClub/th06` branch `portable`: port com **SDL2 + OpenGL**, licença GPLv3 no LICENSE da branch. Build via premake5. **Compilado com sucesso neste projeto** (Ubuntu 24.04, SDL2 2.30, premake 5.0.0-beta7, g++): gerou o binário Linux `th06` e `th06_config`. O binário inicia e cria `東方紅魔郷.cfg`.
- `u-u-z/th06-portable`: fork só para macOS Apple Silicon (correção de tela cheia Retina; seu `setup.sh` baixa dados do jogo de um release, **não usado** por este projeto). Não é automaticamente compatível com Linux.
- Não é possível rodar C++ dentro da JVM do Minecraft: o jogo roda como **processo separado** controlado pelo helper.

## Compilar o port (Linux)
```
sudo apt install build-essential libsdl2-dev libsdl2-image-dev libsdl2-ttf-dev libasound2-dev
# premake5: https://premake.github.io/download
git clone --branch portable https://github.com/GensokyoClub/th06
cd th06 && premake5 gmake && cd build && make -j$(nproc) config=release
```
Copie `th06` para `~/minetouhou/game/` junto com **seus** `紅魔郷CM/ED/IN/MD/ST/TL.DAT` e uma fonte `msgothic.ttc` (MS Gothic ou Kochi Gothic).
O port usa os nomes japoneses; inicie o Minecraft com `LC_ALL=C.UTF-8`.

## Como o helper controla o jogo
1. `GameInstall.verify` confere executável (ELF/PE/Mach-O), SHA-256 do `.exe` original, 6 `.DAT` (tamanho e cabeçalho `PBG3`) e `msgothic.ttc`.
2. `Xvfb :N 640x480x24` — display virtual; o jogo roda **dentro dele**, sem janela na tela do jogador e sem disputar foco com o Minecraft.
3. O jogo é iniciado com `DISPLAY=:N`, `SDL_VIDEODRIVER=x11`.
4. `ffmpeg -f x11grab` captura o display, reduz para a largura configurada e entrega pixels brutos; o helper codifica JPEG ajustando a qualidade ao limite de bytes e envia ao mod (protocolo em [PROTOCOLO-HELPER.md](PROTOCOLO-HELPER.md)).
5. Teclas: `xdotool keydown/keyup` no display virtual, só de uma lista fechada (setas, Z, X, Shift, Ctrl, Esc, Enter, Q).
6. Encerrar: `QUIT` → termina jogo, ffmpeg e Xvfb; se o mod morrer (EOF no stdin) o helper também encerra tudo.

## Limitações conhecidas
- Helper só em **Linux/X11**. Windows/macOS: o mod mostra erro claro; é preciso um helper próprio (captura de janela + injeção de entrada nativas).
- **Áudio não é transmitido**: o jogo toca o som na máquina de quem joga; espectadores veem só a imagem.
- Executável original do Windows via Wine: previsto no código, **não testado**.
- A versão (1.02h) de um binário compilado do port não pode ser provada pelo mod; ele avisa.
