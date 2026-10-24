# Protocolo mod ⇄ helper

Lançamento: `java -cp <jar do mod> com.minetouhou.helper.HelperMain --game-dir D --exe E --fps N --width W --max-frame-bytes B --log F`

**helper → mod (stdout, binário):** `[tipo u8][tamanho u32 big-endian][payload]`, payload ≤ 1 MiB
- tipo 1 STATUS: UTF-8 `ESTADO|mensagem` — ESTADO ∈ STARTING, RUNNING, WARN, ERROR, EXITED, PONG
- tipo 2 FRAME: `[largura u16][altura u16][JPEG]`

**mod → helper (stdin, texto UTF-8, uma linha, ≤ 64 bytes):** `KEY DOWN|UP <TECLA>`, `PING`, `QUIT`.
TECLA ∈ UP DOWN LEFT RIGHT Z X SHIFT CTRL ESC ENTER Q. Qualquer outra linha é recusada e registrada.
Implementação: `core/HelperProtocol.java` (testada em `SelfCheck`).

Códigos de saída: 0 normal, 1 erro interno, 2 SO sem suporte, 3 instalação inválida, 4 ferramenta ausente, 5 display, 6 captura caiu.
