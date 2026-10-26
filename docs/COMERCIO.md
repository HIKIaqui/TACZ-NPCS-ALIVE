# Comércio v0.1 — estoque real, moedas e reputação

Este patch substitui o comércio antigo dos NPCs deste mod. O ADM 0.8.5 continua fornecendo a interface, e o TACZ NPCs controla as ofertas e a transação. As lojas de entidades de outros mods continuam com o comportamento original do ADM.

## Instalação

Use `dist/tacznpcs-2.1.0-hiki.commerce.c2me.1.jar` no lugar do JAR anterior, no cliente e no servidor. O patch já inclui reputação, diálogos, níveis, combate e interesses. O JAR terminado em `-sources` contém código para desenvolvimento e não deve ir na pasta `mods`.

A configuração permanece em `config/tacznpc_inventory.json`. Arquivos existentes não são sobrescritos. Campos novos ausentes usam os padrões abaixo. O exemplo completo no ZIP pode servir de referência para acrescentar apenas os campos desejados à sua configuração atual. Não substitua seus loadouts personalizados pelo exemplo.

`genericTrades`, `factionTrades` e `tradeFile` deixaram de controlar as lojas desses NPCs. Eles podem ser removidos do JSON antigo. Mesmo uma escolha antiga do diálogo apontando para um arquivo de trocas abre o estoque real desse NPC; arquivos estáticos não injetam ofertas ou executam comandos. Não há migração automática das antigas ofertas para `basePrice`.

## O que entra na loja

- Apenas a carga realmente presente, com `basePrice` definido por uma regra de interesse correspondente.
- Itens marcados como `protected` não são vendidos. Equipamento vestido ou empunhado não faz parte da carga e também fica fora.
- As moedas configuradas ficam fora das mercadorias, mesmo que tenham regra de interesse.
- Pilhas com o mesmo item e os mesmos componentes formam uma oferta. Componentes diferentes formam ofertas separadas: armas TaCZ, encantamentos, dano, nomes e demais dados são preservados na entrega e na prévia da interface.
- Uma oferta representa uma unidade. O seletor de quantidade do ADM permite comprar múltiplas unidades, até seu limite de 64, multiplicando o pagamento fixo.
- Todas as ofertas relevantes são listadas, com rolagem do ADM. Não há limite próprio de 8, 12 ou 15 ofertas.

A identificação das regras de interesse continua sendo por ID de item ou tag. Duas armas que compartilham `tacz:modern_kinetic_gun` podem ter ofertas diferentes por seus componentes, mas usam a mesma regra de preço desse item. Este patch não acrescenta preços por GunId, encantamento, munição ou durabilidade.

## Preço por item e por reputação

O `basePrice` de cada regra é o preço de uma unidade, na menor unidade monetária da economia, independente do `weight` de interesse:

```json
{
  "item": "minecraft:bread",
  "weight": 5,
  "basePrice": 100,
  "protected": false
}
```

Regras antigas em string continuam válidas, com `weight: 1`, `basePrice: 1`, `protected: false`. Uma regra exata de item vence uma tag; entre tags, vence a primeira correspondente da lista. O preço não é somado entre regras.

A reputação efetiva soma facção e pessoal com aquele NPC. Primeiro é aplicado o percentual de sua faixa, depois o resultado é convertido em moedas:

`preço = max(1, floor(basePrice × (1 + percentual / 100)))`

Os padrões são um ponto de partida configurável:

| Faixa | Percentual |
| --- | ---: |
| vilanizado | +100% |
| hostil | +50% |
| odiado | +25% |
| neutro | 0% |
| amigavel | -5% |
| aliado | -10% |
| idolizado | -20% |

O bloqueio de comércio por hostilidade/reputação continua valendo. Os percentuais das faixas hostis só têm efeito se a política de reputação permitir o comércio nessa faixa.

Dentro de `defaults` ou de cada entrada de `loadouts`, use:

```json
"reputationPricePercent": {
  "neutral": 0,
  "hated": 25,
  "friendly": -5,
  "allied": -10,
  "idolized": -20
}
```

Percentual positivo aumenta o preço; negativo dá desconto. O loadout herda as faixas que não declarar de `defaults`. São aceitos números maiores que -100 e até 10000, com até quatro casas decimais. Chaves usam as faixas sem acentos.

