# Configuração dos NPCs — loot por bioma

O bioma define o que o NPC encontra. O loadout define o que ele aceita guardar, pelos mesmos `interests` usados no inventário.

Os exemplos novos são instalados em `config/tacznpc_examples/biome-loot-v0.2/`. A subpasta `config/` reproduz a estrutura da configuração real do jogo. Esses arquivos são referências: copie ou mescle o que deseja ativar, preservando suas personalizações, e rode `/tacznpcs_reload`.

## Migração da versão anterior

As listas antigas com `loadouts` não são convertidas automaticamente, porque não existe correspondência entre loadout e bioma que o mod possa adivinhar.

1. Mova a pasta ativa `config/tacznpc_loot/` para um local de backup, fora dessa pasta.
2. Copie `examples/config/tacznpc_loot/` deste pacote para `config/tacznpc_loot/`. Também há uma cópia em `config/tacznpc_examples/biome-loot-v0.2/config/tacznpc_loot/` após iniciar o mod.
3. Reaproveite suas entradas personalizadas de loot dentro das pools novas.
4. Configure os `interests` dos seus loadouts em `config/tacznpc_inventory.json`.
5. Execute `/tacznpcs_reload`.

Não deixe arquivos de loot antigos dentro da pasta ativa, mesmo que não fossem usados: todos os JSONs são validados. O erro do formato antigo explica a migração e preserva os arquivos. Se não existir nenhuma pasta ativa, uma instalação nova cria automaticamente os exemplos padrão, deserto e neve.

O campo antigo `loot.table` em `tacznpc_offline_actions.json` é ignorado. Pode removê-lo; ele não consegue selecionar arquivos nem alterar o resultado. Custos, pesos e ativação das ações continuam configuráveis por loadout.

## Onde configurar cada coisa

Caminhos relativos à pasta `config/` real:

| Arquivo | Função |
|---|---|
| `tacznpc_loadouts.json` | Equipamento inicial, skins, nomes, aliados e inimigos. |
| `tacznpc_factions.json` | Tipos de entidades pertencentes a cada facção. |
| `tacznpc_quests.json` e `tacznpc_quests/*.json` | Missões, regras de aceitação, objetivos, XP e falas preparadas. Veja `MISSOES.md`. |
| `tacznpc_structures.json` | Substituição de entidades durante geração de estruturas. |
| `tacznpc_behavior.json` | Descanso, percepção, fuga e fogueiras. |
| `tacznpc_offline_actions.json` | Carteira de tempo, custos, pesos e ações habilitadas. |
| `tacznpc_loot/default.json` | Pools para biomas sem lista específica. |
| `tacznpc_loot/*.json` | Pools associadas a um ou vários IDs de bioma. |
| `tacznpc_inventory.json` | Interesses que filtram os resultados, compartilhamento social e lojas. |
| `adm-dialogues/trades/*.json` | Ofertas fixas de comércio no formato ADM. |
| `tacznpc_dialogue.json` | Condições e prioridades que selecionam diálogos. |
| `tacznpc_dialogues/*.json` | Falas e escolhas de cada perfil. |

O ID do loadout liga as configurações de identidade, comportamento, ações, inventário e diálogo. Os exemplos usam `villagers` e `illagers`, no plural. As listas de loot não contêm loadouts.

## Ações offline

Dentro de `offlineActions`:

```json
"loot": { "enabled": true, "cost": 2, "weight": 1 },
"share": { "enabled": true, "cost": 1, "weight": 1 }
```

Esse trecho é parte de um objeto. Há um arquivo completo em `examples/config/tacznpc_offline_actions.json`.

- `ticksPerPoint`: ticks sem atualização por ponto; padrão 1200, um minuto de jogo a 20 TPS.
- `maxActionsPerTick`: limite global de tentativas por tick, padrão 16.
- `nearbyNpcLimit` e `populationRadius`: controle populacional de `meetAllies`.
- `enabled`, `cost` e `weight`: ativação, custo e peso relativo da ação; o peso não é porcentagem.

