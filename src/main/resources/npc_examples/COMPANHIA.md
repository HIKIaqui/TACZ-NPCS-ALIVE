# Companhia v0.4

Instale `dist/tacznpcs-2.1.0-hiki.companions.4.jar` no cliente e servidor, substituindo o mod anterior. Inclui todos os patches anteriores de comércio, pedidos, reputação, combate e correção C2ME. O sources.jar é para desenvolvimento.

## Recrutar

Use `I Want to Ask Something.` → `Want to travel with me?` → `Come with me.`.
O NPC exige reputação efetiva de aliado ou acima, usando o `alliedAt` existente (padrão 50). A soma continua sendo reputação pessoal + facção. Abaixo disso, ele recusa sem mostrar números na fala.

Há um líder por NPC, identificado por UUID e salvo no NBT da entidade. Pode ter vários NPCs no grupo; não foi adicionado um limite de recrutamento por jogador. Outros jogadores podem conversar e negociar normalmente quando o NPC estiver disponível, mas não podem assumir, comandar ou equipar o companheiro de alguém.

O recrutamento fecha a conversa. Ao falar de novo com seu companheiro, o menu principal ganha `Orders.`. Comércio, perguntas e demais opções continuam disponíveis.

## Ordens

- `Wait here.`: guarda a posição atual e entra em espera.
- `Follow me.`: substitui a opção anterior quando está esperando e retoma o acompanhamento.
- `Give Equipment.`: entrega uma unidade do item da mão secundária, ou da principal se a secundária estiver vazia e equipa no slot correspondente. O item que estava naquele slot é devolvido ao inventário ou à mão secundária disponível.
- `Stop following me.`: encerra a companhia e permite que o NPC retome sua rotina autônoma.

As ordens concluídas fecham o diálogo; reabrir atualiza as opções. A validação é feita no servidor no momento do clique, incluindo líder correto, sessão de conversa e disponibilidade normal do NPC.

## Troca de equipamento

Armaduras usam seus slots e escudos vão para a mão secundária; os demais itens aceitos pelo NPC usam o slot determinado pelo Minecraft, normalmente a mão principal. Equipamento de corpo de animais (`BODY`) não é aceito. Uma mão vazia não retira equipamento.

Uma ordem explícita pode substituir equipamento melhor por outro pior. A regra de melhoria da coleta automática não restringe essa troca. Ela não usa a carga do NPC como intermediária e não exige interesse no item entregue.

Se houver uma pilha na mão, só uma unidade é equipada; o restante permanece com o jogador. A peça antiga é devolvida com quantidade, componentes, nomes, encantamentos, durabilidade e dados TaCZ preservados. O espaço para devolução é verificado depois de remover a unidade entregue, aproveitando o slot que ela liberar. Se não houver espaço, a troca inteira é recusada sem consumir ou descartar itens.

Trocar a arma principal reinicializa a seleção/saque no controlador TaCZ. O NPC continua coletando equipamento normalmente e respeitando a ordem de combate escolhida.

## Movimento e persistência

O deslocamento usa a navegação normal do Minecraft. O acompanhamento respeita a distância escolhida em About Combat, também fora de combate: começa ao ultrapassar 12/32 blocos e termina ao chegar a até 5/12 blocos, respectivamente. O destino é recalculado a cada dez ticks, sem forçar chunks.

Esperar não desliga a IA de combate. O NPC pode reagir a inimigos ou fugir de um perigo e, quando a situação acalma, a navegação tenta retornar ao ponto de espera. Ordens não tornam o NPC invulnerável.

Conversas do líder pausam o combate. A companhia tem prioridade sobre descanso e passeio aleatório. O retorno prioritário ao líder distante também interrompe a perseguição e tem as prioridades detalhadas na atualização v0.2.

Enquanto estiver vinculado a um líder, tanto seguindo quanto esperando:

- O progresso automático das missões do NPC fica pausado; a missão ativa não é apagada.
- Viagens autônomas são bloqueadas, inclusive ao descarregar a entidade.
- A execução de ações offline fica pausada. Créditos existentes continuam guardados.
- Pedidos de suprimentos, inventário, relações e comércio mantêm suas regras normais.

