# chaim-client-java

A comprehensive Java SDK for the Chaim framework that provides code generation, schema validation, and AWS DynamoDB integration capabilities. This repository contains the tools and libraries needed to generate Java client code from Chaim Bprint schemas and interact with AWS infrastructure.

## Overview

The chaim-client-java repository is part of the Chaim Builder ecosystem and provides:

- **Schema Validation**: Load and validate Chaim Bprint schemas
- **Code Generation**: Generate Java DTOs, configuration classes, and DynamoDB mappers
- **AWS Integration**: Read CloudFormation stack outputs and extract table metadata
- **Runtime SDK**: Provide validation helpers and schema versioning for generated clients

## Repository Structure

This is a multi-module Gradle project with the following components:

### Core Modules

#### `schema-core`
The foundational module containing core schema handling functionality:
- **BprintLoader**: Loads and parses Bprint schema files
- **BprintValidator**: Validates schema structure and field definitions
- **FieldType**: Manages supported field types and validation
- **BprintSchema**: Data models for schema representation

**Dependencies**: Jackson for JSON processing

#### `cdk-integration`
AWS CloudFormation and DynamoDB integration module:
- **CloudFormationReader**: Reads stack outputs from ChaimBinder deployments
- **TableMetadata**: Represents DynamoDB table metadata and schema information
- **ChaimStackOutputs**: Container for CloudFormation stack outputs

**Dependencies**: AWS SDK v2 (CloudFormation, DynamoDB, STS), Jackson

#### `codegen-java`
Java code generation engine:
- **JavaGenerator**: Generates Java classes from Bprint schemas
  - Entity DTOs with getters/setters and validation
  - ChaimConfig classes with table metadata
  - ChaimMapperClient for DynamoDB operations

**Dependencies**: JavaPoet for code generation, AWS SDK v2, schema-core, cdk-integration

### TypeScript Integration

The repository also includes TypeScript integration (`src/index.ts`) that provides a Node.js interface to the Java code generator, allowing it to be used from the Chaim CLI and other Node.js tools.

## Features

### Schema Validation
- Comprehensive validation of Bprint schema structure
- Field type validation and enum value checking
- Required field validation
- Duplicate field detection

### Code Generation
- **Entity Classes**: Generate Java DTOs with:
  - Private fields with public getters/setters
  - Schema version tracking
  - Built-in validation methods
  - Support for all Chaim field types (string, number, bool, timestamp)

- **Configuration Classes**: Generate ChaimConfig classes with:
  - Table name, ARN, and region constants
  - Static factory methods for mapper creation
  - Metadata access methods

- **Mapper Clients**: Generate ChaimMapperClient classes with:
  - DynamoDB operation stubs (save, findById, findByField)
  - Generic type support
  - Ready for implementation with AWS SDK v2

### AWS Integration
- Read CloudFormation stack outputs from ChaimBinder deployments
- Extract table metadata including ARN, region, and schema data
- Support for multiple tables per stack
- Automatic schema data parsing and validation

## Requirements

- **Java**: 22 or higher
- **Node.js**: 18 or higher (for TypeScript integration)
- **Gradle**: 8.0 or higher
- **AWS Credentials**: Configured for CloudFormation and DynamoDB access

## Building

### Prerequisites
```bash
# Ensure Java 22+ is installed
java --version

# Ensure Node.js 18+ is installed
node --version
```

### Build Commands

```bash
# Build all Java modules
./gradlew build

# Build specific module
./gradlew :schema-core:build
./gradlew :cdk-integration:build
./gradlew :codegen-java:build

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```

### TypeScript Build
```bash
# Build TypeScript integration
npm run build:ts

# Build everything (Java + TypeScript)
npm run build
```

## Usage

### As a Java Library

#### Schema Loading and Validation
```java
import io.chaim.core.BprintLoader;
import io.chaim.core.BprintValidator;
import io.chaim.core.model.BprintSchema;
import java.nio.file.Path;

// Load and validate a schema
BprintSchema schema = BprintLoader.load(Path.of("schema.bprint"));
BprintValidator.validate(schema);
```

#### Code Generation
```java
import io.chaim.generators.java.JavaGenerator;
import io.chaim.cdk.TableMetadata;
import java.nio.file.Path;

JavaGenerator generator = new JavaGenerator();
generator.generate(schema, "com.example.model", Path.of("src/main/java"));
```

#### AWS Integration
```java
import io.chaim.cdk.CloudFormationReader;
import io.chaim.cdk.TableMetadata;

CloudFormationReader reader = new CloudFormationReader();
ChaimStackOutputs outputs = reader.readStackOutputs("my-stack", "us-east-1");
TableMetadata metadata = reader.extractTableMetadata(outputs, "MyTable");
```

### As a Node.js Module

```typescript
import { JavaGenerator } from '@chaim/client-java';

const generator = new JavaGenerator();
await generator.generate(schema, 'com.example.model', './generated', tableMetadata);
```

## Generated Code Structure

When generating code from a Bprint schema, the following structure is created:

```
com.example.model/
├── User.java                    # Entity DTO
├── config/
│   └── ChaimConfig.java         # Table configuration
└── mapper/
    └── ChaimMapperClient.java   # DynamoDB mapper
```

### Example Generated Entity
```java
