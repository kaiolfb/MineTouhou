# Instalação

## Onde cada parte roda
| Parte | Roda em | Requisitos |
|---|---|---|
| Mod (bloco, item, GUI, renderer, captura de teclas) | Cliente Minecraft | Java 21, Fabric Loader, Fabric API |
| Mod (estado do PC, CD, loot, retransmissão) | Servidor (dedicado ou integrado) | Java 21, Fabric Loader, Fabric API. **Não executa o jogo.** |
| Helper + Touhou 6 | **Só na máquina do jogador responsável** | Linux/X11, `xvfb`, `ffmpeg`, `xdotool`, Java, o port do Touhou 6 e seus `.DAT` |
| Espectadores | Cliente Minecraft com o mod | nada além do mod |

## Passos
1. **Java 21**: https://adoptium.net/temurin/releases/?version=21
2. **Minecraft Java 1.21.1** + **Fabric Loader** (>= 0.16.5) + **Fabric API** (0.116.x para 1.21.1).
3. **Compilar o mod**: `./gradlew build` → `build/libs/minetouhou-<versão>.jar`. (O workflow `.github/workflows/build.yml` faz o mesmo e publica o JAR.)
4. Coloque o JAR em `.minecraft/mods/` (cliente) e `mods/` do servidor.
5. Na primeira execução o mod cria `~/minetouhou/{game,config,logs}` e `LEIA-ME.txt` (mude com `-Dminetouhou.dir=...` ou a variável `MINETOUHOU_DIR`; não exige administrador).
6. **Touhou 6**: veja [INTEGRACAO-TOUHOU6.md](INTEGRACAO-TOUHOU6.md).
7. No jogo: fabrique o PC e o CD, coloque o PC, clique nele, **Inserir CD**, **Verificar arquivos**, **Iniciar Touhou 6**, **Jogar**. Saída: F10.
8. Linux: `sudo apt install xvfb ffmpeg xdotool`.

## Ambiente de desenvolvimento
`./gradlew runClient` / `./gradlew runServer`. Verificações sem Minecraft: `./gradlew selfCheck`.

## Erros comuns
Veja `~/minetouhou/logs/helper.log`. "Ferramenta ausente" = instale xvfb/ffmpeg/xdotool. "Falta msgothic.ttc" = copie uma fonte com esse nome para `game/`.
