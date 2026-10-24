# Missões e progressão dos NPCs — v0.1

Cada NPC guarda sua missão ativa, origem, identidade da atribuição, XP total e histórico de conclusões. Tudo acompanha o NBT da entidade, incluindo inventário e equipamento anteriores. NPCs antigos começam com zero XP e recebem uma missão se houver alguma elegível.

Esta etapa reconhece objetivos alcançados pelo movimento normal ou por teleporte de teste. O registro de viagens, a ação offline `move`, o novo menu de perguntas, a reputação e o acompanhamento serão conectados em etapas posteriores. Ter uma missão ainda não dá ao NPC navegação de longa distância até ela.

## Arquivos

- `config/tacznpc_quests.json`: ativação, intervalos e progressão.
- `config/tacznpc_quests/*.json`: um arquivo por missão. O nome sem `.json` é seu ID.
- `config/tacznpc_examples/biome-loot-v0.2/MISSOES.md`: cópia de referência deste guia.

Os padrões são criados apenas quando ausentes. Uma pasta de missões já existente e vazia é respeitada. Os arquivos de referência não substituem os ativos.

O padrão habilita apenas `find_plains`. Os exemplos `visit_village` e `visit_meeting_point` ficam desabilitados até você decidir usá-los. Não existe missão automática de visitar fogueira.

## Configuração geral

```json
{
  "enabled": true,
  "checkIntervalTicks": 100,
  "assignmentRetryTicks": 200,
  "progression": {
    "xpPerLevel": 100,
    "healthPerLevel": 1,
    "maxHealth": 30
  }
}
```

O nível começa em 0 e é a divisão inteira de XP total por `xpPerLevel`. Com os padrões, cada 100 XP concede um nível e +1 de vida máxima, até +10 para um NPC com vida base 20. São 30 pontos de vida, ou 15 corações. O nível pode continuar aumentando depois desse limite; o bônus de vida para nesse teto.

O bônus usa um único modificador de atributo identificado, preservando a vida base e os modificadores de outros mods. O limite de 30 se aplica à vida base somada ao bônus deste sistema; outros mods ainda podem modificar o resultado final. Uma vida base que já exceda o limite não é reduzida.

Ao ganhar capacidade de vida, o NPC recebe somente os pontos acrescentados: 12/20 passa a 13/21. Recarregar a entidade não cura nem reaplica bônus cumulativos. Não há interface de nível nesta etapa; os comandos mostram os valores para teste.

`checkIntervalTicks` aceita 20–12000 ticks. As consultas são distribuídas entre NPCs. `assignmentRetryTicks` aceita 20–72000 ticks e controla a espera quando não existe missão elegível. Com `enabled: false`, a atribuição e conclusão automáticas param; XP existente e seu bônus continuam preservados. NPCs com `NoAI` não progridem automaticamente, mas os comandos de teste ainda podem operá-los.

## Uma missão

```json
{
  "enabled": true,
  "weight": 1,
  "xp": 25,
  "loadouts": [],
  "excludedLoadouts": [],
  "dimensions": ["minecraft:overworld"],
  "acceptance": {
    "requireOutsideObjective": true,
    "excludedBiomes": [],
    "excludedStructures": [],
    "excludedPoiTypes": []
  },
  "objective": {
    "type": "biome",
    "targets": ["minecraft:plains"],
    "poiRadius": 8,
    "minTravelDistance": 32,
    "excludedBiomes": [],
    "excludedStructures": [],
    "excludedPoiTypes": []
  },
  "dialogues": {
    "default": {
      "activity": ["I'm looking for open plains."],
      "news": ["I'm still looking for open country nearby."]
    },
    "loadouts": {
      "illagers": { "activity": ["None of your business."] }
    }
  }
}
```

`weight` é o peso entre missões elegíveis; `xp` é a recompensa de conclusão. `loadouts: []` permite todos e `excludedLoadouts` tem prioridade. IDs de loadout e missão diferenciam maiúsculas e minúsculas. `dimensions: []` permite qualquer dimensão; os IDs precisam existir no mundo.

`acceptance` filtra o local na hora de atribuir a missão. `requireOutsideObjective: true` impede entregá-la a quem já está em um destino válido. `excludedBiomes`, `excludedStructures` e `excludedPoiTypes` proíbem origens correspondentes. As três listas homônimas em `objective` proíbem destinos correspondentes. Assim você pode impedir a aceitação em uma região e, separadamente, proibir certos locais para conclusão.

`minTravelDistance` exige distância horizontal em linha reta a partir da posição de atribuição; o padrão é 32 blocos. Não mede o caminho percorrido. Se desabilitar `requireOutsideObjective`, a distância mínima precisa ser pelo menos 1, para evitar conclusão repetida sem sair do lugar. O início e a conclusão precisam ocorrer na mesma dimensão nesta versão.

