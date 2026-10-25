# Combate de NPCs — v0.1

Esta versão inclui o patch de diálogos e as viagens anteriores. Acrescenta reação
por alvo, erro de mira próprio do NPC por distância, intervalo entre tiros,
rajadas, pausas e distância de uso da mira. Não altera os arquivos das armas.

## Instalação e migração

Substitua o JAR anterior de TACZ NPCs pelo principal em `dist/`, sem instalar
`-sources.jar`. Mantenha TaCZ, ADM e Player Animator nas mesmas versões.
Os JSONs antigos continuam válidos: a ausência de `combat` usa os padrões novos.
O patch não reescreve sua configuração existente nem os dados persistentes dos NPCs.

Para editar, acrescente `combat` em `config/tacznpc_behavior.json` e/ou em
`loadouts.<id>.combat`. O exemplo completo acompanha os fontes em
`src/main/resources/npc_examples/config/tacznpc_behavior.json`.
Execute `/tacznpcs_reload` após alterações. Configurações inválidas preservam o
snapshot anterior de comportamento inteiro. Chaves desconhecidas dentro de
`combat` são rejeitadas para identificar erros de digitação.

## Padrões

```json
"combat": {
  "reactionTimeTicks": {"min": 10, "max": 24},
  "targetMemoryTicks": 40,
  "aimDistance": 10,
  "baseAimErrorDegrees": 0.6,
  "accuracyByDistance": [
    {"distance": 0, "spreadMultiplier": 1},
    {"distance": 15, "spreadMultiplier": 1.4},
    {"distance": 35, "spreadMultiplier": 2.5},
    {"distance": 60, "spreadMultiplier": 4}
  ],
  "shotIntervalTicks": {"min": 2, "max": 4},
  "burstShots": {"min": 1, "max": 2},
  "burstPauseTicks": {"min": 18, "max": 36}
}
```

Este trecho é um membro do JSON, não um arquivo completo. Os intervalos incluem
os dois extremos. A 20 TPS, 20 ticks correspondem a um segundo de simulação.
O balanceamento é um ponto de partida editável, não uma medição de dificuldade
do modpack inteiro.

- `reactionTimeTicks`: sorteado ao reconhecer um alvo diferente. A arma pronta
  não ignora o prazo. Recarga, saque, ferrolho e a cadência nativa continuam
  podendo atrasar o tiro além desse prazo.
- `targetMemoryTicks`: tolerância a ticks sem contato visual. Uma oclusão breve
  não reinicia a reação; um reencontro após perda prolongada exige nova reação.
  Zero exige reação após qualquer tick de perda, mas não sob visão contínua.
  A observação continua durante outras ações de IA, inclusive recarga.
- `aimDistance`: distância real em blocos para usar ADS. Corrige o antigo limiar
  que comparava distância ao quadrado com `10`, equivalente a cerca de 3,16 blocos.
- `baseAimErrorDegrees`: raio máximo do erro angular próprio do NPC na escala 1.
  Zero elimina apenas esse erro extra; não transforma a arma em precisão perfeita.
- `accuracyByDistance`: multiplica o erro próprio do NPC. Maior significa pior
  precisão. Os pontos devem começar em zero e ter distâncias estritamente
  crescentes. Há interpolação entre pontos e manutenção do último fator depois
  do último ponto. A dispersão normal do TaCZ permanece intacta.
- `shotIntervalTicks`: intervalo mínimo sorteado após um disparo bem-sucedido.
  Não é somado à cadência nativa: o NPC precisa satisfazer ambos os controles.
  Zero remove apenas esta espera extra. A IA continua fazendo no máximo uma
  tentativa por tick, sem loops para compensar falhas ou tempo perdido.
- `burstShots`: quantidade de disparos bem-sucedidos na rajada da IA. Tentativas
  recusadas pelo TaCZ não contam como tiros.
- `burstPauseTicks`: contador de pausa entre rajadas, separado do intervalo
  entre tiros. As transições de estado e a arma podem acrescentar tempo.

