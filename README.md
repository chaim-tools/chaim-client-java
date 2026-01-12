# chaim-client-java

A production-ready Java SDK generator for the Chaim framework. Generates type-safe DynamoDB Enhanced Client code using **schema-defined keys**, Lombok annotations, and DI-friendly architecture.

## Overview

The chaim-client-java is a **hybrid Java/TypeScript package** that serves as the code generation engine for the Chaim ecosystem. It is an **internal dependency** of `chaim-cli` — end users should not invoke it directly.

- **Schema-Driven Keys**: Uses your schema-defined PK/SK fields directly (no invented fields)
- **DynamoDB Enhanced Client**: Full `@DynamoDbBean` annotation support
- **Lombok Integration**: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- **DI-Friendly**: Builder pattern, endpoint override, client injection for testing
- **Drop-in Ready**: Generated code matches your existing table structure
- **No Scan by Default**: Promotes NoSQL best practices

**Invocation Model**:
```
End users → chaim-cli → chaim-client-java (internal)
```

**Data Flow**:
```
.bprint file → chaim-cdk → OS cache snapshot → chaim-cli → chaim-client-java → .java files
     ↑             ↑                               ↑
user defines   user deploys                   user generates
```

> **Note**: This package does not read `.bprint` files or OS cache snapshots directly. It receives parsed schema JSON from `chaim-cli`. Direct invocation is only for local development/testing.

## Schema-Driven Keys

**The generator uses exactly what you define.**

Your schema:
```json
{
  "entity": {
    "primaryKey": {
      "partitionKey": "userId",
      "sortKey": "entityType"
    },
    "fields": [
      { "name": "userId", "type": "string" },
      { "name": "entityType", "type": "string" },
      ...
    ]
  }
}
```

Generated Java:
```java
@DynamoDbBean
public class User {
    private String userId;      // YOUR partition key
    private String entityType;  // YOUR sort key

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }

    @DynamoDbSortKey
    public String getEntityType() { return entityType; }
}
```

**Benefits**:
- ✅ Works with existing tables and data
- ✅ Easy data migrations
- ✅ Schema is the single source of truth

## Installation

### As npm Dependency (Recommended)

```bash
npm install @chaim-tools/client-java
```

This installs the TypeScript wrapper and bundled Java JAR together.

### Building from Source

```bash
# Clone the repository
git clone https://github.com/chaim-tools/chaim-client-java.git
cd chaim-client-java

# Build everything
npm run build
```

## Requirements

- **Java**: 17 LTS (runtime — JAR targets Java 17 for enterprise compatibility)
- **Node.js**: 18+ (for TypeScript wrapper)
- **Gradle**: 8+ (for building from source)

## Repository Structure

```
chaim-client-java/
├── schema-core/           # Core schema handling (BprintLoader, Validator)
├── codegen-java/          # Code generation engine (JavaPoet, TableMetadata)
├── src/
│   └── index.ts           # TypeScript wrapper
├── dist/                  # Compiled output
│   ├── index.js           # TypeScript wrapper
│   └── jars/
│       └── codegen-java-0.1.0.jar  # Bundled fat JAR
└── package.json
```

## Usage

### From chaim-cli (End User Method)

End users should always use the CLI — it handles snapshot discovery and invokes this package internally:

```bash
chaim generate --package com.example.model --language java
```

The CLI:
1. Reads snapshots from OS cache (`~/.chaim/cache/snapshots/`)
2. Groups snapshots by physical table (using `tableArn` or composite key)
3. **Validates PK/SK consistency** across all entities for each table
4. Invokes chaim-client-java with all schemas for each table
5. Writes generated `.java` files to the output directory

## Generated Output

For a package `com.example.model` with User and Order schemas:

```
com/example/model/
├── User.java                      # Entity DTO with schema-defined keys
├── Order.java                     # Entity DTO with schema-defined keys
├── keys/
│   ├── UserKeys.java              # Key constants and helpers
│   └── OrderKeys.java             # Key constants and helpers
├── repository/
│   ├── UserRepository.java        # Key-based CRUD operations
│   └── OrderRepository.java       # Key-based CRUD operations
├── client/
│   └── ChaimDynamoDbClient.java   # DI-friendly client wrapper
└── config/
    └── ChaimConfig.java           # Constants + repository factories
```

### Entity DTO

