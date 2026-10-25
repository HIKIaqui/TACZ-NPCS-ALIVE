# Reputação — base do patch 1, incluída no patch 2

Base: GitHub HIKIaqui/TACZ-NPCS-ALIVE, commit 4d0320374e8ed5ae64285f5978890d14d9bd06e7.
Versão atual do JAR: 2.1.0-hiki.reputation.2.

Este patch entrega relações direcionais entre NPCs, identidade explícita de facção,
reputação por jogador, persistência, comandos e integração ao combate e à autorização
para negociar. O patch 2 acrescenta saudações e condições de diálogo por reputação;
veja REPUTACAO-DIALOGOS.md. Pedidos, recompensas automáticas, companhia e preços por reputação
continuam para as etapas seguintes. Neste patch os valores do jogador são alterados
pelos comandos/API; não há penalidades automáticas por dano/morte ou ganhos por trocas.

## Configuração incompatível com aligned/opposed

Em config/tacznpc_loadouts.json, remova aligned e opposed. O novo formato é:

```json
{
  "village_guard": {
    "faction": "villagers",
    "relations": { "villagers": 50, "illagers": -100, "monster": -100 }
  },
  "village_trader": {
    "faction": "villagers",
    "relations": { "villagers": 50, "illagers": 0, "monster": -100 }
  },
  "illagers": {
    "faction": "illagers",
    "relations": { "villagers": -100, "village_trader": 0 }
  }
}
```

Os campos de equipamento, skin, names e demais opções permanecem no mesmo objeto.
Se faction estiver ausente, usa-se o ID do loadout. Identificadores diferenciam
maiúsculas/minúsculas. Use a grafia exata, inclusive monster em minúsculas.
Gostar de uma facção não concede afiliação a ela.

Prioridade para alvos NPCs: ID exato do loadout > identidade da facção do alvo > 0.
O valor zero explícito também sobrescreve uma regra geral negativa.
NPCs não herdam facções/categorias de seu tipo de entidade compartilhado.

Para outros mobs: grupos de entidade de tacznpc_factions.json > categoria vanilla > 0.
Se um mob estiver em vários grupos com regras explícitas conflitantes, usa-se o menor
valor entre os grupos. A política é determinística; não depende da ordem do JSON.
Uma regra explícita do grupo prevalece sobre a categoria, mesmo quando é zero.
A IA de outros mods/vanilla não recebe automaticamente nossa política de relações.
O alerta de inimigos só atribui alvo a outro TACZ NPC cuja própria relação autorize atacar.

A considera B inimigo não implica que B considere A inimigo. Não há propagação de
alianças por terceiros. Ajuda em combate considera a opinião de quem ajuda; compartilhar
itens e encontros amistosos offline exigem aliança nos dois sentidos.
Todo NPC considera a si próprio aliado.
Para NPCs diferentes da mesma facção serem aliados, configure a própria facção em relations.

aligned/opposed são rejeitados explicitamente: no início o servidor avisa e não prossegue
com configuração inválida; em reload a última configuração válida é preservada.
A validação de loadouts, grupos de entidades e estruturas ocorre antes de trocar os mapas
em uso. Um erro nesses arquivos não apaga a configuração ativa.

## Reputação do jogador

Reputação efetiva = reputação com a facção + reputação pessoal com o NPC.

A reputação da facção é indexada por UUID do jogador e ID da facção.
A pessoal é indexada por UUID do jogador e UUID do NPC. NPCs de loadouts diferentes
que compartilham faction usam a mesma reputação de facção com aquele jogador.
Exemplo: facção=-100 e pessoal=150 resulta em efetiva=50 para o amigo;
outros NPCs da mesma facção continuam em -100.

Os valores pessoais e de facção suportam -1.000.000.000 a +1.000.000.000;
adições saturam nessa faixa. A soma usa long. Os marcos ±100 não são limites de pontuação.
As consultas rodam no servidor; não há varredura global de entidades para calcular reputação.

Faixas fixas de identificação:

| Valor | Faixa |
|---|---|
| <= -100 | Vilanizado |
| -99 a -50 | Hostil |
| -49 a -25 | Odiado |
| -24 a 24 | Neutro |
| 25 a 49 | Amigável |
| 50 a 99 | Aliado |
| >= 100 | Idolizado |

Em config/tacznpc_reputation.json:

```json
{
  "hostileAt": -50,
  "alliedAt": 50,
  "tradeMinimum": -49,
  "defaultFactionReputation": 0,
  "factionDefaults": {
    "military": -100,
    "raiders": -100
  }
}
```

