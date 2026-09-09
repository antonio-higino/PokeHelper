# Proposta de Projeto — PokéHelper

## 1. Visão do produto

### Para quem
Jogadores dos jogos Pokémon que precisam de uma forma prática de planejar e montar seus times.

### Problema
Montar um time equilibrado pode exigir que o jogador pesquise manualmente informações sobre diferentes Pokémon, tipos, fraquezas e resistências, ou tente avaliar a composição do time apenas com seu próprio conhecimento das mecânicas do jogo. Isso torna o planejamento mais trabalhoso e dificulta a identificação de lacunas defensivas na equipe.

### Solução
O **PokéHelper** é uma aplicação web que auxilia jogadores no planejamento e na montagem de times Pokémon, oferecendo análise da composição atual e recomendações personalizadas.

A aplicação permite montar times, visualizar suas fraquezas e resistências e receber recomendações dinâmicas de tipos e de Pokémon que podem melhorar o equilíbrio defensivo da equipe. Dessa forma, em vez de depender de pesquisas manuais em wikis ou de planejar o time apenas de cabeça, o jogador recebe sugestões calculadas a partir da composição que já possui.

### Valor diferencial
O principal diferencial do PokéHelper é a recomendação dinâmica da composição do time. As sugestões são baseadas nos Pokémon já escolhidos e procuram contribuir para um time defensivamente mais equilibrado, em vez de apresentar apenas informações isoladas sobre cada Pokémon.

### Viabilidade
O produto será desenvolvido como um projeto acadêmico ao longo do semestre, com um MVP delimitado às funcionalidades centrais de montagem, análise e recomendação de times. A proposta também aproveita conhecimentos e funcionalidades já desenvolvidos no projeto de Web 1.

---

## 2. MVP

O MVP do PokéHelper permitirá que o usuário:

- crie uma conta e realize login;
- pesquise Pokémon pelo nome;
- monte um time com até seis Pokémon;
- remova Pokémon do time;
- salve seus times;
- visualize e carregue times anteriormente salvos;
- renomeie times salvos;
- exclua times salvos;
- visualize as fraquezas e resistências da composição do time;
- receba recomendações de tipos que possam melhorar o equilíbrio defensivo do time;
- receba sugestões de Pokémon compatíveis com os tipos recomendados.

Essas funcionalidades correspondem ao núcleo do produto já desenvolvido em Web1: montagem de times, análise defensiva, recomendações e persistência de times.

### Fora do escopo do MVP

Nesta etapa, não fazem parte do MVP:

- análise das habilidades dos Pokémon;
- seleção e análise de golpes;
- análise de cobertura ofensiva;
- consideração de EVs, IVs, naturezas ou itens;
- filtros por jogo ou geração;
- tela detalhada de cada Pokémon;
- funcionalidades voltadas a batalhas competitivas avançadas.

A delimitação busca manter o MVP viável dentro das quatro sprints previstas para a etapa, concentrando o desenvolvimento no núcleo de valor do produto.

---

## 3. Hipótese de valor

> Acreditamos que jogadores de Pokémon vão planejar e otimizar melhor seus times por meio de recomendações dinâmicas e recursos visuais, porque esses recursos facilitam a compreensão das mecânicas do jogo e das interações entre os diferentes tipos.

A hipótese será considerada apoiada caso os usuários consigam montar e revisar seus times com mais facilidade utilizando a análise defensiva e as recomendações, sem precisar consultar manualmente diversas fontes para identificar possíveis problemas na composição.

---

## 4. Entidades do domínio

As principais entidades consideradas para o MVP são:

### Usuário
Representa a pessoa que utiliza o sistema.

Principais responsabilidades/dados:

- identificação do usuário;
- dados necessários para autenticação;
- relação com os times salvos.

### Pokémon
Representa um Pokémon utilizado na montagem ou análise de um time.

Os dados dos Pokémon são obtidos a partir da PokeAPI e utilizados pelo sistema para pesquisa, montagem da equipe e análise de tipos.

### Time
Representa uma composição criada pelo usuário.

Principais características:

- pertence a um usuário;
- possui um nome;
- contém até seis Pokémon;
- pode ser salvo, carregado, renomeado e excluído.

### Tipo Pokémon
Representa um dos tipos utilizados na mecânica defensiva do jogo.

Os tipos são utilizados para determinar fraquezas, resistências e imunidades e para gerar as recomendações de composição.

### Relações principais

- Um **Usuário** pode possuir vários **Times**.
- Um **Time** pertence a um único **Usuário**.
- Um **Time** contém de zero a seis **Pokémon**, de acordo com o estado de montagem da equipe.
- Um **Pokémon** possui um ou dois **Tipos**.
- Os **Tipos** são utilizados na análise das interações defensivas do **Time**.

A persistência dos dados necessários ao sistema ficará concentrada principalmente no serviço principal, enquanto os dados externos da PokeAPI serão integrados por um microsserviço específico.

