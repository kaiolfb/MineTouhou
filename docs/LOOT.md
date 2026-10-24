# Loot

Para cada loot table de baú **vanilla** cujo caminho começa com um prefixo de `lootTablePrefixes` (padrão: masmorras, minas abandonadas,
templos do deserto/selva, vilas, fortalezas, mansões, portais em ruínas, naufrágios, ruínas submarinas), o mod **adiciona um pool novo por item**:
1 rolagem, condição `random_chance = 1/N`. Os pools originais ficam intactos (nada é removido) e cada tabela recebe exatamente um pool por item
(sem rolagens duplicadas). Cada vez que o baú gera seu conteúdo é **uma tentativa**. Blocos comuns e mobs não são afetados.

- PC: `pcLootOneIn=5000` → 0,02 % · CD: `cdLootOneIn=9000` → ≈ 0,0111 % · `0` desativa.
- Config: `~/minetouhou/config/minetouhou.properties` (lida ao iniciar).
- Cálculo: `core/LootMath.java` (testado).
