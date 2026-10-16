# PokeHelper

Projeto de Web 2.

Equipe:

- Adriano Nobrega Azevedo Filho - 20250070190
- Antonio Higino Bisneto Leite Medeiros - 20250070261
- Gabriel Guilherme Cavalcanti da Costa - 20200042966

Como rodar:

1. Clone o repositório
2. Instale o Docker com Docker Compose e, para execução local, Java 21 e `mise`
3. Para subir a API e o PostgreSQL:
   ```bash
   docker compose up --build
   ```
   Ou use `mise run up`. A API fica em `http://localhost:8080` e o PostgreSQL em `localhost:5433`.
4. Para encerrar os serviços, execute `docker compose down`.

As configurações do banco podem ser sobrescritas pelas variáveis `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` e `POSTGRES_HOST_PORT` (porta publicada no host). Os valores padrão são destinados apenas ao desenvolvimento local. O Flyway aplica as migrações de `com/PokeHelper/src/api/resources/db/migration` quando a API inicia.

O código do serviço Ktor fica em `com/PokeHelper/src/api/`. As pastas `com/PokeHelper/src/services/` e `com/PokeHelper/src/protos/` ficam reservadas, respectivamente, ao microsserviço Go e aos contratos compartilhados entre serviços.

Vídeo de demonstração:
https://youtu.be/7IoKYcgAcBw
