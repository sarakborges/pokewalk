# LiteWalker

Aplicativo Android para acompanhar caminhadas planejadas e registrar estimativas de passos e distância no Health Connect.

Versão atual: **v1.1.0** (`versionCode 10100`).

## Destaques

- cabeçalho fixo e interface limpa para escolher velocidade e distância;
- modos claro e escuro, com seleção persistente;
- interface completa em português do Brasil e inglês dos Estados Unidos;
- modo sem limite, que continua até a atividade ser encerrada manualmente;
- acompanhamento de tempo, distância, passos estimados e progresso;
- cancelamento com salvamento do progresso já realizado;
- histórico local das cinco atividades mais recentes, com opção de limpeza;
- notificações durante a atividade, a cada quilômetro e ao concluir;
- versão fixada no rodapé;
- política de privacidade acessível dentro do app;
- identidade própria, com a silhueta de um aventureiro caminhando e área segura ampliada no ícone.

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