---

## 5. Decisão da stack — Kotlin/Ktor

A tecnologia escolhida para o serviço principal é **Kotlin com Ktor**.

A escolha busca explorar uma tecnologia de fronteira adequada ao desenvolvimento de APIs e que permita uma implementação relativamente enxuta. Para o projeto proposto, o Ktor apresenta uma abordagem modular para criação de serviços web, permitindo que a equipe mantenha o serviço principal simples enquanto implementa autenticação, persistência e regras relacionadas ao domínio.

Outro fator considerado é a experiência de aprendizagem proporcionada pela escolha. O projeto tem como objetivo acadêmico não apenas entregar as funcionalidades, mas também explorar tecnologias e decisões arquiteturais diferentes. Nesse contexto, Kotlin/Ktor permite trabalhar com uma stack moderna e adequada à construção de APIs, mantendo o código conciso.

A escolha não se baseia apenas em conveniência: ela também está alinhada às características do domínio e ao objetivo da disciplina de experimentar uma arquitetura de serviços com responsabilidades bem definidas.

### Stack principal

- **Linguagem:** Kotlin
- **Framework:** Ktor
- **Responsabilidades:** API principal, autenticação, domínio e persistência de usuários e times.

---

## 6. Divisão com o serviço Go

A arquitetura será dividida entre um **serviço principal em Kotlin/Ktor** e um **microsserviço em Go**. A separação segue as características dos tipos de trabalho realizados por cada parte.

### Serviço principal — Kotlin/Ktor

Responsável por:

- lógica principal do domínio;
- autenticação de usuários;
- persistência de usuários e times;
- criação, edição, carregamento e exclusão de times;
- exposição da API utilizada pela aplicação web;
- processamento das operações relacionadas ao domínio do sistema.

### Microsserviço de integração com a PokeAPI — Go

Responsável por:

- conexão com a PokeAPI;
- coleta e integração dos dados externos de Pokémon e tipos;
- processamento em lote quando necessário;
- operações de I/O concorrente;
- cache dos dados obtidos da API externa.

A escolha de Go para esse serviço está relacionada ao perfil de suas tarefas: integração com serviço externo, operações de I/O e processamento concorrente são atividades que se beneficiam de um serviço leve e com suporte adequado à concorrência.

### Fluxo simplificado

A aplicação web se comunica com o serviço principal. Quando informações externas da PokeAPI forem necessárias, o serviço principal utiliza o microsserviço em Go, que consulta ou recupera os dados de seu cache. O serviço de recomendação poderá utilizar os dados integrados para apoiar o processamento das sugestões de composição.

A existência do microsserviço é, portanto, justificada pela responsabilidade específica de integração com uma fonte externa e pelo tratamento eficiente de I/O e cache, evitando misturar essas responsabilidades com o núcleo do domínio.

---

## 7. Equipe

| Integrante | Responsabilidade principal |
|---|---|
| Adriano Nobrega Azevedo Filho | A definir pelo grupo |
| Antonio Higino Bisneto Leite Medeiros | A definir pelo grupo |
| Gabriel Guilherme Cavalcanti da Costa | A definir pelo grupo |

---

## 8. Coorte e integração

**Coorte de apresentação:** a definir pelo grupo.

O projeto será desenvolvido em um **monorepo**, contendo os serviços e os arquivos compartilhados necessários à integração. A estrutura prevista é:

```text
/
├── api/
├── services/
├── protos/
└── docs/
```

A arquitetura seguirá a divisão definida entre o serviço principal em Kotlin/Ktor e o microsserviço em Go. A comunicação e os contratos entre os componentes deverão ser mantidos de forma explícita para permitir o desenvolvimento independente dos serviços.

O projeto também deverá possuir um pipeline de **CI com GitHub Actions**, executado em pushes e pull requests, verificando o build dos dois stacks. O CI deverá permanecer passando na branch principal.

### Backlog

O backlog inicial do projeto será mantido no **GitHub Projects**, contendo histórias priorizadas e estimadas. O quadro deve possuir pelo menos cinco histórias, todas priorizadas e com ao menos três itens estimados, conforme solicitado para a Sprint 0.

**Link do backlog:** [inserir link do GitHub Projects]

---

## Resumo das decisões

| Aspecto | Decisão |
|---|---|
| Produto | PokéHelper |
| Público | Jogadores de Pokémon |
| Problema | Dificuldade de planejar times equilibrados manualmente |
| Proposta de valor | Análise defensiva e recomendações dinâmicas de composição |
| Serviço principal | Kotlin/Ktor |
| Microsserviço | Go |
| Fonte externa | PokeAPI |
| Persistência | Serviço principal |
| Autenticação | Serviço principal |
| Integração externa, cache e I/O concorrente | Serviço Go |
| Limite do time | Até 6 Pokémon |
| Backlog | GitHub Projects |
| CI | GitHub Actions |