A carteira usa tempo do mundo; servidor fechado não rende saldo. As ações são resolvidas quando o NPC recebe ticks, com espera inicial e limite global. Combate, alerta, fogo ou `NoAI` podem impedir o processamento. O bioma é consultado na posição do NPC quando cada ação é resolvida.

## Arquivos e seleção por bioma

`default.json` tem apenas `pools` e é o fallback:

```json
{
  "pools": [
    {
      "name": "supplies",
      "chance": 0.8,
      "rolls": { "min": 1, "max": 2 },
      "entries": [
        { "item": "minecraft:bread", "weight": 4, "count": { "min": 1, "max": 3 } },
        { "item": "minecraft:iron_ingot", "weight": 1, "chance": 0.5, "count": 1 }
      ]
    }
  ]
}
```

Outros arquivos adicionam `biomes`. Exemplo de `desert.json`:

```json
{
  "biomes": ["minecraft:desert", "minecraft:badlands"],
  "pools": [
    {
      "name": "scrap",
      "rolls": 1,
      "entries": [
        { "item": "minecraft:iron_ingot", "weight": 3, "count": { "min": 1, "max": 2 } },
        { "item": "minecraft:redstone", "weight": 2, "count": { "min": 1, "max": 4 } }
      ]
    }
  ]
}
```

IDs de biomas de outros mods também são aceitos quando registrados no jogo. A lista usa IDs exatos, não tags de bioma. Vários biomas podem compartilhar um arquivo. Um bioma não pode aparecer em mais de um arquivo: sobreposição recusa o reload e identifica os arquivos conflitantes.

Uma lista específica substitui o fallback, sem somar pools. `"pools": []` desativa loot naquele bioma e não volta para o padrão. Não adicione `biomes` a `default.json`; use outros arquivos para seletores. Na falta de lista específica, qualquer loadout usa o mesmo fallback.

Ainda não existe seleção por Lost Cities: isso poderá ser acrescentado na seleção da origem, preservando o filtro de interesses.

## Sorteio primeiro, interesses depois

A ação visita todas as pools, testa a chance de cada uma e sorteia `rolls`. Em cada tentativa, escolhe uma entrada pelo `weight`, testa sua `chance` e sorteia `count`. Tentativas podem repetir itens.

Depois de gerar todos os candidatos, usa `interests` de `tacznpc_inventory.json` para decidir o que guardar. IDs de itens e tags de itens com `#` funcionam como na coleta. Exemplo:

```json
"loadouts": {
  "villagers": { "interests": ["minecraft:bread", "minecraft:carrot", "minecraft:emerald"] },
  "illagers": { "interests": ["minecraft:iron_ingot", "tacz:modern_kinetic_gun"] }
}
```

Se o sorteio oferecer pão, ferro e redstone, o primeiro NPC leva só o pão e o segundo só o ferro. Se vários itens interessarem, leva todos eles, preservando suas quantidades. Se nenhum interessar, não leva nada. Uma lista de interesses no loadout substitui a de `defaults`.

Os interesses não alteram pesos, chances ou pools e não causam outro sorteio. A ação válida cobra seu custo mesmo quando não encontra nada ou rejeita todos os candidatos. Sem pools no bioma, a ação não é executada nem cobrada.

No loot offline, armas e armaduras também precisam corresponder a um interesse. Não há exceção automática para equipamento nessa etapa. A coleta ativa no chão mantém as regras anteriores de equipamento. Loot offline não equipa automaticamente: os itens aceitos vão para a mochila persistente.

Campos omitidos: `rolls=1`, `chance=1`, `weight=1`, `count=1`. Chances de 0 a 1; intervalos inclusivos. Peso 1..1000000. Até 16 pools, 16 sorteios por pool, 64 sorteios máximos somados e 256 entradas por pool. Quantidade 1..64, respeitando o máximo da pilha. Use `count: 1` para armas não empilháveis. Cada arquivo específico aceita até 256 biomas; até 128 arquivos de 2 MiB. Nomes de arquivo usam letras minúsculas, números, `_` e `-`, terminando em `.json`.

## NBT, componentes e TaCZ

