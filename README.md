# PokeWalk Lite

Aplicativo Android pessoal para registrar atividades de caminhada no Health Connect.

Versão atual: **v1.0.0** (`versionCode 10000`).

## Destaques da v1.0.0

- interface redesenhada, mais limpa e organizada;
- seleção de velocidade entre 1 e 8 km/h;
- seleção de distância entre 1 e 20 km;
- acompanhamento de tempo, distância, passos e progresso;
- cancelamento com salvamento do progresso já percorrido;
- notificações durante e ao concluir a atividade;
- histórico e controles de diagnóstico removidos;
- versão fixada no rodapé;
- novo ícone com uma pessoa caminhando lateralmente sobre uma Pokébola.

O gravador do Health Connect mantém o comportamento validado na v0.4.9: somente `DistanceRecord` e `StepsRecord`, escritos em blocos de um minuto com `Metadata.activelyRecorded(Device.TYPE_PHONE)`.

## Requisitos

- Android 9 ou mais recente;
- Health Connect disponível e atualizado;
- permissões para gravar passos e distância, reconhecer atividade e exibir notificações.

## Compilar

O workflow do GitHub Actions gera um APK release sem assinatura. Também é possível compilar localmente com JDK 17, Android SDK 36 e Gradle 9.5:

```bash
gradle --no-daemon :app:assembleRelease
```

APKs distribuídos devem ser assinados com o certificado estável documentado em [`SIGNING.md`](SIGNING.md). A chave privada nunca deve ser adicionada a este repositório público.

## Projeto

- Android min SDK: 28
- Android target/compile SDK: 36
- Health Connect: `androidx.health.connect:connect-client:1.1.0`
- Package: `com.example.pokewalklite`
