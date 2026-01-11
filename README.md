# chaim-client-java

A comprehensive Java SDK for the Chaim framework that provides code generation, schema validation, and AWS DynamoDB integration. This package generates Java DTOs, configuration classes, and DynamoDB mapper clients from `.bprint` schemas.

## Overview

The chaim-client-java is a **hybrid Java/TypeScript package** that serves as the code generation engine for the Chaim ecosystem:

- **Schema Parsing**: Load and validate Chaim `.bprint` schemas
- **Code Generation**: Generate Java DTOs, ChaimConfig, and ChaimMapperClient classes
- **TypeScript Wrapper**: Node.js interface for integration with chaim-cli
- **npm Distribution**: Bundled JAR for seamless npm installation

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

- **Java**: 11+ (runtime for code generation)
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

### From chaim-cli (Recommended)

The CLI handles everything automatically:

```bash
chaim generate --package com.example.model
```

### As Node.js Module

```typescript
import { JavaGenerator } from '@chaim-tools/client-java';

const generator = new JavaGenerator();
await generator.generate(
  schema,           // Schema JSON object
  'com.example.model',  // Java package name
  './src/main/java',    // Output directory
  tableMetadata     // Optional table metadata
);
```

### As Java Library

```java
import io.chaim.generators.java.JavaGenerator;
import io.chaim.core.model.BprintSchema;
import java.nio.file.Path;

JavaGenerator generator = new JavaGenerator();
generator.generate(schema, "com.example.model", Path.of("src/main/java"), tableMetadata);
```

### As CLI

```bash
java -jar codegen-java.jar \
  --schema '{"schemaVersion":"v1",...}' \
  --package com.example.model \
  --output ./src/main/java \
  --table-metadata '{"tableName":"MyTable",...}'
```

## Generated Output

```
com/example/model/
├── Users.java                 # Entity DTO
│   ├── private fields         # With getters/setters
│   ├── chaimVersion           # Schema version constant
│   └── validate()             # Required field validation
│
├── config/
│   └── ChaimConfig.java       # Table configuration
│       ├── TABLE_NAME
│       ├── TABLE_ARN
│       ├── REGION
│       └── createMapper()
│
└── mapper/
    └── ChaimMapperClient.java # DynamoDB mapper stubs
        ├── save(entity)
        ├── findById(class, id)
        └── findByField(class, field, value)
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
- `BprintLoader` - Load `.bprint` JSON files
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
- `Main` - CLI entry point
- `JavaGenerator` - JavaPoet-based code generator

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