O erro angular é sorteado em disco em torno da mira. Os valores de pitch/yaw
ficam constantes durante uma tentativa, inclusive se o TaCZ consultar os
suppliers várias vezes; a dispersão original dos projéteis/pellets é mantida.
Não altera dano, RPM da arma, recuo do jogador ou os JSONs dos gunpacks.

## Tipos de arma

Além dos campos comuns, `combat.weapons` aceita `default`, `pistol`, `rifle`,
`sniper`, `shotgun`, `smg`, `mg` e `rpg`. Cada objeto aceita apenas os três campos
de tiro: `shotIntervalTicks`, `burstShots` e `burstPauseTicks`.

Os padrões específicos são:

| Tipo | Intervalo mínimo (ticks) | Rajada | Pausa (ticks) |
|---|---|---|---|
| default / rifle | 2–4 | 1–2 | 18–36 |
| pistol | 4–8 | 1–2 | 16–32 |
| smg | 2–4 | 1–3 | 20–36 |
| mg | 2–4 | 1–3 | 24–44 |
| sniper | 0 | 1 | 40–70 |
| shotgun | 2–4 | 1 | 28–48 |
| rpg | 2–4 | 1 | 50–90 |

O tipo vem da categoria real do gunpack, não de uma adivinhação pelo calibre.

## Exemplo por loadout

```json
"loadouts": {
  "survivors": {
    "combat": {
      "reactionTimeTicks": {"min": 16, "max": 32},
      "baseAimErrorDegrees": 0.9,
      "burstPauseTicks": {"min": 24, "max": 48}
    }
  },
  "military": {
    "combat": {
      "reactionTimeTicks": {"min": 6, "max": 14},
      "baseAimErrorDegrees": 0.3,
      "weapons": {
        "rifle": {
          "burstShots": {"min": 2, "max": 3},
          "shotIntervalTicks": {"min": 1, "max": 2}
        }
      }
    }
  }
}
```

Use os IDs reais dos seus loadouts. A herança é independente por campo:
padrões internos → `combat` global comum → tipos globais → campos comuns do
loadout → tipos do loadout. Portanto, definir um intervalo comum no loadout
sobrescreve esse campo para todos os tipos herdados; uma exceção em `weapons`
pode então refiná-lo. Uma curva fornecida substitui a curva herdada inteira.

## Estado e validação

O prazo de reação e a memória de contato são transitórios; carregar a entidade
novamente começa uma reação nova ao adquirir o alvo. Parar/reiniciar apenas um
goal não apaga essa memória. Trocar de alvo não apaga um intervalo de tiro ainda
pendente. Um reload de configuração afeta a precisão imediatamente e os próximos
sorteios; os prazos já sorteados não são recalculados retroativamente.

Passaram 41 verificações isoladas de configuração, herança, reação e mira;
21 verificações no servidor com AK-47 e controlador nativo do TaCZ; e os testes
existentes de diálogos, ADM, percepção e carteira de viagens.
O cenário do servidor usa alvos imóveis e tempos fixos, sem mockar o disparo.
O balanceamento contra jogadores no modpack continua sendo ajustável.
Os testes efetivamente executados estão em `TESTES-COMBATE.txt` no pacote.
Para reproduzir os testes isolados com Java 21:

```
.\gradlew.bat -I validation/run-validation.gradle validateCombat validateDialogueMenu validateBehaviorConfig validateDialogueConfig validateAdmCompatibility validateTravelPure validateAwareness
```

O harness `validation/combat-server-validation.gradle` roda apenas em um diretório
de desenvolvimento descartável com EULA aceita e mundo plano. Ele prepara chunks,
altera a configuração de comportamento e encerra o servidor; não é uma ferramenta
para rodar apontando para um mundo de jogo. O resultado fica em
`run/combat-probe-results.txt`. O harness fica fora do JAR de distribuição.