Uma entrada continua aceitando `nbt` em SNBT ou objeto JSON:

```json
{
  "item": "tacz:modern_kinetic_gun",
  "weight": 1,
  "chance": 0.25,
  "count": 1,
  "nbt": "{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:0}"
}
```

Ou `"nbt": { "GunId": "tacz:glock_17", "GunCurrentAmmoCount": 0 }`. O campo grava em `minecraft:custom_data`; SNBT preserva tipos como `1b`. Use `components` para dados nativos, como `"components": { "minecraft:damage": 40 }` em uma espada. Não combine `nbt` e `components.minecraft:custom_data` na mesma entrada.

NBT, durabilidade e demais componentes são preservados no filtro e no armazenamento. Para copiar um item, jogue uma unidade no chão e consulte `/data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item`.

Interesses são por ID-base ou tag, não por NBT. Se dois modelos TaCZ usam `tacz:modern_kinetic_gun`, esse interesse aceita ambos, mantendo os dados do modelo sorteado. O parser valida o item e componentes; o `GunId` precisa existir no gunpack instalado. Munição na mochila continua sem ser consumida pelo disparo do NPC.

## Share offline

Procura um NPC já carregado na mesma dimensão, até seis blocos de distância, com linha de visão. Ambos precisam estar vivos, fora de combate/alerta/fogo, mutuamente aliados e permitir `shareItems` na configuração de inventário.

Transfere uma unidade de uma pilha aleatória da mochila. O remetente precisa ter pelo menos duas unidades a mais que o destinatário, contando todos os itens da carga. Equipamento vestido não é compartilhado. A soma dos itens se conserva e os componentes são preservados.

A seleção offline substitui `shareChance` e a espera de `shareCooldownTicks` da conversa ativa. Depois de transferir, atualiza o cooldown social nos dois NPCs. Sem parceiro apto, não cobra; as tentativas malsucedidas são limitadas pelo sistema offline. O compartilhamento normal continua usando sua chance e cooldown.

## Loadouts, facções e estruturas

Em `tacznpc_loadouts.json`, `aligned` e `opposed` aceitam facções e IDs de loadouts. Loadouts com uma afiliação em comum ou referência direta em `aligned` se reconhecem como aliados; oposição de qualquer lado tem prioridade. `meetAllies` sorteia entre todos os loadouts aliados cadastrados. A reação a dano próximo usa a mesma relação. Veja `ALIADOS.md` para um exemplo completo dessas regras. `skin` pode ser um objeto ou lista com `id` e `slim` opcional; `names` permite nomes aleatórios.

Os campos `primary`, `secondary`, `helmet`, `chestplate`, `leggings` e `boots` aceitam ID de item, `%tacz:glock_17` para uma arma, `#tag` para sortear item de uma tag ou `$namespace:loot_table` para uma loot table. `equipment.table` usa uma tabela de equipamento completa. Essas tabelas pertencem aos recursos/data packs do Minecraft, enquanto `tacznpc_loot/` é o loot de expedição que vai para a mochila.

Os exemplos preservam as skins e tabelas originais de villagers/illagers. Campos antigos de `dropChances` permanecem no formato, mas o inventário atual dropa toda a carga e equipamento na morte, respeitando a regra mundial de loot.

`tacznpc_factions.json` associa facções a tipos de entidades como `minecraft:villager`. NPCs entram nessas facções via `aligned`. Categorias especiais como `monster` são reconhecidas pelo comportamento existente.

`tacznpc_structures.json` contém regras por ID de estrutura com `entities`, `loadout` e `chance`. Atua durante geração de estruturas; não converte retroativamente todos os mobs existentes.

## Comportamento e fogueiras

`tacznpc_behavior.json` contém padrões e substituições parciais em `loadouts`:

- `rest`: POIs, busca, chance, duração/cooldown, tempo de viagem, velocidade e raio social.
- `perception`: alcance calmo, descansando e alerta, duração do alerta e aviso a aliados.
- `damageEscape`: reação ao dano, duração, raio, distância mínima, velocidade e atualização do caminho.
- `campfire`: permissão de usar, construir e acender fogueiras; tempos, cooldown e exigência de céu aberto.
- `campfires`: configurações mundiais de apagar com o tempo, duração, ocupação e espaçamento. Este grupo é global.

