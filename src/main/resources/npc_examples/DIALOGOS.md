# Diálogos — v0.1

Este patch inclui as viagens da prévia anterior e acrescenta o menu de perguntas
às conversas dos NPCs. O comércio continua usando estoque finito e a integração
existente com ADM 0.8.5. Não há alteração nas missões autônomas nem na carteira.

## Instalar

Substitua o JAR anterior de TACZ NPCs pelo JAR principal de `dist/`, sem instalar
o arquivo `-sources.jar`. Mantenha as configurações existentes. No primeiro início
é criado `config/tacznpc_dialogue_replies.json`. Não é necessário substituir os
perfis de diálogo antigos: o novo menu é montado ao abrir a conversa.

## Menu

Principal:

1. `Let's Trade.`
2. `I Want to Ask Something.`
3. `Goodbye.`

Perguntas:

1. `Need help with something?`
2. `What are you doing here?`
3. `Any news around here?`
4. `Want to travel with me?`
5. `Back.`

O comércio aparece apenas se o loadout puder negociar, como antes. `Back` nas
respostas retorna às perguntas; `Back` nas perguntas retorna ao principal.
`Goodbye` encerra a conversa. Conversas continuam sendo interrompidas por combate,
dano, distância, prazo ou fechamento da tela; durante a conversa, o NPC não parte
em viagem offline.

As opções de ajuda e companhia têm apenas uma resposta configurável nesta versão.
Não há pedidos de itens, reputação, consumo de itens ou acompanhamento. Por padrão,
o NPC diz que não precisa de suprimentos e que está viajando sozinho.

## Falas por missão e loadout

Para `activity` e `news`, cada assunto usa sua própria prioridade:

1. Exceção do loadout na missão ativa (`dialogues.loadouts`).
2. Fala geral da missão ativa (`dialogues.default`).
3. Fala genérica do loadout em `tacznpc_dialogue_replies.json`, herdando `defaults`.
4. Texto genérico antigo do perfil selecionado, quando disponível, ou fallback do mod.

Uma exceção que define apenas `activity` continua herdando `news` da missão.
Missões sem falas não impedem os fallbacks. O formato de missões não mudou.

O arquivo novo aceita uma string ou lista de alternativas por assunto:

```json
{
  "defaults": {
    "activity": [],
    "news": ["Nothing new. Keep your eyes open."],
    "help": ["Nothing I need right now."],
    "follow": ["I'm staying here for now."]
  },
  "loadouts": {
    "villagers": {
      "activity": ["I'm gathering supplies.", "Looking for a safer place."],
      "news": ["Someone saw movement near the old road."]
    }
  }
}
```

Use `/tacznpcs_reload` após editar. Chaves desconhecidas, listas inválidas e falas
vazias são rejeitadas; a configuração ativa anterior é preservada. `[]` significa
herdar, não silenciar o assunto. Cada alternativa pode ter até 2.048 caracteres.

As falas são sorteadas ao abrir a conversa e ficam estáveis naquela sessão. Uma
nova conversa consulta novamente a missão ativa. A configuração de um NPC não é
escrita no perfil compartilhado nem passa para a conversa de outro NPC.

## Perfis personalizados

As regras de seleção de perfil, prioridade e `genericMode` existentes continuam
ativas. O cumprimento do perfil é preservado. Tópicos extras do menu antigo ficam
em `Other questions.`, antes de `Back` no submenu; o menu padrão não tem esse item
extra. Condições e ações desses tópicos continuam sendo avaliadas pelo ADM.

As antigas opções genéricas simples de atividade e notícias viram fallbacks das
novas perguntas. Tópicos que tenham condições ou ações próprias são preservados
como extras; seus textos condicionais não são usados como fallback público.

O mod registra uma definição privada enquanto a conversa está aberta e a remove
ao fechar. Seu ID é estável por NPC, para não criar uma chave de histórico ADM
nova a cada abertura. Referências de histórico totalmente qualificadas pelo ID
antigo de um perfil podem precisar de adaptação em perfis avançados; flags e IDs
explícitos de escolhas continuam sendo processados pelo ADM.

## Compilar e validar

Java 21, na raiz do projeto:

```
.\gradlew.bat clean build
.\gradlew.bat -I validation/run-validation.gradle validateDialogueMenu validateDialogueConfig validateAdmCompatibility validateTravelPure
```

Passaram 51 verificações de menu, prioridade e configuração; 34 verificações em
servidor dedicado usando ADM 0.8.5; os testes de configuração antiga,
compatibilidade ADM, percepção e carteira de viagens também passaram.
Consulte `TESTES-DIALOGOS.txt` no pacote para os resultados efetivamente executados.
O teste de servidor usa jogadores artificiais e chama as APIs reais de abertura,
escolha e avanço do ADM; ele não simula cliques ou renderiza a janela do cliente.
A compilação local reutilizou os artefatos exatos já existentes de Minecraft
1.21.1/NeoForge 21.1.262, pois o NeoForm deste ambiente falhou ao consultar o
comando do próprio processo. O código Java do patch foi recompilado contra esses
artefatos e as dependências reais.
O código do harness de servidor fica em `validation/server` e não entra no JAR
normal. A validação gráfica da janela ADM deve ser feita no cliente do modpack.