Líder, modo e posição de espera sobrevivem ao descarregamento e ao salvamento em NBT. Se o líder sair, morrer, entrar em espectador ou estiver em outra dimensão, o NPC fica parado quando estiver carregado e pode retomar ao encontrar o líder vivo na mesma dimensão.

Não há teleporte de recuperação, carregamento forçado de chunks ou acompanhamento automático entre dimensões neste patch. Se o jogador se afastar a ponto de descarregar o NPC, ele permanece salvo no local e retoma quando voltar a receber ticks com o líder acessível. A navegação normal pode falhar diante de obstáculos; o NPC não atravessa blocos para alcançar o jogador.

A reputação de aliado é exigida para entrar no grupo. Uma pequena queda posterior não encerra automaticamente a companhia. Se o NPC ficar hostil ao líder ou passar a tê-lo como alvo de retaliação, o vínculo é encerrado na próxima checagem, em até vinte ticks. As regras de perda pessoal por acerto e de facção por morte continuam valendo.

Não há configuração nova obrigatória. O mínimo para aceitar recrutar acompanha o `alliedAt` global da reputação; não foi criado um mínimo separado por loadout nesta versão.

## Controle em combate — v0.2

O modo padrão dos seguidores passa a ser defensivo, inclusive para seguidores salvos antes desta atualização que ainda não tenham esse campo. Em Orders → About Combat…, `Attack enemies on sight.` libera a aquisição normal de inimigos hostis. No modo agressivo, essa opção é substituída por `Never attack first.`, que volta ao modo defensivo. A escolha é salva no NPC.

No modo defensivo, continuam autorizadas as entidades específicas que causaram dano real ao líder nos últimos 1200 ticks (60 segundos a 20 TPS). A partir da v0.4, o seguidor ocioso também ajuda contra a entidade que o líder ferir primeiro, conforme as regras abaixo. Dano de projétil usa seu atirador. Não basta outro mob ser do mesmo tipo ou da mesma facção do agressor. Alvos aliados do próprio NPC continuam protegidos. O histórico é temporário, reinicia ao sair do servidor e não é salvo em NBT. Os demais NPCs mantêm suas regras normais de hostilidade.

A restrição também vale para aquisição por retaliação, avisos de aliados e atribuições diretas de alvo, sem depender apenas da busca por proximidade. No modo defensivo, um golpe só contra o seguidor não autoriza atacar uma entidade que ainda não tenha ferido o líder; a fuga de perigo continua disponível. Se o próprio líder atacar abaixo de aliado e provocar retaliação, o NPC encerra a companhia antes de revidar, respeitando as regras anteriores.

O líder pode abrir a conversa do seu companheiro mesmo com alerta ou alvo de combate. A abertura limpa o alvo, interrompe a mira/recarga e segura a aquisição de novos alvos enquanto o diálogo ou a loja permanecer aberto. O estado de alerta e o prazo normal de conversa não fecham esse diálogo. Dano real ao NPC fecha a conversa imediatamente. Fechar manualmente, afastar-se, perder linha de visão, morrer ou desconectar continuam encerrando a sessão pelas verificações normais. A exceção não vale para NPCs de outros jogadores ou NPCs sem vínculo.

Seguindo o líder, ultrapassar a distância escolhida ativa um retorno prioritário: abandona o alvo e bloqueia readquirir inimigos até alcançar a distância de parada do modo atual. Essa margem evita alternar sem parar entre perseguir e retornar. O retorno tem prioridade sobre perseguir/atirar e recarregar andando; emergência de fuga e flutuação continuam tendo suas prioridades. Durante Wait here, não há retorno ao líder: a referência continua sendo a posição guardada.

## Mãos e armas — v0.2

`Give Equipment` usa primeiro o item da offhand. Só usa a mão principal se a offhand estiver vazia. O plano de devolução agora considera também o espaço disponível na mão secundária: se ela for liberada pela entrega e a mochila estiver cheia, pode receber o equipamento anterior. Pilhas continuam entregando uma única unidade e a troca continua sendo integral.

