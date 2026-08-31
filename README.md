# LiteWalker

Aplicativo Android para acompanhar caminhadas planejadas e registrar estimativas de passos e distância no Health Connect.

Versão atual: **v1.0.0** (`versionCode 10000`).

## Destaques

- interface limpa para escolher velocidade e distância;
- acompanhamento de tempo, distância, passos estimados e progresso;
- cancelamento com salvamento do progresso já realizado;
- notificações durante e ao concluir a atividade;
- versão fixada no rodapé;
- política de privacidade acessível dentro do app;
- identidade própria, com a silhueta de um aventureiro caminhando.

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
- Package: `io.github.sarakborges.litewalker`
- Política de privacidade: [`docs/privacy.html`](docs/privacy.html)

O package novo faz do LiteWalker um aplicativo separado das versões pessoais anteriores do PokeWalk.
