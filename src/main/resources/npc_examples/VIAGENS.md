# Viagens offline — revisão experimental

Esta revisão acrescenta `move` à carteira existente e um registro mundial
`tacznpcs_travel` em SavedData do Overworld. A funcionalidade começa **desativada**.
O build e os testes descritos abaixo passaram. Mantenha o primeiro teste em uma cópia do mundo.

## Comportamento

- Um NPC com missão ativa pode partir fora da observação dos jogadores. Há uma
  tentativa na descarga normal da entidade e também a ação ponderada `move`
  quando ele possui saldo offline. Combate, alerta, fogo, conversa, passageiros,
  montaria, coleira e o intervalo após uma chegada impedem a partida.
- O registro preserva uma serialização completa do NPC. Não há entidade substituta
  tickando, caminho simulado, tickets de chunks ou busca `locate`.
- A cada passagem, uma fila circular processa um número limitado de registros e
  amostra posições em chunks já carregadas em torno dos jogadores. A fila não
  recomeça quando outro NPC parte; recibos de mortos saem da fila.
- Biomas, estruturas e POIs usam as condições da missão existente, incluindo
  exclusões, dimensão e distância mínima desde a origem da missão. Estruturas cujo
  início está descarregado e consultas de POI incompletas ficam pendentes.
- A distância cobrada é horizontal e em linha reta desde a posição de partida:
  `ceil(distância / blocksPerPoint * move.cost * ticksPerPoint)` ticks, mínimo 1.
  O saldo, incluindo o tempo offline desde a partida e créditos administrativos,
  é calculado sob demanda. Só a chegada cobra o percurso; tentativas falhas não.
- A chegada usa chão firme, espaço livre, área carregada com entidades prontas,
  exclusão de jogadores e teste de visibilidade. Sem posição válida, aguarda.
- A conclusão, XP, vida máxima e próxima missão usam `NpcQuests.check`. O snapshot
  de chegada inclui pagamento e recompensa antes da admissão da entidade.
- UUID e geração identificam a cópia autorizada. Cópias antigas da origem são
  recusadas ao entrar. Recibos permanecem no registro para reconhecer cópias
  antigas após reiniciar. Recuperação espera terreno, armazenamento de entidades
  e simulação prontos; uma chunk de terreno carregada sozinha não prova ausência.
- O comando de retorno exige a origem carregada, segura e fora de observação.
  Missões removidas ou desativadas também tentam retornar à origem.

## Configuração e migração

`config/tacznpc_travel.json` é criado no início do servidor:

```json
{
  "enabled": false,
  "blocksPerPoint": 32.0,
  "playerExclusionRadius": 64,
  "intervalTicks": 20,
  "recordsPerPass": 4,
  "sitesPerRecord": 8,
  "arrivalCooldownTicks": 1200
}
```

32 blocos por ponto é um valor provisório para testes, não um balanceamento
aprovado. Com `ticksPerPoint=1200` e `move.cost=1`, 320 blocos custam 12.000 ticks.
São ticks do mundo; o tempo com o servidor desligado não rende crédito.

O arquivo offline existente continua válido. A nova ação herda por padrão
`{"enabled":true,"cost":1,"weight":1}`; pode ser substituída em `offlineActions`
global ou por loadout. O interruptor geral de viagens continua desativado.
`weight` participa da escolha enquanto a entidade está ativa; na descarga há uma
tentativa própria, limitada pelo orçamento global de ações, sem sorteio de peso.

Use `/tacznpcs_reload` para recarregar. Uma configuração inválida mantém a anterior.
Não apague SavedData ou remova o mod enquanto houver viajantes: o registro pode
ser a única cópia atual do inventário. Faça backup antes de testar a migração.
Desativar viagens pausa os viajantes; `recall` continua permitindo retorno.

## Administração

```
/tacznpcs_travel list
/tacznpcs_travel status <uuid>
/tacznpcs_travel recall <uuid>
```

As consultas não carregam a origem. `status` exibe saldo projetado, missão, XP,
dimensão, geração e fase. É uma consulta ao último snapshot do registro: para um
NPC residente, ele pode estar desatualizado até o próximo salvamento da entidade.

## Compilar

Java 21, na raiz do projeto:

```
.\gradlew.bat clean build
```

O wrapper JAR está incluído nesta revisão. As dependências reais estão no
`build.gradle`: NeoForge 21.1.262, TaCZ 1.1.8-hotfix-r7 (ou o JAR local em `libs`),
Player Animator 2.0.1+1.21.1 e ADM 0.8.5. O mod e fontes saem em `build/libs/`.
Há um workflow de build nos fontes para eventual uso futuro no GitHub; ele não foi publicado nem executado nesta entrega.

## Instalação da prévia compilada