O TaCZ 1.1.8-hotfix-r7 cancela o clique antes da interação normal do Minecraft. Esta versão libera o clique direito no cliente somente quando o jogador aponta para seu próprio companheiro, usando o UUID do líder sincronizado na entidade. Não altera o disparo pelo botão esquerdo nem a interação com outros alvos. Segurar Shift mantém o comportamento normal do clique. Também são tratados os dois caminhos de interação com entidade, incluindo a mão secundária.

O protocolo de rede passa para 4 devido ao novo dado sincronizado de liderança. Instale o JAR atualizado nos dois lados. O teste do caminho do cliente foi feito por compilação e contrato binário com o TaCZ; o clique físico/renderização no cliente e a combinação completa do modpack ainda precisam de teste no jogo.

## Distância e submenu — v0.3

Em `Orders.` → `About Combat…` ficam as duas preferências de combate: quando atacar e a distância de acompanhamento.

| Modo | Começa a acompanhar ao ultrapassar | Termina o retorno a até |
| --- | --- | --- |
| Próximo (padrão) | 12 blocos | 5 blocos |
| Distante | 32 blocos | 12 blocos |

No modo próximo aparece `Keep your distance.`. Depois de escolhê-la, aparece `Stay close to me.` para voltar. A opção de ataque continua alternando entre as duas ordens anteriores. `Back.` retorna às ordens gerais.

As distâncias usam a posição real em três dimensões. Dentro do raio inicial, o seguidor pode manter sua posição ou combate. Depois de começar o retorno, continua até o raio menor, evitando alternância constante. O modo distante não faz o NPC fugir se o jogador se aproximar.

A preferência é individual, salva no NBT e preservada ao esperar/retomar e descarregar/recarregar a entidade. Seguidores existentes sem a nova preferência usam o modo próximo. A opção de distância não altera a escolha defensivo/agressivo. `Wait here` mantém sua posição de espera, independentemente desta preferência.

O patch é cumulativo e não exige apagar configurações ou recrutar novamente. O protocolo continua 4; substitua o JAR anterior no cliente e no servidor para manter a mesma versão.

## Ajuda ofensiva e textos — v0.4

Opções curtas em Orders → About Combat…:

- `Never attack first.`: ativa o modo defensivo.
- `Attack enemies on sight.`: restaura a aquisição normal de inimigos.
- `Stay close to me.`: ativa o acompanhamento próximo, 12 → 5 blocos.
- `Keep your distance.`: ativa o acompanhamento distante, 32 → 12 blocos.

No modo defensivo, dano real causado pelo líder a uma entidade viva também pode dar alvo ao seguidor. Projéteis usam o jogador creditado como causador do dano.

A ajuda só é atribuída se o seguidor estiver sem alvo no instante do acerto. Se já estiver em combate, mantém o alvo atual e não registra a segunda vítima como pedido de ajuda. Portanto, acertar vários inimigos não provoca alternância nem cria uma fila posterior.

Cada seguidor recebe sua própria autorização temporária de apoio para a entidade escolhida, por até 1200 ticks (60 segundos a 20 TPS). Essa memória não é salva em NBT, é descartada ao encerrar a companhia e é limpa no logout ou ao parar o servidor. A memória anterior de inimigos que feriram o líder continua funcionando.

A busca verifica apenas seguidores carregados a até 64 blocos do líder; cada um precisa enxergar a vítima e tê-la dentro de seu alcance atual de detecção. Não há carregamento de chunks para ajudar. Aliados do NPC e companheiros do próprio líder não recebem essa ordem de ataque. Conversa aberta e retorno prioritário ao líder bloqueiam a ajuda. Wait here mantém a posição de referência, mas continua permitindo combate pelas regras existentes.

O modo agressivo mantém sua lógica anterior. As preferências salvas e os IDs das ações não mudaram; não precisa recrutar novamente.
