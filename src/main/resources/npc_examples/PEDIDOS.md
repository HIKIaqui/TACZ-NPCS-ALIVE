# Pedidos v0.1

Instale `dist/tacznpcs-2.1.0-hiki.requests.1.jar` no cliente e servidor, substituindo o mod anterior. Inclui o comércio e a correção C2ME. O sources.jar é só para desenvolvimento.

## Seleção

Os pedidos usam somente os interesses de `config/tacznpc_inventory.json`:

1. Média aritmética dos pesos positivos das regras de interesse do loadout. Cada regra conta uma vez; uma tag não multiplica seu peso na média pela quantidade de itens que contém. Peso zero não entra na média.
2. Resolvem-se os itens e tags usando a mesma precedência do inventário: item exato vence tag; entre tags, vence a primeira correspondente.
3. Itens com peso efetivo abaixo da média são excluídos.
4. Se o NPC já possui 8 unidades de um item empilhável, ou 1 de um não empilhável, ele é excluído. Somam-se todas as pilhas e também equipamento em uso; a contagem é por ID de item.
5. Também é necessário caber a quantidade pedida na carga, sem expulsar itens. Isso é uma previsão usando a pilha padrão; na entrega o servidor confere os componentes reais e o espaço novamente.
6. Sorteia-se com peso `média / peso do interesse`. Quanto mais próximo da média, maior a chance. Normaliza-se entre candidatos elegíveis. Não se usa o peso de interesse diretamente como peso de sorteio.

A média é calculada antes de excluir itens abastecidos ou sem espaço. Se não houver candidato, não há pedido. Não se reduz a média para forçar um pedido.

O NPC pede a quantidade que falta: se tem 3 unidades de um item empilhável, pede 5; se não possui nenhuma, pede 8. Para itens não empilháveis, pede 1.

## Diálogo e entrega

Em `I Want to Ask Something.` → `Need help with something?`, aparecem o item, a quantidade e a recompensa. A opção `Deliver ...` tenta entregar tudo de uma vez. Não há aceitação separada nem entregas parciais.

Há um pedido por NPC, compartilhado entre jogadores. Ele é criado ao preparar uma conversa elegível e permanece salvo na entidade: reabrir, descarregar chunks e salvar/carregar não sorteiam outro pedido. Quem completar recebe a recompensa. A entrega encerra a conversa; reabra para ver a nova saudação por reputação.

O jogador paga pelos 36 slots principais, incluindo hotbar. Armadura e mão secundária não são consumidas. Os itens entram realmente na carga do NPC, preservando componentes, nomes, dados TaCZ, encantamentos e durabilidade. Não há item virtual nem descarte para abrir espaço. Se faltar quantidade ou espaço, nada é consumido e a reputação não muda.

Os pedidos identificam itens por ID de registro, como as regras de interesse atuais. Não distinguem GunIds entre armas que compartilham `tacz:modern_kinetic_gun`; qualquer variante desse ID pode atender o pedido. Os dados da variante entregue são preservados. Moedas e itens protegidos também podem ser pedidos quando seu interesse satisfizer as mesmas regras; proteção não impede receber itens.

Se o NPC se abastecer sozinho até o limite, ou a regra deixar de ser elegível, o pedido antigo não concede recompensa. Ao reabrir, ele é invalidado e o NPC pode escolher outro candidato. Se ele ganhar apenas parte do estoque, o pedido válido mantém a quantidade originalmente combinada.

Itens recebidos passam a seguir as regras normais de carga, compartilhamento e comércio. Não existe reserva permanente desses itens depois da entrega.

## Configuração

`config/tacznpc_requests.json` é criado automaticamente. Exemplo:

```json
{
  "defaults": {
    "enabled": true,
    "personalReward": 5,
    "factionReward": 0,
    "cooldownTicks": 24000,
    "minimumReputation": -49
  },
  "loadouts": {
    "military": { "factionReward": 1 },
    "raiders": { "enabled": false }
  }
}
```

Cada loadout herda campos omitidos de defaults. Não há segunda lista de itens nem faixas de peso configuráveis: a seleção deriva dos interesses.

- `personalReward` / `factionReward`: inteiros 0..1.000.000. Valor zero não altera essa reputação. Recompensas respeitam a saturação já existente da reputação.
- `cooldownTicks`: 0..1.728.000. Padrão 24000 ticks, um dia de jogo a 20 TPS, a partir da entrega concluída. É por NPC e salvo em NBT. Tempo conta enquanto o servidor simula o mundo, não em tempo real offline.
- `minimumReputation`: inteiro entre -1.000.000.000 e 1.000.000.000, baseado na soma facção + pessoal. Hostilidade e disponibilidade normal para diálogo também são respeitadas.

Recarregue com `/tacznpcs_reload`. A configuração de pedidos é validada junto com os diálogos; falhas mantêm as configurações anteriores. Um pedido já criado mantém sua recompensa e intervalo originais; habilitação, reputação mínima e utilidade do item são conferidas novamente na entrega.

Os limites de estoque são fixos: 8 para empilháveis e 1 para não empilháveis. A capacidade de carga continua configurável de 1 a 36, com padrão 15, como no patch anterior.

## Escopo

Não inclui companhia/seguir, entrega de vários pedidos simultâneos, pedidos por jogador nem preço de recompensa calculado por raridade. O comércio e a correção C2ME continuam incluídos.
