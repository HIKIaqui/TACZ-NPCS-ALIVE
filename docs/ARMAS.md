# Modificadores de armas v0.1

Acrescente `weaponModifiers` dentro de `combat` em `config/tacznpc_behavior.json`. Também é aceito em `loadouts.<loadout>.combat.weaponModifiers`. A chave é o **GunId TaCZ**, por exemplo `tacz:minigun`, e não o item genérico `tacz:modern_kinetic_gun`.

## Exemplo

```json
{
  "combat": {
    "weaponModifiers": {
      "tacz:minigun": {
        "aimDistancePercent": 100,
        "baseAimErrorDegreesPercent": 100,
        "accuracyByDistancePercent": 100,
        "shotIntervalTicksPercent": 25,
        "burstShotsPercent": 500,
        "burstPauseTicksPercent": 100
      }
    }
  },
  "loadouts": {
    "military": {
      "combat": {
        "weaponModifiers": {
          "tacz:minigun": {
            "burstShotsPercent": 700
          }
        }
      }
    }
  }
}
```

Este é um exemplo parcial para mesclar ao arquivo atual. Não substitua seu behavior inteiro por ele, pois isso removeria suas outras personalizações. O loadout `military` é ilustrativo: use o ID real do seu loadout.

## Como os percentuais funcionam

100 é 100% do valor original (x1); 500 é 500% (x5), não +500%; 25 é 25% (x0,25). Valores decimais são aceitos. Intervalo permitido: 0 a 10000%.

| Campo | Efeito |
| --- | --- |
| aimDistancePercent | Multiplica a distância em que o NPC passa a mirar/usar ADS. Não altera alcance, percepção ou distância para seguir. |
| baseAimErrorDegreesPercent | Multiplica o erro angular base da mira. Menor significa mais precisão. |
| accuracyByDistancePercent | Multiplica o spread da curva já configurada. Preserva os pontos de distância e a interpolação. Menor significa mais precisão. |
| shotIntervalTicksPercent | Multiplica a espera extra da IA entre tiros bem-sucedidos. |
| burstShotsPercent | Multiplica a quantidade sorteada de tiros na rajada. |
| burstPauseTicksPercent | Multiplica a pausa sorteada entre rajadas. |

Primeiro são resolvidos os valores globais, de categoria (mg, rifle etc.) e de loadout pelas regras existentes. Para contagens/tempos, o valor real é sorteado e só então multiplicado. Por exemplo, uma rajada sorteada de 3 tiros com 500% vira 15. Se os valores possíveis eram 1, 2 ou 3, passam a 5, 10 ou 15, preservando o sorteio original.

Contagens e tempos arredondam para o inteiro mais próximo; metade arredonda para cima. Rajadas mantêm no mínimo um tiro; pausas e intervalos podem chegar a zero. Zero não desativa o disparo. Não se aplica o antigo máximo do valor-base ao resultado multiplicado.

Precisão por nível continua participando normalmente. Os dois percentuais de precisão se multiplicam entre si e com o erro existente, sem alterar o spread nativo da arma ou a progressão de spread do nível. O erro angular final da IA continua limitado a 45 graus.

O percentual de um loadout **substitui o percentual global daquele campo**, sem aplicar duas vezes. Campos omitidos herdam o global. Armas sem entrada usam 100% em tudo. Uma entrada vazia herda os valores anteriores; para neutralizar uma personalização, defina explicitamente os campos desejados como 100.

## Padrão da minigun

`tacz:minigun` vem com burstShotsPercent=500 e shotIntervalTicksPercent=25. Todos os demais campos ficam em 100%. Com o padrão mg atual, isso produz rajadas de 5/10/15 tiros e intervalo extra de 1 tick. Outras metralhadoras não recebem essa alteração.

Esse padrão também vale em configurações antigas sem weaponModifiers, sem reescrever seu arquivo. Para desativá-lo, configure os dois campos como 100.

O intervalo é uma espera adicional da IA. A cadência, munição, recarga, aquecimento e demais restrições do TaCZ continuam valendo. Mesmo com 0% de intervalo, esta IA não dispara mais de uma vez por tick. Valores menores não prometem ultrapassar esses limites.

## Aplicação e instalação

Use `/tacznpcs_reload` para recarregar. Uma entrada inválida rejeita o carregamento de behavior e mantém seu snapshot anterior. Mudanças de tempo/contagem valem nos próximos sorteios; uma rajada já iniciada não é redimensionada no meio. Trocar de arma usa o GunId atual.

Instale `dist/tacznpcs-2.1.0-hiki.weapons.1.jar` no cliente e servidor, substituindo o anterior. Patch cumulativo sobre Companhia v0.3: inclui distância próxima/distante, submenu About Combat, offhand, reputação, pedidos, comércio e C2ME. Não precisa apagar configurações ou recrutar seguidores novamente. O sources.jar é para desenvolvimento.
