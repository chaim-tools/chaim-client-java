# chaim-client-java

A production-ready Java SDK generator for the Chaim framework. Generates type-safe DynamoDB Enhanced Client code with single-table design support, Lombok annotations, and DI-friendly architecture.

## Overview

The chaim-client-java is a **hybrid Java/TypeScript package** that serves as the code generation engine for the Chaim ecosystem. It is an **internal dependency** of `chaim-cli` — end users should not invoke it directly.

- **DynamoDB Enhanced Client**: Full `@DynamoDbBean` annotation support
- **Single-Table Design**: Multiple entities per table with key prefixes
- **Lombok Integration**: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- **DI-Friendly**: Builder pattern, endpoint override, client injection for testing
- **No Scan by Default**: Promotes NoSQL best practices

**Invocation Model**:
```
End users → chaim-cli → chaim-client-java (internal)
```

**Data Flow**:
```
.bprint file → chaim-cdk → OS cache snapshot → chaim-cli → chaim-client-java → .java files
                                                   ↑
                                            user runs this
```

> **Note**: This package does not read `.bprint` files or OS cache snapshots directly. It receives parsed schema JSON from `chaim-cli`. Direct invocation is only for local development/testing.

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
├── cdk-integration/       # AWS CDK/CloudFormation integration
├── codegen-java/          # Code generation engine (JavaPoet)
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
3. Invokes chaim-client-java with all schemas for each table
4. Writes generated `.java` files to the output directory

---

### Direct Invocation (Development/Testing Only)

The following methods are for package maintainers and local testing only.

#### As Node.js Module (Internal)

```typescript
import { JavaGenerator } from '@chaim-tools/client-java';

const generator = new JavaGenerator();

// Single-table design: multiple schemas for one table
await generator.generateForTable(
  [userSchema, orderSchema],  // Array of schema objects
  'com.example.model',        // Java package name
  './src/main/java',          // Output directory
  tableMetadata               // { tableName, tableArn, region }
);
```

#### As Java Library (Testing)

```java
import io.chaim.generators.java.JavaGenerator;
import io.chaim.core.model.BprintSchema;
import io.chaim.cdk.TableMetadata;
import java.nio.file.Path;
import java.util.List;

JavaGenerator generator = new JavaGenerator();

// Multiple schemas for single-table design
generator.generateForTable(
    List.of(userSchema, orderSchema),
    "com.example.model",
    Path.of("src/main/java"),
    tableMetadata
);
```

#### As CLI (Testing)

```bash
# Multiple schemas (single-table design)
java -jar codegen-java.jar \
  --schemas '[{"schemaVersion":"v1",...},{"schemaVersion":"v1",...}]' \
  --package com.example.model \
  --output ./src/main/java \
  --table-metadata '{"tableName":"DataTable","tableArn":"arn:..."}'

# File-based for large payloads
java -jar codegen-java.jar \
  --schemas-file /tmp/schemas.json \
  --package com.example.model \
  --output ./src/main/java \
  --table-metadata '{"tableName":"DataTable",...}'
```

## Generated Output

For a package `com.example.model` with User and Order schemas:

```
com/example/model/
├── User.java                      # Entity DTO with pk/sk fields
├── Order.java                     # Entity DTO with pk/sk fields
├── keys/
│   ├── UserKeys.java              # Key composition helpers
│   └── OrderKeys.java             # Key composition helpers
├── repository/
│   ├── UserRepository.java        # PK/SK-based CRUD operations
│   └── OrderRepository.java       # PK/SK-based CRUD operations
├── client/
│   └── ChaimDynamoDbClient.java   # DI-friendly client wrapper
└── config/
    └── ChaimConfig.java           # Constants + repository factories
```

### Entity DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class User {
    private String pk;      // "USER#{userId}"
    private String sk;      // "USER"
    private String userId;
    private String email;

    @DynamoDbPartitionKey
    public String getPk() { return pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
}
```

### Key Helpers

```java
public final class UserKeys {
    public static final String ENTITY_PREFIX = "USER#";

    public static String pk(String userId) {
        return ENTITY_PREFIX + userId;  // "USER#user-123"
    }

    public static Key key(String userId) {
        return Key.builder()
            .partitionValue(pk(userId))
            .sortValue(sk())
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
    public Optional<User> findByPkSk(String pk, String sk) { ... }
    public Optional<User> findByUserId(String userId) { ... }  // Convenience
    public void deleteByPkSk(String pk, String sk) { ... }
    public void deleteByUserId(String userId) { ... }
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

### cdk-integration

AWS integration:
- `TableMetadata` - DynamoDB table metadata container
- `CloudFormationReader` - Read CFN stack outputs
- `ChaimStackOutputs` - Stack output container

### codegen-java

Code generation:
- `Main` - CLI entry point (supports `--schemas`, `--schemas-file`, `--schema`)
- `JavaGenerator` - JavaPoet-based code generator with single-table design support

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

## License

Apache-2.0

---

**Chaim** means life, representing our mission: supporting the life (data) of software applications as they grow and evolve alongside businesses.
