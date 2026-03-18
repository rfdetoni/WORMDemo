# WORM Use Demo

Aplicação Spring Boot de demonstração do [WORM ORM](https://github.com/rfdetoni/worm) — um ORM leve, sem dependência de JPA/Hibernate, baseado em JDBC puro.

O projeto expõe uma API REST de **Authors** e **Books** e cobre na prática os principais recursos da biblioteca: ActiveRecord, FilterBuilder, paginação com Slice, soft delete, joins e controle de versão otimista.

---

## Sumário

- [Tecnologias](#tecnologias)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Entidades e recursos WORM demonstrados](#entidades-e-recursos-worm-demonstrados)
- [API REST](#api-rest)
- [Como executar](#como-executar)
- [Testes](#testes)
- [Docker Compose](#docker-compose)
- [Configuração](#configuração)

---

## Tecnologias

| Tecnologia | Versão |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.3 |
| WORM ORM | 1.0.2 |
| PostgreSQL | 16 |
| Flyway | (gerenciado pelo Spring Boot) |
| Lombok | (gerenciado pelo Spring Boot) |
| H2 (testes) | (gerenciado pelo Spring Boot) |

---

## Estrutura do projeto

```
src/
├── main/
│   ├── java/br/com/worm/demo/
│   │   ├── Author.java                 # Entidade Author (ActiveRecord)
│   │   ├── Book.java                   # Entidade Book (ActiveRecord + soft delete)
│   │   ├── WormUseDemoApplication.java
│   │   ├── controller/
│   │   │   ├── AuthorController.java
│   │   │   └── BookController.java
│   │   ├── dto/
│   │   │   ├── AuthorDto.java          # record de resposta
│   │   │   ├── BookDto.java            # record com @DbTable/@DbJoin (projeção)
│   │   │   ├── CreateAuthorDto.java
│   │   │   └── CreateBookDto.java
│   │   ├── mapper/
│   │   │   ├── AuthorMapper.java
│   │   │   └── BookMapper.java
│   │   └── service/
│   │       ├── AuthorService.java
│   │       └── BookService.java
│   └── resources/
│       ├── application.yaml
│       └── db/migration/
│           └── V1__Create_author_and_book_tables.sql
└── test/
    ├── java/br/com/worm/demo/
    │   ├── WormExamplesTests.java      # testes funcionais com H2
    │   └── WormUseDemoApplicationTests.java
    └── resources/
        ├── application-test.yaml
        └── schema.sql
```

---

## Entidades e recursos WORM demonstrados

### `Author` — ActiveRecord simples

```java
@DbTable("authors")
public class Author extends ActiveRecord<Author, UUID> {

    public static final Finder<Author, UUID> find = ActiveRecord.find(Author.class);

    @DbId("id")          private UUID id;
    @DbColumn("name")    private String name;
    @DbColumn("email")   private String email;
    @CreatedAt           private LocalDateTime createdAt;
    @UpdatedAt           private LocalDateTime updatedAt;
    @DbVersion           private long version;   // controle de versão otimista
}
```

**Recursos:** `@DbTable`, `@DbId`, `@DbColumn`, `@CreatedAt`, `@UpdatedAt`, `@DbVersion`, `ActiveRecord.find`.

### `Book` — ActiveRecord com join, soft delete e paginação

```java
@DbTable("books")
public class Book extends ActiveRecord<Book, UUID> {

    public static final Finder<Book, UUID> find = ActiveRecord.find(Book.class);

    @DbId("id")            private UUID id;
    @DbColumn("title")     private String title;
    @DbColumn("isbn")      private String isbn;
    @DbColumn("status")    private String status;
    @DbColumn("author_id") private UUID authorId;

    @DbJoin(table = "authors", alias = "a", on = "a.id = a1.author_id", type = DbJoin.Type.LEFT)
    private Author author;   // join automático

    @CreatedAt   private LocalDateTime createdAt;
    @UpdatedAt   private LocalDateTime updatedAt;
    @DeletedAt   private LocalDateTime deletedAt; // soft delete via timestamp
    @Active      private boolean active;          // soft delete via flag
    @DbVersion   private long version;
}
```

**Recursos:** `@DbJoin`, `@DeletedAt`, `@Active` (soft delete), todas as auditorias.

### `BookDto` — Java record como projeção

```java
@DbTable("books")
public record BookDto(
    @DbId UUID id,
    @DbColumn("title") String title,
    @DbColumn("status") String status,
    @DbJoin(table = "authors", alias = "author", on = "author.id = bookDto.author_id")
    AuthorRef author,
    @DbColumn(expr = "author.name", value = "authorName") String authorName,
    ...
) implements Finder<BookDto, UUID> { ... }
```

Demonstra que **records Java podem ser usados diretamente como projeções** tipadas pelo WORM, sem classe intermediária.

---

## API REST

A aplicação sobe na porta **8090**.

### Authors

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/authors` | Lista todos os autores |
| `GET` | `/api/authors/{id}` | Busca autor por ID |
| `POST` | `/api/authors` | Cria novo autor |
| `DELETE` | `/api/authors/{id}` | Remove autor |

**POST `/api/authors`** — corpo:
```json
{
  "name": "George R. R. Martin",
  "email": "grrm@example.com"
}
```

### Books

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/books` | Lista todos os livros (com autor via join) |
| `GET` | `/api/books/{id}` | Busca livro por ID |
| `POST` | `/api/books` | Cria novo livro |
| `DELETE` | `/api/books/{id}` | Soft delete do livro |

**POST `/api/books`** — corpo:
```json
{
  "title": "A Game of Thrones",
  "isbn": "978-0553593716",
  "status": "PUBLISHED",
  "authorId": "<uuid-do-autor>"
}
```

> Status disponíveis: `DRAFT`, `PUBLISHED`, `OUT_OF_STOCK`.

---

## Como executar

### Pré-requisitos

- Java 25+
- Maven 3.9+ (ou use o wrapper `./mvnw`)
- PostgreSQL 16 rodando (ou use o Docker Compose abaixo)

### Executar localmente

```bash
# 1. Suba o banco via Docker
docker compose -f docker-compose.host-network.yml up postgres -d

# 2. Execute a aplicação
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8090`.

### Build

```bash
./mvnw clean package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

---

## Testes

Os testes usam **H2 em memória** (modo PostgreSQL) — nenhum banco externo é necessário.

```bash
./mvnw test
```

| Teste | O que verifica |
|-------|----------------|
| `testActiveRecordCreateAndFind` | `save()` e `find.byId()` |
| `testFilterBuilderQueryAndCount` | `FilterBuilder.eq()`, `find.all(filter)`, `find.count()` |
| `testUpdateVersionIncrementOnSave` | incremento de `@DbVersion` no `save()` |
| `testPaginationWithSlice` | `Pageable`, `Slice.content()`, `Slice.hasNext()` |
| `testSoftDeleteBehavior` | `delete()` via `@DeletedAt`/`@Active` |
| `testAggregationTotalBooks` | `find.count()` sem filtro |
| `testRecordProjectionWithJdbcTemplate` | projeção com record + `JdbcTemplate` |

---

## Docker Compose

### `docker-compose.yml` — modo padrão (bridge network)

```bash
docker compose up --build
```

Requer suporte a `veth` no kernel. Postgres na porta `5432`, app na `8090`.

### `docker-compose.host-network.yml` — fallback host network (Linux)

Use este arquivo se o Docker não conseguir criar interfaces `veth`:

```bash
docker compose -f docker-compose.host-network.yml up --build
```

> O Postgres sobe na porta **5433** para não conflitar com outras instâncias já em execução no host. O app conecta em `127.0.0.1:5433`.

#### Sintoma do problema de rede

```
failed to set up container networking: failed to add the host (...) <=> sandbox (...) pair interfaces: operation not supported
```

Isso ocorre quando o módulo `veth` do kernel não está carregado — comum após atualização de kernel sem reinicialização. A solução definitiva é **reiniciar o sistema**. O arquivo host-network é o workaround enquanto isso não é feito.

---

## Configuração

### `src/main/resources/application.yaml`

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/worm_demo}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
  flyway:
    enabled: ${SPRING_FLYWAY_ENABLED:true}

server:
  port: 8090

worm:
  batch-size: 1000
  enable-schema-validation: true
  query:
    repository:
      base-packages:
        - br.com.worm.demo
```

### Variáveis de ambiente (Docker Compose)

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/worm_demo` | URL do banco |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `password` | Senha do banco |
| `SPRING_FLYWAY_ENABLED` | `true` | Habilita migrações Flyway |

---

## Dependência WORM

A biblioteca é resolvida via [JitPack](https://jitpack.io):

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.rfdetoni</groupId>
    <artifactId>worm</artifactId>
    <version>1.0.2</version>
</dependency>
```
