# MineTouhou (MTH)

Mod Fabric para **Minecraft Java 1.21.1** (Java 21). Coloque um **PC** no mundo, insira o **CD MineTouhou**, inicie o
**Touhou 6: Embodiment of Scarlet Devil** (jogo real, não simulação) e deixe outros jogadores assistirem o gameplay no
monitor do PC dentro do Minecraft.

> **Estado honesto do projeto:** veja [docs/TESTES.md](docs/TESTES.md). Em resumo: o núcleo, o protocolo e o helper de
> captura foram testados de verdade (com um jogo de teste no lugar do Touhou 6); o código do mod Fabric foi escrito
> contra a API do Yarn 1.21.1 mas **ainda não foi executado dentro do Minecraft**; o Touhou 6 real **não foi executado**
> porque requer os arquivos do seu jogo original.

## Conteúdo
- Bloco **PC MineTouhou** (`minetouhou:pc`) com estados (desligado, inicializando, aguardando CD, verificando, pronto, executando, erro) e tela dinâmica.
- Item **CD MineTouhou** (`minetouhou:cd`), plano, textura própria (prata, furo, centro vermelho, "MTH").
- Receitas e loot raro (PC 1/5000, CD 1/9000 por tentativa de loot elegível; configurável).
- Interface do PC com botões reais, modo de jogo com captura de teclado, transmissão a espectadores.
- **Helper** separado (Java, só JDK) que roda o jogo num display virtual (Xvfb), captura com ffmpeg e injeta teclas com xdotool.

## Documentação
- [Instalação e compilação](docs/INSTALACAO.md)
- [Integração com o Touhou 6](docs/INTEGRACAO-TOUHOU6.md)
- [Multiplayer e transmissão](docs/MULTIPLAYER.md)
- [Protocolo do helper](docs/PROTOCOLO-HELPER.md)
- [Testes e estado atual](docs/TESTES.md)
- [Loot: como as chances são calculadas](docs/LOOT.md)

## Direitos autorais
Este projeto **não inclui nem baixa** nenhum arquivo do Touhou 6. Você precisa da sua própria cópia legítima.
Touhou Project / Touhou 6 são de ZUN / Team Shanghai Alice. O código de decompilação usado como referência
(GensokyoClub/th06) é CC0 na branch principal; a branch `portable` traz GPLv3 no LICENSE (veja o arquivo antes de redistribuir binários).
