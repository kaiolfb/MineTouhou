# Multiplayer

```
 jogador responsável (cliente)          servidor Minecraft              espectadores (clientes)
 helper + Touhou 6 + captura  --Frame--> valida + limita + retransmite --FrameOut--> textura do monitor
        ^                                      |
        '------ Session(start) <-- Action START-'
```
- O **servidor não executa o jogo** (qualquer SO com Java 21 serve). Ele guarda CD/estado, decide quem é o responsável e retransmite.
- Só **uma** sessão por PC: o servidor recusa `START` se já há dono. Só o dono envia quadros e relatórios e só ele encerra.
- Quadros: JPEG ≤ `maxFrameBytes` (padrão 30 KB), até 640×480, cabeçalho JPEG conferido contra o tamanho declarado. Limites no servidor: quadros/s (`fps`+2), bytes/s de saída (`maxBytesPerSecond`, contado por espectador), `maxViewers`, `viewDistance`.
- Pacotes só carregam ids de ação, estado e imagem: **nenhum caminho nem comando** vem da rede.
- Dono desconecta, trava (15 s sem sinal) ou o PC é quebrado ⇒ sessão encerra; o CD permanece no PC (ou cai no chão se o bloco é quebrado).
- Espectadores nunca recebem arquivos do jogo.
- Custo de banda típico: ~8 KB × 12 quadros/s ≈ 100 KB/s por espectador (256×192). Ajuste `fps`, `width`, `maxFrameBytes`.