O preço é calculado ao abrir a loja e permanece fixo nessa sessão. Reabrir atualiza a reputação, os preços e o conjunto de mercadorias. Cair abaixo da reputação necessária bloqueia imediatamente novas compras, mesmo com a tela aberta.

## Moedas

Na raiz do mesmo JSON, ao lado de `defaults` e `loadouts`:

```json
"commerce": {
  "currencies": [
    { "item": "minecraft:emerald", "value": 1 },
    { "item": "minecraft:emerald_block", "value": 60 },
    { "item": "minecraft:netherite_block", "value": 3600 }
  ]
}
```

Pode configurar quantos tipos quiser. `value` é um inteiro positivo até 1.000.000.000. Não precisa haver uma progressão regular e a ordem da lista não altera o resultado. Não pode repetir um ID de item, e cada item precisa existir no servidor. Um bloco de esmeralda vale 60 neste sistema por configuração, independente da receita vanilla de 9 esmeraldas. Se `commerce` estiver ausente, esses três tipos são usados.

O algoritmo tenta pagar o preço exato com a menor quantidade física de moedas, usando no máximo três tipos e uma pilha por tipo para o preço unitário. O limite da pilha vem do próprio item, até o limite de contagem 999 do ADM. Se não houver representação exata, escolhe o maior valor possível abaixo do preço e, entre combinações desse valor, a menor quantidade de moedas. Empates são resolvidos de maneira determinística.

Exemplos com os padrões:

- Preço 100: 1 bloco de esmeralda + 40 esmeraldas.
- Preço 3600: 1 bloco de netherite.
- Preço 3661: 1 bloco de netherite + 1 bloco de esmeralda + 1 esmeralda.
- Com moedas de valores 4, 3 e 1, preço 6 usa duas moedas de 3: a busca não depende de uma escolha gulosa.

Se até o menor pagamento positivo exceder o preço, a oferta fica fora da loja. Não há aumento para a menor moeda nem entrega gratuita. A compra em quantidade multiplica a combinação unitária exibida; ela pode consumir várias pilhas do inventário.

A compra cobra os itens exatos exibidos. Não converte as moedas que o jogador carrega, não aceita pagamento equivalente em outros tipos e não fornece troco.

## Inventários e atualização

O pagamento sai dos 36 slots principais do jogador, incluindo hotbar, e entra na carga do NPC. A mão secundária e os slots de armadura não são usados para pagar. A carga continua com 15 slots por padrão e `maxCargoSlots` configurável de 1 a 36 por loadout.

A transação confere o espaço depois de retirar a mercadoria e o pagamento, permitindo aproveitar os slots liberados. Se o jogador ou o NPC não comportar o resultado, a compra é recusada por inteiro, preservando os dois inventários. Não há descarte automático para caber pagamento. O tratamento de carga antiga acima da capacidade reduzida continua preservando esses itens.

As moedas recebidas são itens reais. Sua prioridade de retenção continua seguindo as regras de interesse: configure também o interesse das moedas se quiser que o NPC prefira guardá-las diante de outros achados. O simples registro como moeda não altera o `weight`.

Após uma compra, a tela recebe a quantidade restante e bloqueia ofertas esgotadas sem fechar a sessão. Outras alterações na carga também são verificadas no momento da compra; mercadorias novas aparecem ao reabrir. Um item com os mesmos dados deve continuar existindo para a oferta ser atendida.

O servidor rejeita quantidades inválidas, oferta/índice divergentes, pacotes de uma tela anterior e compras duplicadas no mesmo tick. Um recarregamento válido de `tacznpc_inventory.json` exige reabrir a loja; um recarregamento inválido mantém a política anterior. Use `/tacznpcs_reload` (operador); esse comando também recarrega os outros sistemas e encerra conversas quando o recarregamento de diálogos conclui.

A interface continua sujeita ao limite de texto já existente no protocolo do ADM. Não foi adicionado sistema de páginas nem um limite preventivo de ofertas.

## Próximos passos

Este patch oferece compra do jogador ao NPC. A venda direta de itens do jogador para o NPC, pedidos com recompensas de reputação e companhia ainda ficam para os próximos patches.
