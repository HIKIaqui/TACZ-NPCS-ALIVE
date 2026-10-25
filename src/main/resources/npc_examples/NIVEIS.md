# Experiência, precisão e nível inicial

Versão: 2.1.0-hiki.levels.1. Inclui combate, diálogos e viagens dos patches anteriores.

O nível continua sendo `XP total / xpPerLevel` (divisão inteira). Ganhar XP melhora vida,
mira e dispersão para todos os loadouts. Não existe vantagem de precisão exclusiva do militar:
um survivor e um militar no mesmo nível usam os mesmos multiplicadores de experiência.
As diferenças já configuradas em `tacznpc_behavior.json` continuam valendo como base.

## Valores iniciais

| Nível | Erro de mira | Spread da arma |
|---|---:|---:|
| 0 | ×2,00 | ×1,50 |
| 1 | ×1,33 | ×1,20 |
| 2 | ×1,00 | ×1,00 |
| 3 | ×0,80 | ×0,86 |
| 5 | ×0,57 | ×0,67 |
| 10 | ×0,35 | ×0,60 |

Menor multiplicador significa maior precisão. O nível 2 corresponde à base do patch
anterior. O nível 0 agora erra mais. Os limites evitam precisão perfeita só por acumular XP.
Esses valores são um ponto de partida para testar no modpack, não uma taxa de acerto medida.

O erro de mira multiplica `baseAimErrorDegrees × accuracyByDistance`, mantendo a curva
de distância do loadout. O resultado angular tem teto de 45 graus.
O spread multiplica as propriedades nativas TaCZ `INACCURACY` e `AIM_INACCURACY` durante
o disparo do NPC. A arma mantém os próprios valores e acessórios; as propriedades
originais são restauradas ao final, inclusive se ocorrer uma exceção. A arma dropada
não ganha bônus permanente. Jogadores não recebem esse multiplicador.
Scripts de armas que substituem por completo o cálculo nativo de dispersão podem ter
comportamento próprio e precisam ser testados com os respectivos gunpacks.
Reflexos, cadência e quantidade de tiros por rajada continuam como no patch anterior.

## Configuração

Edite `config/tacznpc_quests.json`, preservando suas outras opções:

```json
{
  "enabled": true,
  "checkIntervalTicks": 100,
  "assignmentRetryTicks": 200,
  "progression": {
    "xpPerLevel": 100,
    "healthPerLevel": 1,
    "maxHealth": 30,
    "aimError": {
      "levelZeroMultiplier": 2.0,
      "reductionPerLevel": 0.5,
      "minimumMultiplier": 0.35
    },
    "weaponSpread": {
      "levelZeroMultiplier": 1.5,
      "reductionPerLevel": 0.25,
      "minimumMultiplier": 0.6
    }
  },
  "loadouts": {
    "military": { "minSpawnLevel": 2 },
    "survivors": { "minSpawnLevel": 0 }
  }
}
```

Fórmula para cada multiplicador:
`max(minimumMultiplier, levelZeroMultiplier / (1 + nível × reductionPerLevel))`.
`reductionPerLevel: 0` mantém o multiplicador constante.
Multiplicadores entre 0 e 4; redução entre 0 e 10; mínimo não pode superar o valor inicial.
O nível mínimo aceita inteiros de 0 a 1.000.000.

A configuração antiga continua funcionando. Campos ausentes usam os padrões acima.
`military` tem mínimo 2 por padrão, inclusive em configurações antigas; outros IDs
nascem no nível 0, salvo configuração explícita. Para desativar o mínimo dos militares,
configure `"military": { "minSpawnLevel": 0 }`. Use o ID exato do loadout;
no dump enviado, os IDs são `military` e `survivors`.

O mínimo concede XP total suficiente para o nível (200 XP para nível 2 com os padrões).
Ele não soma XP novamente a NPCs que já possuem XP suficiente. A inicialização ocorre
na criação do template, inclusive spawns normais e aliados criados por ações offline.
NPCs já existentes preservam seu XP; reload, viagem e restauração NBT não aplicam o mínimo
novamente. Aumentar o mínimo afeta os próximos spawns.
A vida segue `healthPerLevel` e `maxHealth`: com base de 20, o nível 2 tem 22 de vida.
As regras de vida existentes continuam preservando ferimentos ao recarregar entidades.

Use `/tacznpcs_reload` após editar. Um JSON inválido mantém a última configuração válida
de missões/níveis. Cada arquivo de configuração do mod tem seu próprio resultado de reload.
O XP de entidades existentes não é convertido ao alterar `xpPerLevel`; como antes,
isso recalcula seus níveis a partir do XP salvo.

## Conferir no jogo

- Consulte `/tacznpcs_quest status @e[type=tacznpcs:npc,distance=..32]` para XP, nível,
  vida e multiplicadores atuais. Confira o ID da entidade na sugestão do comando se necessário.
- Crie novos NPCs usando seus spawns/loadouts habituais: military deve nascer no nível 2.
- Ganhe/conceda XP a um survivor e compare no mesmo nível, arma, distância e configuração.
- `/tacznpcs_quest xp <seletor> 100` concede 100 XP. O comando soma XP; não define nível.
- Reinicie com um NPC ferido e confira que XP e ferimento foram preservados.
- Ajuste os multiplicadores com os gunpacks reais depois de testar perto e longe.
