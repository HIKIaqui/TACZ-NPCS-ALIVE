# TacZ: NPCs — projeto reconstruído

Fontes reconstruídos do TacZ: NPCs 2.1.0 para Minecraft 1.21.1 NeoForge.
Autor original: Corbin Smith. Detalhes e limitações em docs/ESTADO-DA-RECONSTRUCAO.md.

**Status: build concluído com sucesso; Minecraft ainda não executado para validação.**
Um JAR já recompilado está em dist/.

## Abrir no IntelliJ IDEA (Windows)
1. Extraia este projeto para uma pasta própria, fora da instância do Minecraft.
2. Instale/configure JDK 21 (por exemplo, Eclipse Temurin 21).
3. No IntelliJ, use Open e escolha a pasta que contém build.gradle e settings.gradle.
4. Importe como projeto Gradle; use o Gradle Wrapper incluído.
5. Configure Project SDK = 21 e Gradle JVM = 21.
6. Aguarde a sincronização. O primeiro carregamento baixa Minecraft, NeoForge e dependências.

## Comandos no terminal da pasta do projeto
```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```
No Linux/macOS, use ./gradlew em vez de .\gradlew.bat.
O JAR instalável fica em build/libs/tacznpcs-2.1.0-reconstructed.1.jar.
O arquivo com sufixo -sources.jar contém fontes, não é o mod para jogar.

## Dependências do seu modpack
O Gradle baixa uma versão padrão do TaCZ. Para trabalhar com a versão exata da sua instância,
coloque somente o JAR do TaCZ (tacz-*.jar) na pasta libs/ e sincronize o Gradle novamente.
Não coloque todos os mods do modpack em libs/. Dependências adicionais de execução vão em run/mods/.
Player Animator já é baixado pelo Gradle.

## Testes
runClient usa a pasta run/ separada da sua instância. Comece num mundo descartável.
Para testar o JAR no modpack, remova o JAR original de TacZ: NPCs dessa cópia da instância antes de adicionar o reconstruído.
Não carregue os dois ao mesmo tempo: ambos usam o ID tacznpcs.

## Onde começar a ler
TNPC.java → data/NPCTemplate.java → entity/AutoGunOperator.java → entity/goal/FireAtNearestTargetGoal.java.
Consulte docs/ESTADO-DA-RECONSTRUCAO.md antes de interpretar recompilação como equivalência de comportamento.

## Referências do ambiente
- MDK oficial: https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle
- Documentação NeoForge 1.21.1: https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- ModDevGradle: https://github.com/neoforged/ModDevGradle
- TaCZ de desenvolvimento: https://modrinth.com/mod/tacz-1.21.1/version/1.1.8-hotfix-r7
- Player Animator: https://maven.kosmx.dev/dev/kosmx/player-anim/player-animation-lib-forge/2.0.1+1.21.1/