hostileAt: ataque espontâneo quando o valor é menor ou igual.
alliedAt: reconhecimento como aliado quando o valor é maior ou igual.
tradeMinimum: valor mínimo efetivo para negociar, além das condições existentes.
Exige-se hostileAt < 0 < alliedAt e tradeMinimum > hostileAt.
Mudar esses três limites comportamentais não muda os nomes das faixas acima.

factionDefaults define a reputação de facção de um jogador sem valor salvo para aquela
facção; defaultFactionReputation é o fallback. Um valor salvo pelo comando/API substitui
o padrão. reset remove esse valor salvo e volta ao padrão atual.
A antiga oposição a players deve virar factionDefaults, não relations.players.
relations.players é rejeitado para evitar uma configuração de hostilidade sem efeito.

Os padrões do mod são neutros com todos os jogadores. O arquivo convertido do dump
incluído no ZIP mantém em -100 as facções que antes listavam players em opposed.
Esse arquivo é uma conversão do dump enviado, não uma leitura da sua instalação atual.

## Combate e comércio

Todas as decisões de relação com jogadores usam a soma efetiva. Isso inclui descobrir
alvos, reconhecer aliados e autorizar comércio. canTrade=false do inventário ainda vale.
Uma sessão de compra revalida as condições ao executar a troca.

Mudar a reputação/relação faz um NPC largar, no próximo tick, um alvo adquirido por
hostilidade que deixou de ser hostil; tornar o alvo aliado também interrompe o ataque.
Retaliação contra um agressor neutro continua possível como antes. Neutralidade não é
invulnerabilidade, e um invasor neutro com um comerciante pode retaliar se for atacado.
Não há proteção contra balas perdidas nem garantia de imortalidade neste patch.

O estado de alerta e o cooldown de dano continuam valendo para conversar: melhorar
reputação durante uma luta não elimina instantaneamente esses tempos de segurança.
As saudações por reputação estão disponíveis no patch 2; veja REPUTACAO-DIALOGOS.md.

## Comandos (operador, permissão 2)

```
/tacznpcs_reputation status <jogador> <seletor-do-npc>
/tacznpcs_reputation faction <jogador> <facção> get
/tacznpcs_reputation faction <jogador> <facção> set <valor>
/tacznpcs_reputation faction <jogador> <facção> add <valor>
/tacznpcs_reputation faction <jogador> <facção> reset
/tacznpcs_reputation personal <jogador> <uuid-do-npc> get
/tacznpcs_reputation personal <jogador> <uuid-do-npc> set <valor>
/tacznpcs_reputation personal <jogador> <uuid-do-npc> add <valor>
/tacznpcs_reputation personal <jogador> <uuid-do-npc> reset
/tacznpcs_reload
```

O primeiro comando imprime os UUIDs, a facção, as duas parcelas, a soma e sua faixa.
Use, por exemplo, @e[type=tacznpcs:npc,sort=nearest,limit=1] para o NPC e @p para jogador.
O argumento jogador usa os perfis conhecidos pelo servidor, incluindo nomes offline
presentes no cache. personal aceita o UUID de um NPC descarregado ou viajando.
Para IDs de facção contendo caracteres especiais, use aspas, por exemplo "mod:guards".
reset pessoal zera só aquela amizade; reset facção não apaga as reputações pessoais.

Os dados ficam no SavedData do Overworld: data/tacznpcs_reputation.dat no mundo.
Persistem com o salvamento normal, logout, morte/respawn do jogador, troca de dimensão e
reinício; as relações pessoais não dependem de o NPC estar carregado. A identidade
salva no NBT é a mesma usada pelas viagens. Não há nova cópia da reputação em cada viagem.
Alterações ainda não salvas podem se perder em encerramento forçado, como os demais SavedData.

## Instalação

1. Substitua o JAR anterior por dist/tacznpcs-2.1.0-hiki.reputation.2.jar.
2. Atualize tacznpc_loadouts.json para faction/relations antes de iniciar.
3. Se quiser partir do dump enviado, o ZIP inclui os dois JSONs já convertidos em
   config-do-dump-convertida/. Preserve outras alterações que tenha feito desde aquele dump.
4. Em instalação nova os exemplos/padrões novos são gerados automaticamente.
5. Depois das edições, /tacznpcs_reload. Falhas ficam no log e mantêm os valores anteriores.

O patch inclui todos os recursos presentes no commit-base do GitHub.