1. Feche o jogo/servidor e faça backup do mundo.
2. Substitua o JAR anterior de **TACZ NPCs** pelo JAR principal em `dist/`;
   o arquivo terminado em `-sources.jar` é apenas código fonte. Mantenha TaCZ,
   Player Animator e ADM e não deixe duas versões de TACZ NPCs na pasta de mods.
3. Inicie o servidor uma vez para gerar `config/tacznpc_travel.json`.
4. Na cópia de testes, altere `enabled` para `true` nesse arquivo e execute
   `/tacznpcs_reload` ou reinicie o servidor.
5. Use os comandos de missões e `/tacznpcs_travel list` para acompanhar os NPCs.
   Configurações anteriores são preservadas; não é necessário substituí-las
   pelas configurações de exemplo.

## Evidência e pendências

Executado nesta revisão com Java 21 e NeoForge 21.1.262:

- `clean build`: JAR principal e fontes correspondentes gerados.
- `TravelPureTest`: **36 verificações** de custo, carteira, cooldown, limites e
  admissão por geração.
- Testes existentes de comportamento/configuração, diálogos (**19 verificações**),
  compatibilidade com ADM 0.8.5 e percepção (**40 verificações**) aprovados.
- Servidor dedicado local em um mundo novo: **47 verificações** aprovadas.
- Nova inicialização desse mesmo mundo: **35 verificações** aprovadas, incluindo
  missão/XP/ferimentos/equipamento persistentes, viajante pendente, recuperação
  de uma chegada registrada sem entidade em disco e morte sem ressurreição.
- Partida e chegada por bioma e POI, gasto de saldo, ausência de chegada sem saldo,
  retorno com item solto no chão, conclusão única e rejeição da cópia antiga pelo
  evento de entrada foram exercitados no servidor.

Os testes usam terreno plano, chunks preparados pelo próprio teste e um jogador
artificial. A descarga é disparada pelo evento de remoção da entidade. Isso não
substitui testar descarga natural pela distância do jogador e carregar depois um
arquivo antigo da chunk de origem no modpack completo. A preservação de componentes
foi verificada com cargo vanilla marcado por `CUSTOM_DATA` e equipamento danificado;
armas TaCZ reais ainda precisam de teste no modpack.

A tentativa anterior de compilação falhou no NeoForm. A nova tentativa passou
pela preparação dos artefatos; também foi corrigida uma função de cálculo de
cooldown ausente. O teste no servidor identificou e permitiu corrigir uma posição
de retorno bloqueada indevidamente por itens soltos. Os testes acima foram
executados após essas correções. Nenhum build foi publicado no GitHub.

O teste de servidor pode ser reproduzido num diretório de desenvolvimento
**descartável**, com EULA aceita e mundo plano novo:

```
.\gradlew.bat -I validation/server-validation.gradle runServer
```

Execute duas vezes para a fase de persistência. O harness altera configurações,
prepara chunks e encerra o servidor ao terminar; não o execute apontando para seu
mundo de jogo. Ele fica fora do JAR principal de distribuição. O resultado é
registrado em `run/travel-probe-results.txt`; confira `PASSED`, pois o processo
pode encerrar normalmente mesmo se uma asserção do harness falhar.

Antes de habilitar em produção, validar em uma cópia do mundo:

1. Atribuir uma missão fora do objetivo e anotar UUID, equipamento, componentes
   TaCZ, inventário, vida, XP e saldo. Partir sem jogador observando.
2. Visitar primeiro o destino, deixando a origem descarregada. Sem saldo suficiente
   deve aguardar; com saldo suficiente deve chegar apenas uma vez.
3. Repetir para bioma, estrutura e POI, incluindo exclusões, distância mínima,
   terreno inseguro e estruturas com o chunk de início descarregado.
4. Reiniciar durante viagem e após chegada; carregar a origem depois; comparar
   todos os dados e confirmar uma entidade, um pagamento e uma recompensa.
5. Testar morte, cancelamento de spawn por outro mod, retorno, reload inválido,
   missão removida, cooldown e parada limpa do servidor.
6. Injetar interrupções entre gravação do registro e salvamento de entidades.
   O registro usa NBT no formato SavedData, arquivo temporário e substituição
   atômica; erros de IO impedem uma nova partida/chegada. A gravação normal do
   armazenamento de entidades é separada: **não há transação entre os dois nem
   garantia de fsync**. A consistência após
   falha de disco ou desligamento abrupto continua uma pendência de validação.

A amostragem é limitada, não exaustiva: um destino válido pode demorar a ser
encontrado. Não há índice espacial persistente dos destinos nesta revisão; o
custo por passagem é limitado pela fila e pelo número de amostras, sem varrer
todos os NPCs a cada chunk carregada. Recibos históricos permanecem no arquivo,
que cresce conforme NPCs distintos participam. Acompanhamento e novos menus de
diálogo ficam para outro patch.
