# AI Agent Context: chaim-client-java

**Purpose**: Structured context for AI agents to understand and modify the chaim-client-java repository.  
This package is a hybrid Node and Java code generator that converts bprint schemas into production-ready Java source code with DynamoDB Enhanced Client integration.

**Package**: `@chaim-tools/client-java`  
**Version**: 0.1.0  
**License**: Apache-2.0

---

## What this repo does

chaim-client-java generates **production-ready Java source code** from schema JSON and datastore metadata.  
It is an **internal dependency** of `chaim-cli` — end users should not invoke it directly.

**Invocation model**:
- End users run `chaim generate --language java --package com.example.model`
- chaim-cli reads OS cache snapshots and invokes this package internally
- Direct JAR invocation is only for local development/testing

**Data flow**:
```
.bprint file → chaim-cdk → OS cache snapshot → chaim-cli → chaim-client-java → .java files
     ↑             ↑                               ↑
user defines   user deploys                   user generates
```

> **Note**: This package does not read `.bprint` files or OS cache snapshots directly. It receives parsed schema JSON from `chaim-cli`.

**Primary outputs**:
- Entity DTOs with DynamoDB Enhanced Client annotations on **schema-defined keys**
- Key constants helpers (`{Entity}Keys.java`)
- Repository classes with key-based CRUD operations
- DI-friendly DynamoDB client wrapper (`ChaimDynamoDbClient.java`)
- Configuration with repository factory methods (`ChaimConfig.java`)

---

## Schema-Driven Keys (Core Design)

When you define your `.bprint` schema:
```json
{
  "entity": {
    "name": "User",
    "primaryKey": {
      "partitionKey": "userId",    // ← This field gets @DynamoDbPartitionKey
      "sortKey": "entityType"       // ← This field gets @DynamoDbSortKey
    },
    "fields": [
      { "name": "userId", "type": "string" },
      { "name": "entityType", "type": "string" },
      { "name": "email", "type": "string" }
    ]
  }
}
```

The generator annotates **your fields** with DynamoDB annotations:
```java
@DynamoDbBean
public class User {
    private String userId;      // Your partition key
    private String entityType;  // Your sort key
    private String email;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }

    @DynamoDbSortKey
    public String getEntityType() { return entityType; }
}
```

**Benefits**:
- ✅ **Drop-in replacement** — Generated code matches existing table structure
- ✅ **Data migration friendly** — No key transformation needed
- ✅ **Schema is truth** — Single source of truth for key structure
- ✅ **Existing data compatible** — Works with tables that already have data

---

## Multi-Entity Table Support

Multiple entities can share the same DynamoDB table if they have **matching PK/SK field names**:

```
Table: DataTable (PK: userId, SK: entityType)
├── User   → primaryKey: { partitionKey: "userId", sortKey: "entityType" } ✅
├── Order  → primaryKey: { partitionKey: "userId", sortKey: "entityType" } ✅
└── Product → primaryKey: { partitionKey: "productId", sortKey: "category" } ❌ ERROR!
```

**Validation**: chaim-cli validates that all entities bound to the same table have matching PK/SK field names before generation.

---

## How it works

**Runtime flow**:
1. chaim-cli groups snapshots by physical table (using `tableArn` or composite key)
2. chaim-cli validates PK/SK consistency across all entities for each table
3. chaim-cli calls `JavaGenerator.generateForTable()` with all schemas for a table
4. The TypeScript wrapper spawns `java -jar` with schemas and metadata as JSON
5. The Java generator parses JSON and writes Java files to the output directory

```mermaid
flowchart LR
    CLI["chaim-cli (Node.js)<br/>groups by table<br/>validates PK/SK<br/>calls generateForTable()"]
    TS["TypeScript wrapper<br/>resolves JAR path<br/>spawns java process"]
    JAR["Java generator JAR<br/>parses schema JSON<br/>generates .java files"]
    OUT["Generated .java files<br/>per entity + shared infra"]

    CLI --> TS --> JAR --> OUT
```

---

## Inputs and outputs

**Inputs**:
- Array of schema JSON objects (extracted from OS cache snapshots by chaim-cli)
- Java package name
- Output directory
- Table metadata JSON (tableName, tableArn, region)

**Outputs**:
- `.java` files written to the output directory under the provided package namespace

---

## CLI interface

Java is invoked with **multiple schemas**:

```bash
# Inline JSON array
java -jar codegen-java.jar \
  --schemas '[{"schemaVersion":"v1",...},{"schemaVersion":"v1",...}]' \
  --package com.example.model \
  --output ./src/main/java \
  --table-metadata '{"tableName":"DataTable","tableArn":"arn:..."}'

# File-based (for large payloads)
java -jar codegen-java.jar \
  --schemas-file /tmp/schemas.json \
  --package com.example.model \
  --output ./src/main/java \
  --table-metadata '{"tableName":"DataTable",...}'
```