Uses your schema-defined keys:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class User {
    private String userId;      // Schema-defined partition key
    private String entityType;  // Schema-defined sort key
    private String email;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }

    @DynamoDbSortKey
    public String getEntityType() { return entityType; }
}
```

### Key Constants

```java
public final class UserKeys {
    public static final String PARTITION_KEY_FIELD = "userId";
    public static final String SORT_KEY_FIELD = "entityType";

    public static Key key(String userId, String entityType) {
        return Key.builder()
            .partitionValue(userId)
            .sortValue(entityType)
            .build();
    }
}
```

### Repository

```java
public class UserRepository {
    // DI-friendly constructors
    public UserRepository(ChaimDynamoDbClient client) { ... }
    public UserRepository(DynamoDbEnhancedClient client, String tableName) { ... }

    public void save(User entity) { ... }
    public Optional<User> findByKey(String userId, String entityType) { ... }
    public void deleteByKey(String userId, String entityType) { ... }
    // NOTE: No findAll() or scan() - promotes NoSQL best practices
}
```

### DI-Friendly Client

```java
// Builder pattern for configuration
ChaimDynamoDbClient client = ChaimDynamoDbClient.builder()
    .tableName("DataTable")
    .region("us-east-1")
    .endpoint("http://localhost:8000")  // For local DynamoDB
    .build();

// Or inject existing client for testing
ChaimDynamoDbClient client = ChaimDynamoDbClient.wrap(mockEnhancedClient, "DataTable");
```

### Configuration

```java
// Use shared client (lazy singleton)
UserRepository users = ChaimConfig.userRepository();
OrderRepository orders = ChaimConfig.orderRepository();

// Or with custom client
ChaimDynamoDbClient customClient = ChaimConfig.clientBuilder()
    .endpoint("http://localhost:8000")
    .build();
UserRepository users = ChaimConfig.userRepository(customClient);
```

## Multi-Entity Table Validation

For entities sharing a table, all must have **matching PK/SK field names**:

```
Table: DataTable (PK: userId, SK: entityType)
├── User   → partitionKey: "userId", sortKey: "entityType" ✅
├── Order  → partitionKey: "userId", sortKey: "entityType" ✅
└── Product → partitionKey: "productId", sortKey: "category" ❌ ERROR!
```

chaim-cli validates this before generation.

## Type Mappings

| .bprint Type | Java Type |
|--------------|-----------|
| `string` | `String` |
| `number` | `Double` |
| `boolean` | `Boolean` |
| `timestamp` | `Instant` |

## Entity Name Derivation

When `entity.name` is not in the schema, the generator derives it from `namespace`:

- `"namespace": "example.users"` → Class name: `Users`
- `"namespace": "com.acme.orders"` → Class name: `Orders`

## Building

### Full Build

```bash
npm run build
```

This runs:
1. `./gradlew build` - Build Java modules
2. `tsc` - Compile TypeScript
3. Copy JAR to `dist/jars/`

### Individual Steps

```bash
# Java only
./gradlew build
./gradlew :codegen-java:build

# TypeScript only
npm run build:ts

# Tests
./gradlew test
```

### Clean

```bash
npm run clean
```

## Modules

### schema-core

Core schema handling:
- `BprintLoader` - Load schema JSON strings
- `BprintValidator` - Validate schema structure
- `BprintSchema` - Java model with Jackson annotations
- `FieldType` - Type mapping utilities

### codegen-java

Code generation:
- `Main` - CLI entry point (supports `--schemas`, `--schemas-file`)
- `JavaGenerator` - JavaPoet-based code generator with schema-driven keys
- `TableMetadata` - Simple record for table metadata (passed from CLI)

## npm Packaging

The package publishes `dist/` containing:
- TypeScript wrapper (`index.js`)
- Bundled fat JAR (`jars/codegen-java-0.1.0.jar`)

The TypeScript wrapper automatically resolves the JAR location for both:
- **npm install**: `dist/jars/codegen-java-0.1.0.jar`
- **Development**: `codegen-java/build/libs/codegen-java-0.1.0.jar`

## Related Packages

| Package | Purpose |
|---------|---------|
| [chaim-cli](https://github.com/chaim-tools/chaim-cli) | CLI tool that uses this generator |
| [chaim-bprint-spec](https://github.com/chaim-tools/chaim-bprint-spec) | Schema specification |
| [chaim-cdk](https://github.com/chaim-tools/chaim-cdk) | CDK constructs that produce snapshots |

## Roadmap

- AWS SDK v1 DynamoDBMapper annotation support (planned)
- Additional language generators

## License

Apache-2.0

---

**Chaim** means life, representing our mission: supporting the life (data) of software applications as they grow and evolve alongside businesses.
