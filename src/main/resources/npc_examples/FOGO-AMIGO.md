# Fogo amigo v0.1

Configuração em `config/tacznpc_behavior.json` → `combat.friendlyFire`. Cada loadout também aceita `loadouts.<id>.combat.friendlyFire`, herdando individualmente os campos omitidos.

```json
{
  "friendlyFire": {
    "shotCheck": {
      "enabled": true,
      "corridorRadius": 0.35,
      "retryTicks": 4
    },
    "damageReduction": {
      "enabled": true,
      "allies": {
        "enabled": true,
        "damagePercent": 50
      },
      "nearbyAllies": {
        "enabled": true,
        "withinBlocks": 6,
        "damagePercent": 25
      },
      "companionGroup": {
        "enabled": true,
        "damagePercent": 10
      }
    }
  }
}
```

## Evitar o disparo

`shotCheck.enabled` liga/desliga a checagem antes da tentativa de disparo. Usa uma consulta ao índice de entidades carregadas na região do tiro, seguida da interseção exata dos corpos dos aliados com o corredor. Não faz uma busca permanente enquanto o NPC está parado ou fora de combate.

São consideradas a linha dos olhos do atirador até os olhos do alvo e a direção atual do tiro com o erro angular da IA, até a distância do alvo. `corridorRadius` é a margem em blocos ao redor dos corpos, padrão 0,35, configurável de 0 a 2.

Um aliado bloqueando essas linhas segura o disparo. O NPC não consome tiro da rajada nem executa o disparo TaCZ nessa tentativa. Aguarda `retryTicks` antes de verificar novamente, padrão 4 ticks (0,2 segundo a 20 TPS), configurável de 1 a 200 ticks inteiros.

Um aliado ao lado, acima ou atrás do trecho do tiro não bloqueia só por estar perto. O corredor não representa todo o cone de spread nativo nem impede alguém de entrar no caminho depois que a bala já foi disparada. A proteção de dano cobre acidentes que ainda acontecerem.

Desativar shotCheck também ignora uma espera de segurança já pendente. Não é adicionado movimento de flanqueamento neste patch.

## Reduzir o dano

`damageReduction.enabled` liga/desliga todas as reduções abaixo, sem desligar a checagem do disparo.

| Regra | Campo | Dano padrão |
| --- | --- | --- |
| Aliado em geral | allies.damagePercent | 50% |
| Aliado a até 6 blocos | nearbyAllies.damagePercent | 25% |
| Líder ou seguidor do mesmo líder | companionGroup.damagePercent | 10% |

Cada regra tem seu próprio `enabled`. `nearbyAllies.withinBlocks` ajusta a distância (0 a 64 blocos). Percentuais vão de 0 a 100; 0 permite dano zero e 100 mantém o dano.

Vale a menor porcentagem entre as regras habilitadas e aplicáveis. Elas não se multiplicam: um colega do grupo a 3 blocos recebe 10%, e não 50% × 25% × 10%.

Desligar a regra do grupo permite que as outras ainda se apliquem. Desligar as três regras ou o controle geral mantém o dano integral.

As reduções usam as configurações do NPC atirador. Aplicam-se a dano de bala TaCZ (incluindo perfurante) ou projétil cinético TaCZ atribuído ao nosso NPC. A redução acontece antes da armadura e das demais mitigações normais. Não alteram tiros atribuídos ao jogador, ataques corpo a corpo nem dano genérico sem essa origem.

Aliados são reconhecidos pelas relações e reputação existentes. O líder do seguidor e os NPCs com o mesmo líder também são tratados como grupo protegido, mesmo sem relação de facção aliada. Companheiros do mesmo grupo não podem se tornar alvo intencional de combate enquanto o vínculo estiver ativo; a regra de encerramento da companhia por traição continua valendo.

## Compatibilidade e instalação

Padrões são aplicados também a arquivos antigos sem friendlyFire, sem reescrever a configuração. Use `/tacznpcs_reload` para aplicar alterações. Configuração inválida mantém o snapshot anterior.

O exemplo parcial incluído deve ser mesclado ao seu behavior, não usado para substituir o arquivo inteiro. Troque `example_loadout` pelo ID real do loadout que quiser personalizar.

Substitua o JAR anterior por `dist/tacznpcs-2.1.0-hiki.friendlyfire.1.jar` no cliente e servidor. Patch cumulativo sobre Companhia v0.4, incluindo os modificadores de armas, minigun, ordens, distância, offhand, pedidos, comércio, reputação e C2ME. Não precisa recrutar novamente ou apagar configurações.

As consultas só ocorrem nas tentativas de tiro habilitadas, com espera entre tentativas bloqueadas. Isso reduz trabalho desnecessário, mas não é um benchmark: o custo com grandes grupos e o modpack completo ainda precisa ser medido no jogo.
