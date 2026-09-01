# LiteWalker

Aplicativo Android para acompanhar caminhadas planejadas e registrar estimativas de passos e distância no Health Connect.

Versão atual: **v1.1.6** (`versionCode 10106`).

## Destaques

- cabeçalho fixo com seletores de tema e idioma empilhados à direita;
- paleta inspirada na Master Ball, em roxo com detalhes vermelhos;
- modos claro e escuro, com seleção persistente;
- interface completa em português do Brasil e inglês dos Estados Unidos;
- modo sem limite, que continua até a atividade ser encerrada manualmente;
- distância e duração estimada ocultas automaticamente no modo sem limite;
- cards que podem ser recolhidos e expandidos individualmente;
- acompanhamento de tempo, distância, passos estimados e progresso;
- encerramento com salvamento do progresso já realizado e limpeza do painel atual;
- tempo do exercício atual e do histórico no formato `HH:MM`;
- histórico local dos cinco exercícios mais recentes, com detalhes à esquerda e data/hora à direita alinhados pelo centro vertical;
- notificações durante o exercício e a cada quilômetro, sem notificação adicional ao encerrar;
- versão fixada no rodapé;
- botões dedicados para novidades e política de privacidade;
- identidade própria, com a mesma silhueta branca e sombra preta no launcher e no cabeçalho.

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
