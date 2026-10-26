# Interesses, preços base e capacidade

Versão: 2.1.0-hiki.interests.1. Inclui os dois patches de reputação e recursos anteriores.
Esta etapa define interesses e capacidade. No patch Comércio v0.1, `basePrice` também passou a controlar os preços reais da loja, com moedas e reputação. Consulte `COMERCIO.md` para as regras atuais de comércio.

## Capacidade

Em config/tacznpc_inventory.json:

```json
{
  "defaults": {
    "maxCargoSlots": 15,
    "pickupRetryTicks": 100,
    "interests": []
  },
  "loadouts": {
    "survivors": { "maxCargoSlots": 15 },
    "military": { "maxCargoSlots": 20 },
    "scientists": { "maxCargoSlots": 12 }
  }
}
```

Mescle essas opções no arquivo existente, preservando canTrade, tradeFile, compartilhamento
e outras configurações. A capacidade padrão é 15 slots. O máximo permitido é 36,
usando Inventory.INVENTORY_SIZE do Minecraft: 27 posições de mochila + 9 da hotbar.
Armadura/arma equipadas ficam nos slots de equipamento e não ocupam a carga.
Backpacks de outros mods não ampliam o teto. Valores fora de 1..36 são rejeitados.
Campos ausentes usam o padrão; cada loadout pode substituir a capacidade herdada.
Um slot comporta uma pilha no limite próprio daquele item: arma não empilhável ocupa
um slot, 64 itens comuns equivalentes podem ocupar apenas um.

NBT/carga já existentes não são truncados ao carregar nem quando você reduz o limite.
O mesmo vale para extras iniciais de templates: são preservados antes de o NPC ganhar
sua posição final. Esse excesso legado não ganha novos slots por coleta/administração;
pode ser consumido/vendido e receber itens compatíveis em pilhas incompletas.
As capacidades configuradas para carga nova nunca excedem o inventário principal do jogador.

## Regras de interesse

Strings antigas continuam válidas: "minecraft:bread" ou "#minecraft:planks" equivalem
a peso 1, preço base 1 e protected=false. A forma expandida permite:

```json
{
  "defaults": {
    "maxCargoSlots": 15,
    "pickupRetryTicks": 100,
    "interests": []
  },
  "loadouts": {
    "survivors": {
      "interests": [
        { "item": "minecraft:bread", "weight": 5, "basePrice": 2 },
        { "tag": "minecraft:planks", "weight": 1, "basePrice": 1 },
        { "item": "minecraft:oak_planks", "weight": 2, "basePrice": 1 },
        { "item": "minecraft:golden_apple", "weight": 20, "basePrice": 80, "protected": true }
      ]
    }
  }
}
```

Os números do exemplo são ilustrativos para configurar o seu balanceamento.
- item ou tag: exatamente um deles. Inclua namespace; no campo tag não escreva #.
- weight: interesse por unidade, de 0 a 1.000.000, podendo ser decimal. Zero não atrai coleta.
- basePrice: preço por unidade na menor unidade monetária configurada, inteiro de 1 a 1.000.000.000.
- protected: não descartar, compartilhar nem entregar em troca a partir da carga.

IDs duplicados na mesma lista são rejeitados, inclusive string e objeto para o mesmo ID.
Um item exato prevalece sobre tags, mesmo se a tag aparecer primeiro. Entre várias
tags correspondentes, a primeira na lista vence. A regra vencedora define peso, preço
e proteção; não se somam os valores das regras. A lista do loadout substitui inteira
a lista de defaults, como antes. Campos de uma regra nova têm padrões 1/1/false.
Um erro mantém a última configuração de inventário válida.

A correspondência atual é por ID de item/tag, não por componentes. Armas TaCZ que usam
o mesmo item registrado compartilham essa regra. Seus componentes são preservados;
valores específicos por gun ID não foram acrescentados neste patch.

## Coleta e substituição

Primeiro, o NPC completa pilhas compatíveis. Depois ocupa slots livres. Se lotar:
1. Calcula a utilidade da porção que caberia numa pilha: weight × quantidade.
2. Procura a pilha substituível de menor utilidade na carga.
3. Só considera trocar se a utilidade nova superar a pilha antiga inteira.
4. A chance é 1 − utilidade antiga / utilidade nova.

