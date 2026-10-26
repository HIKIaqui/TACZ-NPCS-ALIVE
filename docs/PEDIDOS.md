# Pedidos v0.4

Instale `dist/tacznpcs-2.1.0-hiki.requests.4.jar` no cliente e servidor, substituindo o mod anterior. Inclui o comércio e a correção C2ME. O sources.jar é só para desenvolvimento.

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

Em `I Want to Ask Something.` → `Need help with something?`, aparecem o item, a quantidade e o pagamento em moedas. Não aparecem números de reputação. A opção `Deliver ...` tenta entregar tudo de uma vez. Não há aceitação separada nem entregas parciais.

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
    "minimumReputation": -49,
    "currencyRewardMultiplier": 1.5
  },
  "loadouts": {
    "military": { "factionReward": 1 },
    "raiders": { "enabled": false }
  }
}
```

Cada loadout herda campos omitidos de defaults. Não há segunda lista de itens nem faixas de peso configuráveis: a seleção deriva dos interesses.

- `personalReward`: base da reputação pessoal, inteiro 0..1.000.000 (padrão 5). A recompensa é `personalReward + floor(max(0, peso do item - média dos pesos positivos))`. Na média dá 5; cinco pontos acima da média dão 10. A quantidade entregue não multiplica a reputação. O bônus decimal arredonda para baixo.
- `factionReward`: adicional fixo de reputação de facção, inteiro 0..1.000.000, padrão 0. Recompensas respeitam a saturação da reputação. A mensagem após entregar mostra o ganho efetivo, não o valor nominal quando já atingiu o limite.
- `currencyRewardMultiplier`: decimal de 0 a 10000, até quatro casas, padrão 1.5. O valor é `floor(basePrice × quantidade pedida × multiplicador)`, sem os modificadores da loja por reputação. Zero desativa o pagamento em moedas.
- `cooldownTicks`: 0..1.728.000. Padrão 24000 ticks, um dia de jogo a 20 TPS, a partir da entrega concluída. É por NPC e salvo em NBT. Tempo conta enquanto o servidor simula o mundo, não em tempo real offline.
- `minimumReputation`: inteiro entre -1.000.000.000 e 1.000.000.000, baseado na soma facção + pessoal. Hostilidade e disponibilidade normal para diálogo também são respeitadas.

Recarregue com `/tacznpcs_reload`. A configuração de pedidos é validada junto com os diálogos; falhas mantêm as configurações anteriores. Um pedido já criado mantém sua recompensa e intervalo originais; habilitação, reputação mínima e utilidade do item são conferidas novamente na entrega.

Os limites de estoque são fixos: 8 para empilháveis e 1 para não empilháveis. A capacidade de carga continua configurável de 1 a 36, com padrão 15, como no patch anterior.

## Escopo

Não inclui companhia/seguir, entrega de vários pedidos simultâneos, pedidos por jogador nem recompensa variável por durabilidade, GunId ou encantamento. O comércio e a correção C2ME continuam incluídos.


## Pagamento e feedback — atualização v0.2

A recompensa monetária é gerada pela conclusão do pedido, não retirada do dinheiro que o NPC carrega para comércio. Sua falta de moedas no inventário não impede um pedido. O pagamento usa a lista global `commerce.currencies` de `tacznpc_inventory.json`, o mesmo algoritmo de combinação eficiente e o máximo de três tipos. Quando não há representação exata, arredonda para o maior valor representável abaixo do calculado. Se esse valor for zero, não há moeda a entregar. O diálogo informa o pagamento real combinado, já convertido.

As moedas são guardadas junto do pedido em NBT e entregues como itens reais. Se faltar espaço no inventário do jogador para recebê-las, toda a entrega é recusada: não consome suprimentos, não paga parcialmente e não concede reputação. O espaço é avaliado depois de retirar os suprimentos, aproveitando os slots liberados. Não há moedas derrubadas no chão.

Pedidos novos guardam as recompensas calculadas ao serem criados. Pedidos do v0.1 são atualizados uma única vez, preservando seu token, item e quantidade, mas calculando as novas recompensas com a configuração vigente. Alterar a configuração depois de criar um pedido novo não muda seu pagamento já combinado.

A reputação não é anunciada antes da entrega. Após concluir, o servidor envia um aviso temporário na action bar, acima da hotbar, com o ganho pessoal e/ou de facção efetivamente recebido. A recompensa monetária continua sendo uma informação negociável na fala do NPC.

## Identificadores de reputação em inglês

Os identificadores canônicos de código, exemplos e configurações novas passam a ser `villainized`, `hostile`, `hated`, `neutral`, `friendly`, `allied` e `idolized`. Isso vale tanto para `reputationPricePercent` quanto para saudações em `tacznpc_greetings.json`.

Arquivos antigos com as chaves em português continuam sendo lidos como aliases. Os valores personalizados são preservados. Não é feita regravação automática dos arquivos existentes. Declarar a mesma faixa nos dois idiomas na mesma seção é rejeitado para evitar uma escolha ambígua. Os dados de reputação salvos guardam números, não nomes de faixas, e não precisam de migração.


## Combate e reputação — atualização v0.3

Um acerto com dano real de jogador contra um NPC considera a reputação efetiva antes da penalidade. Se ela for de aliado ou acima (padrão >= 50, respeitando `alliedAt`), o NPC tolera o dano: não revida por esse acerto, mas ainda perde 5 pontos pessoais com o jogador. Isso não é imunidade a dano. A reação normal de alerta/fuga e o encerramento de uma conversa por ferimento podem continuar.

Todo acerto com dano real reduz em 5 a reputação pessoal, inclusive de aliados. Abaixo de aliado antes do acerto, o NPC passa a ter o atacante como alvo, se sobreviver. Acertos de projéteis usam seu atirador como responsável. O aviso de perda pessoal aparece acima da hotbar. São considerados acertos com redução de vida; ataques que não causam dano não debitam reputação. Outros NPCs não adotam o atacante por uma agressão que a própria vítima considerou fogo amigo tolerável; continuam sujeitos às suas próprias relações de hostilidade normais.

Matar qualquer NPC deste mod reduz a reputação do jogador em 5 com a facção da vítima. Não altera outras facções. Isso vale mesmo para uma facção já hostil. Se a morte veio de um acerto contra um NPC que não era aliado, a perda pessoal do acerto também ocorre.

A atribuição de mortes aceita o jogador como causador direto ou atirador. Finalizações ambientais podem usar o crédito de morte que o Minecraft ainda atribui a ele. Um mob dando o golpe fatal não é tratado como um ataque do jogador, e mortes ambientais sem crédito não debitam reputação de jogadores.

A perda da facção é confirmada ao fim do tick, depois que os listeners de morte tiveram chance de cancelar o evento. Mortes canceladas não aplicam essa penalidade e a mesma morte não é cobrada duas vezes. As perdas respeitam o limite mínimo de armazenamento de reputação existente (-1.000.000.000); perto desse limite, o débito efetivo pode ser menor que 5.

Essas duas penalidades são fixas em 5 nesta versão. Não há novo campo de configuração necessário.


### Memória de fogo amigo — v0.4

A tolerância é decidida antes do débito, sem apagar a perda de reputação. Exemplo: reputação efetiva 50 → acerto tolerado → 45; o próximo acerto aceito já encontra a relação abaixo de aliado, reduz para 40 e provoca retaliação. A memória vanilla de vingança desse primeiro golpe é limpa após processar o dano, para evitar uma retaliação atrasada que contrariaria a tolerância. O crédito de morte do jogador continua disponível para a penalidade de facção.