| Argument | Required | Description |
|----------|----------|-------------|
| `--schemas` | Yes* | JSON array of schema objects |
| `--schemas-file` | Yes* | Path to file containing JSON array of schemas |
| `--package` | Yes | Java package name |
| `--output` | Yes | Output directory |
| `--table-metadata` | No | Table metadata JSON string |

*One of `--schemas` or `--schemas-file` is required.

---

## Repository structure

```
chaim-client-java/
├── schema-core/          # Schema loading and validation
├── codegen-java/         # Java generator engine (includes TableMetadata)
├── src/                  # TypeScript wrapper source
└── dist/                 # Compiled TypeScript and bundled JARs
```

---

## Modules

### schema-core

**Responsibilities**:
- Load bprint JSON
- Validate schema shape
- Map field types

**Key classes**:
| Class | Purpose |
|-------|---------|
| `BprintLoader` | Loads schema JSON using Jackson |
| `BprintValidator` | Validates structure and fields |
| `FieldType` | Maps bprint types to Java types |
| `BprintSchema` | Jackson model for schema |

### codegen-java

**Responsibilities**:
- Parse command-line args
- Parse schema and metadata JSON payloads
- Generate Java source files via JavaPoet

**Key classes**:
| Class | Purpose |
|-------|---------|
| `Main` | Entry point for `java -jar` |
| `JavaGenerator` | Generates DTOs, keys, repositories, client, config |
| `TableMetadata` | Simple record for table metadata (tableName, tableArn, region) |

---

## Generated output layout

Example: `--package com.example.model` with User and Order schemas

```
com/example/model/
├── User.java                      # Entity DTO with schema-defined keys
├── Order.java                     # Entity DTO with schema-defined keys
├── keys/
│   ├── UserKeys.java              # Key constants and helpers
│   └── OrderKeys.java             # Key constants and helpers
├── repository/
│   ├── UserRepository.java        # Key-based CRUD
│   └── OrderRepository.java       # Key-based CRUD
├── client/
│   └── ChaimDynamoDbClient.java   # DI-friendly client wrapper
└── config/
    └── ChaimConfig.java           # Constants + repository factories
```

---

## Generated artifacts detail

### Entity DTO (`User.java`)

Uses **schema-defined keys** — no invented `pk`/`sk` fields:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class User {
    private String userId;      // Schema-defined partition key
    private String entityType;  // Schema-defined sort key (if present)
    private String email;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }

    @DynamoDbSortKey
    public String getEntityType() { return entityType; }
}
```

### Key Constants (`UserKeys.java`)

Provides constants for key field names and a convenience method:

```java
public final class UserKeys {
    public static final String PARTITION_KEY_FIELD = "userId";
    public static final String SORT_KEY_FIELD = "entityType";  // If sort key defined

    public static Key key(String userId, String entityType) {
        return Key.builder()
            .partitionValue(userId)
            .sortValue(entityType)
            .build();
    }
}
```

### Repository (`UserRepository.java`)

Uses schema-defined key parameters directly:

```java
public class UserRepository {
    private final DynamoDbTable<User> table;

    // Constructor with ChaimDynamoDbClient
    public UserRepository(ChaimDynamoDbClient client) { ... }

    // Constructor for DI/testing
    public UserRepository(DynamoDbEnhancedClient enhancedClient, String tableName) { ... }