Exemplo: nova utilidade 100, antiga 25 -> 75% de chance. Valores iguais ou menores não
trocam nem gastam um sorteio. Um item com interesse 2 não substitui 64 itens com interesse 1.
A pilha antiga precisa liberar um slot inteiro; ela cai no chão sem perder componentes.
Pilhas do mesmo item com os mesmos componentes não são substituídas por si próprias.
Equipamento em uso não participa da seleção de vítimas; carga protegida também não.

Se só parte do item encontrado couber, o resto permanece na entidade no chão.
Um teste recusado tem cooldown por NPC + entidade de item (pickupRetryTicks, 100 ticks
por padrão = 5 segundos a 20 TPS). Não há nova tentativa aleatória todo tick para o
mesmo item; a memória dessas recusas é temporária e limitada a 128 entidades por NPC.
Itens descartados recebem pickup delay de 100 ticks.

Coleta automática continua usando a varredura próxima do Minecraft e suas regras de
mobGriefing/pickup delay. Não foi adicionado um novo objetivo que percorra o mundo
procurando loot. /tacznpcs_inventory collect também respeita interesses/capacidade.
Loot offline continua vindo das listas de bioma, sem mudar pesos dos sorteios nem
rerrolar a loot table. Depois passa pelo mesmo filtro/plano de substituição da carga.
Itens virtuais recusados não se tornam estoque; itens antigos substituídos caem no chão.

A carga só muda quando os drops de substituição foram aceitos pelo mundo. Se um evento
cancelar um drop, o plano é recusado e os drops anteriores daquele plano são removidos.
A entidade de loot original permanece intacta. Isso não é uma transação de disco entre
chunks: valem as condições normais de salvamento do Minecraft.

## Equipamento, compartilhamento e comércio existente

A troca de equipamento preserva a peça antiga na carga, se houver espaço. Caso não haja,
a peça antiga pode cair no chão. Se ela for protegida, ou seu drop for cancelado, o upgrade
é recusado e o equipamento original continua no slot. Durante a montagem inicial do
template, extras são preservados; não se cria drop na posição provisória do construtor.

Compartilhamento exige as regras sociais anteriores, interesse do receptor e espaço
livre/empilhável. Não substitui a carga do receptor nem retira itens protegidos do doador.
Se o receptor não aceitar a unidade, nada sai do inventário do doador.

O comércio atual também respeita a capacidade ao receber pagamento/itens. Pode aproveitar
um slot liberado pela mercadoria vendida. Se a operação não couber, é recusada sem cobrar
o jogador. Não descarta mercadorias para acomodar o pagamento.
Itens protegidos não podem sair por ofertas genéricas ou arquivos personalizados;
as ofertas de entrega desses itens ficam indisponíveis. Isso inclui qualquer item
protegido usado como pagamento do próprio NPC na loja antiga de compra de mercadorias.
Equipamento em uso continua fora do estoque comercializável.
Proteção não impede drops normais na morte do NPC nem ações administrativas explícitas.
Reservas dinâmicas de pedidos serão integradas quando o sistema de pedidos existir.

## Comandos e instalação

Use /tacznpcs_reload para aplicar o JSON. Os comandos continuam exigindo operador:

```
/tacznpcs_inventory list @e[type=tacznpcs:npc,sort=nearest,limit=1]
/tacznpcs_inventory give @e[type=tacznpcs:npc,sort=nearest,limit=1] minecraft:bread 64
```

list mostra ocupação/limite da carga e peso/preço base/proteção das pilhas.
give ignora o interesse, mas respeita espaço; informa quantas unidades aceitou.
O comando só cria a quantidade aceita. Não dropa o restante sintético solicitado.

Instale dist/tacznpcs-2.1.0-hiki.interests.1.jar no lugar do JAR anterior.
Não precisa refazer a migração de facções/reputação. O NBT de carga mantém o formato.
Os interesses em strings continuam funcionando; acrescente objetos conforme quiser.
Os exemplos completos estão em project/src/main/resources/npc_examples/config/.
