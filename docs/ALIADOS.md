# Relações e aliados

O formato aligned/opposed foi substituído por faction e relations. Veja REPUTACAO.md.
As relações agora têm direção: A gostar de B não obriga B a gostar de A.
Ajuda em combate consulta a opinião de quem ajuda. Compartilhamento e encontros offline
exigem aliança nos dois sentidos. Não existe propagação automática de amizade entre grupos.

```json
{
  "scout": { "faction": "villagers", "relations": { "villagers": 50, "medic": 50, "raiders": -100 } },
  "guard": { "faction": "villagers", "relations": { "villagers": 50, "raiders": -100 } },
  "medic": { "faction": "rescue", "relations": { "scout": 50 } },
  "raider": { "faction": "raiders", "relations": { "villagers": -100 } }
}
```

scout e guard consultam villagers=50 entre si. scout e medic são aliados mútuos,
mas guard e medic não herdam essa relação. Um valor específico por ID de loadout
prevalece sobre a relação com a facção do alvo, inclusive quando o valor é zero.
