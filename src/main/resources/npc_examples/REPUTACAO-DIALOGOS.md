# Reputação — diálogos, patch 2 de 2

Versão 2.1.0-hiki.reputation.2. Inclui a base completa do patch 1 e todos os recursos
presentes nele. O GitHub continuava no commit 4d0320374e8ed5ae64285f5978890d14d9bd06e7;
este patch foi construído sobre o pacote de reputação 1 entregue no chat.

## Saudações

O novo arquivo config/tacznpc_greetings.json é criado automaticamente se estiver ausente.
Não é necessário substituir tacznpc_dialogue_replies.json nem seus perfis existentes.
O arquivo de saudações contém:

```json
{
  "enabled": true,
  "defaults": {
    "villainized": ["After everything you've done? Get out of my sight."],
    "hostile": ["You have a lot of nerve showing up here."],
    "hated": ["Make it quick. I'm not in the mood."],
    "neutral": [],
    "friendly": ["Good to see you again. Need something?"],
    "allied": ["There you are, friend.", "Always good to have you around."],
    "idolized": ["For you? I'll always make time."]
  },
  "factions": {
    "military": {
      "allied": ["Good to see you. We've got your back."]
    }
  },
  "loadouts": {
    "scientists": {
      "allied": ["A familiar face. I could use a second opinion."]
    }
  }
}
```

Os IDs de faixa são exatamente: vilanizado, hostil, odiado, neutro, amigavel, aliado,
idolizado (sem acentos). As faixas são as mesmas do patch 1. A pontuação consultada
é sempre facção + pessoal daquele jogador com aquele NPC, sem arredondar nem limitar a ±100.

A seleção ocorre separadamente para a faixa atual, nesta ordem:
1. Loadout do NPC.
2. Facção do NPC.
3. defaults.
4. Texto original do nó inicial do perfil escolhido.

Uma string define uma única fala; uma lista define alternativas com a mesma chance.
Lista vazia ou faixa ausente herda da próxima origem. Não se usa uma faixa vizinha como
fallback. Por padrão neutro está vazio, preservando as saudações específicas que você
já escreveu. Para substituir também as neutras, preencha essa faixa.
Use enabled=false para manter somente os greetings originais em todos os NPCs.
IDs de loadout/facção diferenciam maiúsculas e minúsculas.
Cada faixa aceita até 128 alternativas de até 2048 caracteres não vazios.

A frase é sorteada uma vez por abertura, e continua igual ao voltar ao menu inicial.
Reabrir pode sortear a mesma alternativa; não existe exclusão da última fala.
Melhorar/piorar a reputação durante a conversa não troca o texto daquele snapshot;
a próxima abertura usa a nova faixa. O texto é privado daquela conversa, não altera
perfis compartilhados nem a saudação vista por outro jogador.
Somente o texto inicial é substituído; speaker, estilo e demais nós são preservados.

A reputação não remove as condições de segurança existentes. Com hostileAt=-50,
NPCs hostis/vilanizados continuam recusando abrir diálogo. Essas faixas podem ser
configuradas, mas não passam a permitir conversar no meio de um ataque.
Elas se tornam alcançáveis se você alterar o limite de hostilidade, por exemplo para -200.

## Condições de reputação no ADM

Use o novo tipo tnpc.reputation:

```json
{ "type": "tnpc.reputation", "min": 50 }
```

- min: maior ou igual, incluindo faixas superiores.
- max: menor ou igual, incluindo faixas inferiores.
- min e max: intervalo fechado, por exemplo min=25/max=49 para somente Amigável.
- amount: valor exato; use sozinho em vez de min/max.
- expected=false: inverte o resultado, usando o mecanismo padrão do ADM.

Os limites são inteiros entre -2.000.000.000 e +2.000.000.000, compatíveis com a soma
permitida pela base. A condição exige algum limite. count, id, faction ou personal
não selecionam parcelas da reputação: campos não suportados são rejeitados na configuração.

Exemplo de opção personalizada em tacznpc_dialogues/<perfil>.json:

```json
{
  "id": "trusted_information",
  "text": "Can you trust me with something important?",
  "condition": { "type": "tnpc.reputation", "min": 50 },
  "next": "trusted_reply"
}
```

Crie também o nó trusted_reply no mesmo perfil. Opções personalizadas continuam
aparecendo em Other questions conforme o menu existente. A condição é preservada.
Não são adicionadas novas ações de pedidos ou recrutamento neste patch.

Exemplo de regra em tacznpc_dialogue.json para escolher um perfil completo:

```json
{
  "id": "military_trusted",
  "enabled": true,
  "priority": 100,
  "profile": "military_trusted",
  "genericMode": "append",
  "when": {
    "all": [
      { "type": "tnpc.faction", "id": "military" },
      { "type": "tnpc.reputation", "min": 50 }
    ]
  }
}
```

O arquivo tacznpc_dialogues/military_trusted.json precisa existir. As regras continuam
respeitando priority e genericMode. all/any/not e expected são os do ADM.
Não há novas condições separadas para reputação pessoal/facção: toda checagem usa a efetiva.

A disponibilidade das opções é filtrada pelo ADM no servidor ao montar o diálogo e
revalidada ao executar a escolha. Se a reputação cair abaixo do requisito, clicar numa
opção antiga não executa suas ações. Opções que passam a ser elegíveis podem exigir
reabrir a conversa, pois a lista enviada ao cliente é um snapshot.
A seleção do perfil também é refeita somente ao abrir uma nova conversa.

## Reload e teste

Use /tacznpcs_reload. Greetings, replies e perfis de diálogo são analisados antes de
substituir a configuração de diálogo em uso. Um erro de saudação ou condição preserva
as sessões e a última configuração válidas. Um reload válido fecha as conversas atuais,
como antes, para a próxima abertura usar os dados novos.

Use os comandos do patch 1 para testar:

```
/tacznpcs_reputation status @p @e[type=tacznpcs:npc,sort=nearest,limit=1]
/tacznpcs_reputation faction @p military set -100
/tacznpcs_reputation personal @p <uuid-do-npc> set 150
```

Nesse exemplo, apenas o NPC com pessoal=150 vê o jogador como Aliado (+50).
Respeite o cooldown/alerta existente antes de abrir a conversa.
Com pessoal=125, a efetiva será +25 e a próxima abertura usará Amigável.

Instale apenas dist/tacznpcs-2.1.0-hiki.reputation.2.jar no lugar do JAR anterior.
Quem já instalou o patch 1 não precisa migrar novamente os loadouts nem os dados salvos.
Para quem pula direto para o patch 2, a conversão de aligned/opposed ainda é necessária;
veja REPUTACAO.md. O pacote mantém os JSONs convertidos do dump como referência opcional.
