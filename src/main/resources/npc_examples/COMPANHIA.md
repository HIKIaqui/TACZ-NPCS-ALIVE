# Companhia v0.1

Instale `dist/tacznpcs-2.1.0-hiki.companions.1.jar` no cliente e servidor, substituindo o mod anterior. Inclui todos os patches anteriores de comércio, pedidos, reputação, combate e correção C2ME. O sources.jar é para desenvolvimento.

## Recrutar

Use `I Want to Ask Something.` → `Want to travel with me?` → `Come with me.`.
O NPC exige reputação efetiva de aliado ou acima, usando o `alliedAt` existente (padrão 50). A soma continua sendo reputação pessoal + facção. Abaixo disso, ele recusa sem mostrar números na fala.

Há um líder por NPC, identificado por UUID e salvo no NBT da entidade. Pode ter vários NPCs no grupo; não foi adicionado um limite de recrutamento por jogador. Outros jogadores podem conversar e negociar normalmente quando o NPC estiver disponível, mas não podem assumir, comandar ou equipar o companheiro de alguém.

O recrutamento fecha a conversa. Ao falar de novo com seu companheiro, o menu principal ganha `Orders.`. Comércio, perguntas e demais opções continuam disponíveis.

## Ordens

- `Wait here.`: guarda a posição atual e entra em espera.
- `Follow me.`: substitui a opção anterior quando está esperando e retoma o acompanhamento.
- `Give Equipment.`: entrega uma unidade do item na mão principal do jogador e equipa no slot correspondente. O item que estava naquele slot é devolvido ao inventário do jogador.
- `Stop following me.`: encerra a companhia e permite que o NPC retome sua rotina autônoma.

As ordens concluídas fecham o diálogo; reabrir atualiza as opções. A validação é feita no servidor no momento do clique, incluindo líder correto, sessão de conversa e disponibilidade normal do NPC.

## Troca de equipamento

Armaduras usam seus slots e escudos vão para a mão secundária; os demais itens aceitos pelo NPC usam o slot determinado pelo Minecraft, normalmente a mão principal. Equipamento de corpo de animais (`BODY`) não é aceito. Uma mão vazia não retira equipamento.

Uma ordem explícita pode substituir equipamento melhor por outro pior. A regra de melhoria da coleta automática não restringe essa troca. Ela não usa a carga do NPC como intermediária e não exige interesse no item entregue.

Se houver uma pilha na mão, só uma unidade é equipada; o restante permanece com o jogador. A peça antiga é devolvida com quantidade, componentes, nomes, encantamentos, durabilidade e dados TaCZ preservados. O espaço para devolução é verificado depois de remover a unidade entregue, aproveitando o slot que ela liberar. Se não houver espaço, a troca inteira é recusada sem consumir ou descartar itens.

Trocar a arma principal reinicializa a seleção/saque da arma no controlador TaCZ. O NPC continua com seu comportamento normal de equipamento, coleta e combate após a ordem.

## Movimento e persistência

O deslocamento usa a navegação normal do Minecraft. Fora de combate, ele anda até ficar perto do líder, corre quando está mais distante e para aproximadamente a três blocos. O destino é recalculado a cada dez ticks, sem forçar chunks.

Esperar não desliga a IA de combate. O NPC pode reagir a inimigos ou fugir de um perigo e, quando a situação acalma, a navegação tenta retornar ao ponto de espera. Ordens não tornam o NPC invulnerável.

Conversas têm prioridade sobre seguir. Combate, fuga e recarga mantêm suas prioridades existentes. A companhia tem prioridade sobre descanso e passeio aleatório, evitando que o NPC abandone a ordem para ir descansar.

Enquanto estiver vinculado a um líder, tanto seguindo quanto esperando:

- O progresso automático das missões do NPC fica pausado; a missão ativa não é apagada.
- Viagens autônomas são bloqueadas, inclusive ao descarregar a entidade.
- A execução de ações offline fica pausada. Créditos existentes continuam guardados.
- Pedidos de suprimentos, inventário, relações e comércio mantêm suas regras normais.

Líder, modo e posição de espera sobrevivem ao descarregamento e ao salvamento em NBT. Se o líder sair, morrer, entrar em espectador ou estiver em outra dimensão, o NPC fica parado quando estiver carregado e pode retomar ao encontrar o líder vivo na mesma dimensão.

Não há teleporte de recuperação, carregamento forçado de chunks ou acompanhamento automático entre dimensões neste patch. Se o jogador se afastar a ponto de descarregar o NPC, ele permanece salvo no local e retoma quando voltar a receber ticks com o líder acessível. A navegação normal pode falhar diante de obstáculos; o NPC não atravessa blocos para alcançar o jogador.

A reputação de aliado é exigida para entrar no grupo. Uma pequena queda posterior não encerra automaticamente a companhia. Se o NPC ficar hostil ao líder ou passar a tê-lo como alvo de retaliação, o vínculo é encerrado na próxima checagem, em até vinte ticks. As regras de perda pessoal por acerto e de facção por morte continuam valendo.

Não há configuração nova obrigatória. O mínimo para aceitar recrutar acompanha o `alliedAt` global da reputação; não foi criado um mínimo separado por loadout nesta versão.