`placeCampfire` offline depende de `campfire.canCreate`. A ação offline `rest` cura; o grupo `rest` do comportamento controla descanso ativo em POIs. O exemplo diferencia levemente a chance de descanso de villagers/illagers.

## Inventário e comércio

`tacznpc_inventory.json` usa `defaults`, `loadouts`, `factionTrades` e `genericTrades`. `interests` aceita IDs e tags com `#` para coleta próxima. A lista de um loadout substitui a padrão; equipamento útil continua elegível pela regra anterior. `shareItems` controla compartilhamento online e offline. `shareChance` e `shareCooldownTicks` só controlam a conversa ativa.

`canTrade` habilita comércio. Prioridade: arquivo explícito do loadout, arquivo por facção, `defaults.tradeFile`, ofertas genéricas. Várias facções com loja: desempate alfabético por ID. Os exemplos apontam para duas lojas fixas: copie também `adm-dialogues/trades/villagers.json` e `illagers.json`. Para ofertas genéricas, remova essas entradas de `factionTrades` e deixe `tradeFile` vazio. O antigo nome reservado `tnpc_inventory.json` também significa genérico.

Em `genericTrades`, `currency` define moeda, `quantity` define mercadoria por operação, `buyPrice` é o que o NPC paga e `sellPrice` o que cobra. São valores de teste; configure o balanceamento do modpack. O NPC precisa ter itens/moedas reais para negociar. Equipamento vestido fica fora da loja.

Ofertas fixas ADM têm `cost` e `result` com `item` e `count`, ou listas no formato ADM. A correspondência é por ID-base: armas TaCZ com o mesmo ID compartilham a regra, embora o item entregue mantenha seus componentes. O sistema não faz preço individual por modelo. Comandos embutidos nas ofertas são recusados.

## Diálogos

`tacznpc_dialogue.json` seleciona perfis por `priority`, `profile`, `genericMode` e `when`. Os exemplos usam `tnpc.loadout` e IDs `villagers`/`illagers`. Também há condições `tnpc.faction`, `tnpc.uuid` e `tnpc.healthy`. `genericMode: append` acrescenta as opções genéricas.

Os perfis em `tacznpc_dialogues/` têm `start`, `nodes`, texto e `choices`; escolhas podem usar `next`, `close` ou `action: back`. O mod insere a opção de comércio. A antiga entrega da pilha na mão foi removida. Hostilidade, distância e combate continuam bloqueando conversas; uma fala configurada não torna um inimigo amigável.

## Teste e diagnóstico

```mcfunction
/summon tacznpcs:npc ~ ~ ~ {template:"villagers"}
/tacznpcs_offline add 10
/tacznpcs_offline status @e[type=tacznpcs:npc,distance=..16]
/tacznpcs_inventory list @e[type=tacznpcs:npc,sort=nearest,limit=1]
```

`add` concede pontos a todos os NPCs existentes, inclusive descarregados, que recebem quando voltarem. Para isolar loot ou share, desabilite temporariamente as outras ações. Para share, use dois aliados próximos e dê itens a um deles:

```mcfunction
/tacznpcs_inventory give @e[type=tacznpcs:npc,sort=nearest,limit=1] minecraft:bread 10
/tacznpcs_offline add 1
```

`status` mostra saldo e última ação. `list` resume até 32 pilhas; o NBT `tnpcInventory` contém a mochila completa. Não use `NoAI` no teste.

Após editar, execute `/tacznpcs_reload`. O reload das ações offline e das listas é conjunto: qualquer lista inválida ou seletor de bioma inválido mantém ambos na última versão válida. O log identifica arquivo, bioma, pool e entrada quando possível. Em primeira inicialização, não existe snapshot anterior de loot para recuperar.

JSON não aceita comentários nem vírgula após o último campo. Nas listas de loot, use `biomes` para os seletores e `pools → entries` para os sorteios; o arquivo `default.json` não contém seletores.