    public void save(User entity) { ... }
    public Optional<User> findByKey(String userId, String entityType) { ... }
    public void deleteByKey(String userId, String entityType) { ... }
    // NOTE: No findAll() or scan() - intentionally omitted
}
```

---

## DynamoDB Operations Matrix

This matrix shows which DynamoDB operations are available in the generated repository and their current status.

### Repository CRUD Operations

| Operation | Method | Status | Description |
|-----------|--------|--------|-------------|
| **Create** | `save(entity)` | ✅ Available | Insert or replace item (PutItem) |
| **Read (by key)** | `findByKey(pk, sk)` | ✅ Available | Get single item by primary key |
| **Update** | `save(entity)` | ✅ Available | Full item replacement (PutItem) |
| **Delete** | `deleteByKey(pk, sk)` | ✅ Available | Remove item by primary key |

### Query & Scan Operations

| Operation | Method | Status | Description |
|-----------|--------|--------|-------------|
| **Scan** | `findAll()` | ❌ Not Available | Full table scan — intentionally omitted (NoSQL anti-pattern) |
| **Query (by PK)** | `queryByPartitionKey()` | 🔜 Backlog | Query all items with same partition key |
| **Query (with SK condition)** | `queryByPartitionKeyAndSortKeyBeginsWith()` | 🔜 Backlog | Query with sort key prefix |
| **Query (GSI)** | `queryByGsi()` | 🔜 Backlog | Query using Global Secondary Index |
| **Query (LSI)** | `queryByLsi()` | 🔜 Backlog | Query using Local Secondary Index |

### Batch Operations

| Operation | Method | Status | Description |
|-----------|--------|--------|-------------|
| **Batch Write** | `saveAll(entities)` | 🔜 Backlog | Batch put multiple items |
| **Batch Get** | `findAllByKeys(keys)` | 🔜 Backlog | Batch get multiple items by keys |
| **Batch Delete** | `deleteAllByKeys(keys)` | 🔜 Backlog | Batch delete multiple items |

### Conditional & Transactional Operations

| Operation | Method | Status | Description |
|-----------|--------|--------|-------------|
| **Conditional Put** | `saveIfNotExists(entity)` | 🔜 Backlog | Put only if item doesn't exist |
| **Conditional Update** | `updateWithCondition()` | 🔜 Backlog | Update with condition expression |
| **Transact Write** | `transactSave(entities)` | 🔜 Backlog | ACID transaction across items |
| **Transact Get** | `transactFind(keys)` | 🔜 Backlog | Consistent read across items |

### Partial Update Operations

| Operation | Method | Status | Description |
|-----------|--------|--------|-------------|
| **Update Attribute** | `updateAttribute(pk, sk, attr, value)` | 🔜 Backlog | Update single attribute |
| **Increment Counter** | `incrementCounter(pk, sk, attr, delta)` | 🔜 Backlog | Atomic counter increment |
| **Append to List** | `appendToList(pk, sk, attr, values)` | 🔜 Backlog | Append values to list attribute |

### Status Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Available — generated and ready to use |
| 🔜 | Backlog — planned for future release |
| ❌ | Not Available — intentionally omitted |

### Design Decisions

**Why no `scan()` / `findAll()`?**

Full table scans are intentionally omitted because:
1. **Cost** — Scans read every item, incurring high read capacity costs
2. **Performance** — Scans don't scale; response time grows with table size
3. **Best Practice** — NoSQL requires explicit access patterns, not ad-hoc queries

If you need all items, define a GSI with a known partition key pattern (e.g., `GSI1PK = "ENTITY#User"`) and use `queryByGsi()` when available.

**Why PutItem instead of UpdateItem for save()?**

The current `save()` uses `PutItem` which replaces the entire item. This is simpler and covers most use cases. Partial updates via `UpdateItem` are on the backlog for scenarios where you need to update specific attributes without fetching the full item first.

---

### DI-Friendly Client (`ChaimDynamoDbClient.java`)

```java
public class ChaimDynamoDbClient {
    public static Builder builder() { ... }
    public static ChaimDynamoDbClient wrap(DynamoDbEnhancedClient client, String tableName) { ... }

    public DynamoDbEnhancedClient getEnhancedClient() { ... }
    public String getTableName() { ... }

    public static class Builder {
        public Builder tableName(String tableName) { ... }
        public Builder region(String region) { ... }
        public Builder endpoint(String endpoint) { ... }  // For local DynamoDB
        public Builder existingClient(DynamoDbEnhancedClient client) { ... }  // For DI
        public ChaimDynamoDbClient build() { ... }
    }
}
```

### Configuration (`ChaimConfig.java`)

```java
public class ChaimConfig {
    public static final String TABLE_NAME = "DataTable";
    public static final String TABLE_ARN = "arn:aws:dynamodb:...";
    public static final String REGION = "us-east-1";

    public static ChaimDynamoDbClient getClient() { ... }  // Lazy singleton
    public static ChaimDynamoDbClient.Builder clientBuilder() { ... }