## Tipos de objetivo

| `type` | `targets` | Como é reconhecido |
|---|---|---|
| `biome` | IDs como `minecraft:plains` | Bioma na posição atual do NPC. |
| `structure` | IDs como `minecraft:village_plains` | Posição dentro da caixa da estrutura registrada na geração do mundo. |
| `poi` | IDs como `minecraft:meeting` | POI registrado dentro de `poiRadius` blocos, com ocupação livre ou ocupada. |

Vários alvos na lista são alternativas: basta um. Use IDs completos com namespace; tags `#...` ainda não são aceitas. Uma vila não usa o ID genérico `minecraft:village`: os exemplos listam suas variantes.

`poiRadius` aceita 1–32 blocos, padrão 8. Ele também vale para as blacklists de POIs da missão. A distância a POIs inclui a altura. Uma fogueira só é um POI porque este mod a registra como `tacznpcs:campfire`; um bloco qualquer não vira POI automaticamente.

Estruturas copiadas manualmente sem dados de estrutura não são reconhecidas como estruturas geradas. Lost Cities só pode usar um objetivo de estrutura se o local em questão tiver esse registro; a integração própria com cidades/prédios continua para uma etapa posterior.

As consultas usam chunks já carregadas. Se faltarem chunks necessárias para confirmar um POI ou o início de uma estrutura relevante, a decisão fica pendente. Não são executados comandos `locate` nem geração/carregamento de regiões para procurar objetivos.

## Conclusão e próxima missão

A conclusão consome a atribuição antes de conceder XP. Uma checagem repetida não recompensa a mesma atribuição novamente. Depois, o NPC sorteia outra missão elegível na posição atual. Se não houver nenhuma, fica sem missão e tenta novamente após o intervalo configurado.

Com apenas `find_plains` habilitada, um NPC que chega às planícies fica sem missão: ele não pode receber outra igual ali. Isso é esperado. Você pode criar outras missões copiando os exemplos e mudando o nome do arquivo, os alvos e as regras.

Missões removidas ou desabilitadas são canceladas na próxima checagem válida, sem XP. Alterações em uma definição mantida com o mesmo ID passam a valer para atribuições existentes. Não troque o significado de um ID se quiser conservar o objetivo anterior.

## Falas preparadas para o menu futuro

`activity` será a resposta para “What are you doing here?”; `news`, para “Any news around here?”. Cada campo aceita uma string ou lista de strings. A exceção de `dialogues.loadouts.<id>` substitui apenas os assuntos preenchidos; os outros herdam `dialogues.default`. Um assunto sem falas retorna vazio para o futuro menu aplicar seus próprios fallbacks.

Nesta etapa, as falas são dados e podem ser consultadas com o comando `dialogue`; elas ainda não substituem o menu atual do ADM. Não há interpolação automática de variáveis nos textos.

## Comandos de teste

Exigem permissão de operador nível 2. Os seletores operam em NPCs carregados; os dados dos descarregados permanecem salvos, mas não são buscados por esses comandos.

```mcfunction
/tacznpcs_reload
/tacznpcs_quest status @e[type=tacznpcs:npc,distance=..16]
/tacznpcs_quest assign @e[type=tacznpcs:npc,sort=nearest,limit=1] find_plains
/tacznpcs_quest check @e[type=tacznpcs:npc,sort=nearest,limit=1]
/tacznpcs_quest xp @e[type=tacznpcs:npc,sort=nearest,limit=1] 100
/tacznpcs_quest dialogue @e[type=tacznpcs:npc,sort=nearest,limit=1]
/tacznpcs_quest clear @e[type=tacznpcs:npc,sort=nearest,limit=1]
```

`assign` respeita as condições e substitui a missão ativa somente se a atribuição for válida; pode retornar zero caso o NPC já esteja no destino. `check` faz uma checagem imediata, inclusive em NPCs com `NoAI`. `clear` preserva XP e adia a nova atribuição pelo intervalo de tentativa. `xp` concede de 1 a 1 bilhão de XP por chamada; os dados internos não sofrem overflow.

Teste simples: atribua `find_plains` fora de planícies, mova o NPC para planícies pelo menos 32 blocos distante e execute `check`. Confira XP e histórico com `status`, repita `check` e confirme que não recebe a recompensa duas vezes. Salve/reabra o mundo para conferir a persistência.

O reload das quests é atômico: erro de sintaxe, campo desconhecido ou ID inválido em uma missão habilitada mantém o conjunto anterior. IDs de conteúdo opcional em exemplos desabilitados não impedem a carga; habilite-os somente depois de configurar alvos existentes.
