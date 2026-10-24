# Aliados: encontros offline e reação a dano

`meetAllies` pode trazer qualquer loadout aliado cadastrado em `config/tacznpc_loadouts.json`, inclusive o próprio. Cada NPC criado tem seu loadout completo: equipamento, interesses, aparência e comportamento correspondentes. O sorteio é uniforme entre os loadouts elegíveis, para cada NPC do encontro. Quantidade (1–2), custo, espaço seguro e limite de população continuam seguindo as regras anteriores.

## Configurar

Dois loadouts com uma entrada em comum em `aligned` são aliados. Também é possível colocar o ID exato de outro loadout em `aligned`, para uma aliança direta entre facções diferentes. Uma declaração basta para os dois se reconhecerem. As entradas continuam aceitando as facções e categorias usadas antes.

Exemplo parcial para acrescentar aos loadouts existentes (mantenha os demais campos):

```json
{
  "scout": { "aligned": ["villagers", "medic"], "opposed": ["raiders"] },
  "guard": { "aligned": ["villagers"], "opposed": ["raiders"] },
  "medic": { "aligned": ["rescue"], "opposed": [] },
  "raider": { "aligned": ["raiders"], "opposed": ["villagers"] }
}
```

`scout` e `guard` compartilham a facção `villagers`; `scout` e `medic` têm uma aliança explícita. `guard` e `medic` não se tornam aliados por consequência dessa relação. A oposição configurada por qualquer lado tem prioridade, tanto por ID de loadout quanto por uma afiliação em `aligned`. IDs diferenciam maiúsculas e minúsculas. Para evitar ambiguidades, use nomes distintos para facções e loadouts novos.

Não é preciso adicionar loadouts ao arquivo de facções: ele continua sendo a lista de tipos de entidades vanilla/modded associados às facções. Os loadouts disponíveis para encontros vêm de `tacznpc_loadouts.json`.

## Fuga ao ver um aliado ferido

A reação a dano/morte próximo usa a mesma regra de aliança. Um NPC calmo e sem alvo pode fugir ao ver o incidente com um aliado de outro loadout ou facção. Aliados vanilla configurados continuam reconhecidos. NPCs neutros ou inimigos não disparam essa reação por aliança.

Continuam valendo `damageEscape.enabled`, `damageEscape.reactToAllies`, `perception.allyAlertRadius` e as condições existentes de alerta/combate. Não há carregamento de chunks para procurar aliados.

Use `/tacznpcs_reload` após editar. Relações dos NPCs já existentes passam a consultar a configuração atualizada; equipamentos existentes não são recriados.

## Teste no jogo

1. Configure loadouts de teste como acima. Para testar encontros isoladamente, deixe somente `meetAllies` habilitado no perfil offline do NPC de teste e confira o limite de população.
2. Crie um `scout` e conceda `/tacznpcs_offline add 5`. Quando ele estiver calmo, o encontro poderá gerar `scout`, `guard` ou `medic`; o sorteio pode repetir o mesmo loadout.
3. Para testar a fuga, mantenha um `scout` calmo próximo de um `medic`, sem inimigos ou alvos de combate, com reação a aliados habilitada. Cause dano genérico no `medic` e observe a fuga. Repita com um NPC neutro para comparar. Teste oposição em uma área separada, pois inimigos podem entrar em combate antes do dano.

As configurações de loot por bioma, inventário e comércio permanecem compatíveis. Esta atualização não implementa missões.