    // Repository factory methods
    public static UserRepository userRepository() { ... }
    public static UserRepository userRepository(ChaimDynamoDbClient client) { ... }
    public static OrderRepository orderRepository() { ... }
    public static OrderRepository orderRepository(ChaimDynamoDbClient client) { ... }
}
```

---

## Type mapping

| bprint type | Java type |
|-------------|-----------|
| `string` | `String` |
| `number` | `Double` |
| `boolean` | `Boolean` |
| `timestamp` | `Instant` |
| (unknown) | `Object` |

---

## Annotations used

| Annotation | Source | Purpose |
|------------|--------|---------|
| `@DynamoDbBean` | AWS SDK | Marks class as DynamoDB entity |
| `@DynamoDbPartitionKey` | AWS SDK | Marks partition key getter (on schema-defined field) |
| `@DynamoDbSortKey` | AWS SDK | Marks sort key getter (on schema-defined field) |
| `@Data` | Lombok | Generates getters, setters, equals, hashCode, toString |
| `@Builder` | Lombok | Generates builder pattern |
| `@NoArgsConstructor` | Lombok | Generates no-args constructor |
| `@AllArgsConstructor` | Lombok | Generates all-args constructor |

---

## Build and packaging

One command builds everything:

```bash
npm run build
```

**Steps**:
1. Gradle builds Java modules and JAR
2. TypeScript compiles to dist
3. JAR is copied into `dist/jars/` for npm publishing

**Published artifacts**:
- `dist/index.js` — TypeScript wrapper
- `dist/jars/codegen-java-0.1.0.jar` — Fat JAR with all dependencies

---

## JAR resolution logic

The wrapper checks for JAR in this order:
1. **Bundled** (npm install): `dist/jars/codegen-java-*.jar`
2. **Development** (local): `codegen-java/build/libs/codegen-java-*.jar`

---

## Entity name derivation

When `entity.name` is not present in the schema, the generator derives it from `namespace`:

```java
// Priority: entity.name > namespace derivation > "Entity"
// Example: "example.users" → "Users"
String[] parts = schema.namespace.split("\\.");
String lastPart = parts[parts.length - 1];
return capitalize(lastPart);
```

---

## Integration with chaim-cli

chaim-cli imports `JavaGenerator` from `@chaim-tools/client-java`:

```typescript
import { JavaGenerator } from '@chaim-tools/client-java';

const generator = new JavaGenerator();

await generator.generateForTable(
  schemas,          // Array of schema objects
  packageName,      // --package flag
  outputDir,        // --output flag
  tableMetadata     // { tableName, tableArn, region }
);
```

---

## Key files to modify

| Task | File |
|------|------|
| Change wrapper spawn logic | `src/index.ts` |
| Change arg parsing | `codegen-java/.../Main.java` |
| Change code generation | `codegen-java/.../JavaGenerator.java` |
| Change schema model | `schema-core/.../BprintSchema.java` |
| Change table metadata shape | `codegen-java/.../TableMetadata.java` |

---

## Requirements

| Component | Version |
|-----------|---------|
| **Java** | **17 LTS** (runtime — JAR is compiled with `--release 17`) |
| **Node.js** | 18+ |
| **Gradle** | 8+ |

> ✅ **Java 17 LTS** is required at runtime. This ensures broad enterprise compatibility.

---

## Common tasks and where to edit

| Task | Where |
|------|-------|
| Add a new bprint field type | `schema-core/.../FieldType.java` and `codegen-java/.../JavaGenerator.java` (mapType) |
| Change repository methods | `codegen-java/.../JavaGenerator.java` (generateRepository) |
| Add a new generated file | `codegen-java/.../JavaGenerator.java` (add new generate method) |
| Add new table metadata fields | `codegen-java/.../TableMetadata.java` |
| Change CLI argument parsing | `codegen-java/.../Main.java` |
| Change key helper logic | `codegen-java/.../JavaGenerator.java` (generateEntityKeys) |
| Change client builder | `codegen-java/.../JavaGenerator.java` (generateChaimDynamoDbClient) |

---

## Non-goals

This package does **not**:
- Deploy AWS resources (that's chaim-cdk)
- Validate cloud account permissions
- Parse bprint from disk — it receives schema JSON from chaim-cli
- Generate code for languages other than Java (v1 DynamoDBMapper support planned)
- Generate `scan()` or `findAll()` methods (NoSQL anti-pattern)
- Invent `pk`/`sk` fields — uses schema-defined keys only

---

## Related packages

| Package | Relationship | Purpose |
|---------|--------------|---------|
| `chaim-cli` | **Consumer** | Invokes JavaGenerator for SDK generation |
| `@chaim-tools/chaim-bprint-spec` | **Schema format** | Defines .bprint schema structure |
| `@chaim-tools/cdk-lib` | **Upstream** | Produces snapshots with schema + metadata |

---

## Development commands

| Command | Purpose |
|---------|---------|
| `npm run build` | Full build (Java + TypeScript + bundle) |
| `./gradlew build` | Build Java modules only |
| `./gradlew test` | Run Java tests |
| `npm run build:ts` | Compile TypeScript only |
| `npm run clean` | Clean all build artifacts |

---

**Note**: This repo is hybrid by design. TypeScript provides the stable interface for chaim-cli. Java does the actual generation using JavaPoet. The JAR is bundled for npm distribution.
