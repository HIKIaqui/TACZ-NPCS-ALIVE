# Configurar conversas

## Regras

Exemplo de `config/tacznpc_dialogue.json`:

```json
{
  "enabled": true,
  "maxDistance": 5.0,
  "damageCooldownTicks": 100,
  "maxDurationTicks": 2400,
  "genericProfile": "generic",
  "genericWhenUnmatched": true,
  "rules": [
    {
      "id": "guard_loadout",
      "priority": 200,
      "profile": "guard",
      "genericMode": "disabled",
      "when": {"type": "tnpc.loadout", "id": "guard"}
    },
    {
      "id": "stalker_faction",
      "priority": 100,
      "profile": "stalkers",
      "genericMode": "append",
      "when": {
        "all": [
          {"type": "tnpc.faction", "id": "stalkers"},
          {"type": "dimension", "id": "minecraft:overworld"}
        ]
      }
    }
  ]
}
```

Crie `guard.json` e `stalkers.json` na pasta `config/tacznpc_dialogues` antes de ativar essas regras. Nomes de perfil aceitam letras minúsculas, números, `_` e `-`, sem extensão ou caminho. IDs das regras devem ser únicos.

A maior prioridade vence. Empates usam a ordem no arquivo; a ordem interna das facções do NPC não interfere. `when` ausente significa sempre. `enabled: false` desativa uma regra.

| Configuração | Comportamento |
| --- | --- |
| `genericMode: "append"` | Mantém saudação/assuntos específicos e acrescenta assuntos do início do perfil genérico antes da despedida. |
| `genericMode: "fallback"` | Usa só o específico se houver algum assunto disponível no início; caso contrário usa o genérico. |
| `genericMode: "disabled"` | Usa somente o perfil específico. |
| `genericWhenUnmatched: true` | Usa o genérico se nenhuma regra corresponder. |
| `genericWhenUnmatched: false` | Não abre conversa se nenhuma regra corresponder. |
| `enabled: false` na raiz | Desativa todas as conversas desta integração. |

Não há prioridade implícita por categoria: coloque regras de UUID/loadout acima de facção se quiser essa precedência. Uma regra que corresponde encerra a seleção; `fallback` não procura a próxima regra.

`maxDistance` aceita 1–16 blocos; também respeita o limite global do ADM. `damageCooldownTicks` aceita 0–12000; `maxDurationTicks`, 20–72000. Vinte ticks equivalem a um segundo em condições normais. O padrão limita a conversa a dois minutos. Dano recente bloqueia a conversa mesmo sem alvo atual. A proximidade de um inimigo só interrompe quando a IA detecta e seleciona esse alvo; este patch não cria um novo estado de alerta.

## Condições extras

Estas condições podem aparecer em `when` ou no campo `condition` de uma opção:

| Tipo | Parâmetros | Significado |
| --- | --- | --- |
| `tnpc.faction` | `id` | O conjunto `aligned` do template contém esse ID exato. |
| `tnpc.loadout` | `id` | ID exato do loadout atual do NPC. |
| `tnpc.uuid` | `id` | UUID exato do NPC. |
| `tnpc.healthy` | `min`, `max` | Fração de vida atual do NPC, entre 0 e 1. |

`tnpc.faction` usa os mesmos identificadores já usados nos templates, inclusive categorias se estiverem em `aligned`; não cria nem altera facções. Use `all`, `any`, `not` e `expected: false` para combinar/inverter condições. As condições nativas do ADM (como dimensão, bioma, horário, flags, itens e nível) também funcionam. `health` nativo se refere ao jogador; use `tnpc.healthy` para o NPC.

As condições das opções são verificadas pelo ADM ao abrir e novamente ao receber a escolha no servidor. Esta integração não atualiza em tempo real o menu já aberto quando uma condição muda; reabra a conversa para atualizar as opções visíveis.

## Escrever um perfil

Os perfis usam o JSON nativo do ADM, com um `start` fixo e `nodes`. Exemplo `stalkers.json`:

```json
{
  "title": "Conversation",
  "start": "greeting",
  "nodes": {
    "greeting": {
      "text": "What do you need, traveler?",
      "choices": [
        {"text": "Tell me about your group.", "next": "group"},
        {"text": "You look hurt. Are you okay?", "next": "hurt", "condition": {"type": "tnpc.healthy", "max": 0.5}},
        {"text": "Goodbye.", "close": true}
      ]
    },
    "group": {
      "text": "We share supplies and watch each other's backs.",
      "choices": [{"text": "Another question.", "action": "back"}]
    },
    "hurt": {
      "text": "I've had better days. I'll manage.",
      "choices": [{"text": "Stay safe.", "close": true}]
    }
  }
}
```

`next` vai para outro nó; `action: "back"` retorna ao anterior; `close: true` encerra. Para esta primeira versão, use `close` apenas em opções de despedida, sem combinar com `next`.

Com `append`, os nós genéricos recebem IDs internos novos para evitar colisões; os links `next` são remapeados. A saudação e o estilo do perfil específico são mantidos. Escreva os assuntos genéricos com `action: "back"` para retornarem ao menu em que foram abertos. `random_start` não é aceito neste integrador: use regras para escolher saudações por contexto. Em `fallback`, despedidas com `close: true` não contam como assunto disponível.

Clique agachado permite passar pela interação normal da entidade. Hostilidade, combate, dano, distância e linha de visão são sempre verificados no servidor e não podem ser removidos apenas com uma condição de texto.
