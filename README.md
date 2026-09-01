# LiteWalker

Aplicativo Android para acompanhar caminhadas planejadas e registrar estimativas de passos e distância no Health Connect.

Versão atual: **v1.1.8** (`versionCode 10108`).

## Destaques

- cabeçalho fixo com seletores de tema e idioma compactos, sem fundo, empilhados à direita;
- paleta inspirada na Master Ball, em roxo com detalhes vermelhos;
- modos claro e escuro, com seleção persistente;
- interface completa em português do Brasil e inglês dos Estados Unidos;
- modo sem limite, que continua até a atividade ser encerrada manualmente;
- distância e duração estimada ocultas automaticamente no modo sem limite;
- cards que podem ser recolhidos e expandidos individualmente, com o estado de cada um preservado ao reabrir o app;
- acompanhamento do horário de início, velocidade atual, tempo em `HH:MM:SS`, distância, passos estimados e progresso;
- encerramento com salvamento do progresso já realizado e limpeza do painel atual;
- encerramento resiliente mesmo após recriação do serviço pelo Android ou em exercícios longos;
- histórico local compacto dos cinco exercícios mais recentes, com distância, duração em `HH:MM`, velocidade e passos em duas linhas à esquerda, além de início e fim um acima do outro à direita;
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
