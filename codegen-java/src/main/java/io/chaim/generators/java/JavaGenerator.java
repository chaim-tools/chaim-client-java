package io.chaim.generators.java;

import com.squareup.javapoet.*;
import io.chaim.core.model.BprintSchema;
import io.chaim.cdk.TableMetadata;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Java code generator for DynamoDB Enhanced Client.
 * 
 * Uses schema-defined primary keys directly - no invented fields.
 * The partition key and sort key defined in the .bprint schema are
 * annotated with @DynamoDbPartitionKey and @DynamoDbSortKey respectively.
 * 
 * Generates:
 * - Entity DTOs with DynamoDB Enhanced Client annotations on schema-defined keys
 * - Key constants helper ({Entity}Keys.java) with field name references
 * - Entity-specific repositories with key-based operations
 * - Shared DI-friendly DynamoDB client (ChaimDynamoDbClient)
 * - Configuration with repository factory methods (ChaimConfig)
 */
public class JavaGenerator {

    // DynamoDB Enhanced Client annotation class names
    private static final ClassName DYNAMO_DB_BEAN = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb.mapper.annotations", "DynamoDbBean");
    private static final ClassName DYNAMO_DB_PARTITION_KEY = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb.mapper.annotations", "DynamoDbPartitionKey");
    private static final ClassName DYNAMO_DB_SORT_KEY = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb.mapper.annotations", "DynamoDbSortKey");
    
    // Lombok annotation class names
    private static final ClassName LOMBOK_DATA = ClassName.get("lombok", "Data");
    private static final ClassName LOMBOK_BUILDER = ClassName.get("lombok", "Builder");
    private static final ClassName LOMBOK_NO_ARGS_CONSTRUCTOR = ClassName.get("lombok", "NoArgsConstructor");
    private static final ClassName LOMBOK_ALL_ARGS_CONSTRUCTOR = ClassName.get("lombok", "AllArgsConstructor");
    
    // AWS SDK class names
    private static final ClassName DYNAMO_DB_ENHANCED_CLIENT = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "DynamoDbEnhancedClient");
    private static final ClassName DYNAMO_DB_TABLE = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "DynamoDbTable");
    private static final ClassName TABLE_SCHEMA = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "TableSchema");
    private static final ClassName KEY = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "Key");
    private static final ClassName DYNAMO_DB_CLIENT = ClassName.get(
        "software.amazon.awssdk.services.dynamodb", "DynamoDbClient");
    private static final ClassName REGION = ClassName.get(
        "software.amazon.awssdk.regions", "Region");

    /**
     * Generate code for multiple schemas sharing the same DynamoDB table.
     * This is the primary API for multi-entity table support.
     * 
     * @param schemas List of .bprint schemas for entities in this table
     * @param pkg Java package name for generated code
     * @param outDir Output directory
     * @param tableMetadata Table metadata (name, ARN, region)
     */
    public void generateForTable(List<BprintSchema> schemas, String pkg, Path outDir, TableMetadata tableMetadata) throws IOException {
        // Collect entity names for shared infrastructure generation
        List<String> entityNames = new ArrayList<>();
        for (BprintSchema schema : schemas) {
            entityNames.add(deriveEntityName(schema));
        }

        // 1. Generate shared infrastructure ONCE
        if (tableMetadata != null) {
            // Generate ChaimDynamoDbClient (shared DI-friendly client)
            generateChaimDynamoDbClient(pkg, outDir);
            
            // Generate ChaimConfig with repository factories
            generateChaimConfig(tableMetadata, pkg, entityNames, outDir);
        }

        // 2. Generate entity + keys + repository for each schema
        for (BprintSchema schema : schemas) {
            String entityName = deriveEntityName(schema);
            
            // Generate entity DTO with schema-defined keys
            generateEntity(schema, entityName, pkg, outDir);
            
            // Generate key constants helper
            generateEntityKeys(schema, entityName, pkg, outDir);
            
            // Generate repository with key-based operations
            if (tableMetadata != null) {
                generateRepository(schema, entityName, pkg, outDir);
            }
        }
    }
    
    /**
     * Derive entity name from schema.
     * Priority: entity.name > last part of namespace (capitalized) > "Entity"
     */
    private String deriveEntityName(BprintSchema schema) {
        if (schema.entity != null && schema.entity.name != null && !schema.entity.name.isEmpty()) {
            return schema.entity.name;
        }
        
        if (schema.namespace != null && !schema.namespace.isEmpty()) {
            String[] parts = schema.namespace.split("\\.");
            String lastPart = parts[parts.length - 1];
            return cap(lastPart);
        }
        
        return "Entity";
    }

    /**
     * Generate entity DTO with schema-defined keys annotated for DynamoDB.
     * 
     * The partition key field from schema.entity.primaryKey.partitionKey gets @DynamoDbPartitionKey.
     * The sort key field (if defined) from schema.entity.primaryKey.sortKey gets @DynamoDbSortKey.
     * No extra pk/sk fields are invented - we use exactly what the schema defines.
     */
    private void generateEntity(BprintSchema schema, String entityName, String pkg, Path outDir) throws IOException {
        String pkFieldName = schema.entity.primaryKey.partitionKey;
        String skFieldName = schema.entity.primaryKey.sortKey;
        boolean hasSortKey = skFieldName != null && !skFieldName.isEmpty();

        TypeSpec.Builder tb = TypeSpec.classBuilder(entityName)
            .addModifiers(Modifier.PUBLIC)
            // Lombok annotations
            .addAnnotation(LOMBOK_DATA)
            .addAnnotation(LOMBOK_BUILDER)
            .addAnnotation(LOMBOK_NO_ARGS_CONSTRUCTOR)
            .addAnnotation(LOMBOK_ALL_ARGS_CONSTRUCTOR)
            // DynamoDB annotation
            .addAnnotation(DYNAMO_DB_BEAN);

        // Add all fields from schema
        for (BprintSchema.Field field : schema.entity.fields) {
            ClassName type = mapType(field.type);
            tb.addField(FieldSpec.builder(type, field.name, Modifier.PRIVATE).build());
        }

        // Generate explicit getter for partition key with @DynamoDbPartitionKey
        String pkGetterName = "get" + cap(pkFieldName);
        ClassName pkType = findFieldType(schema, pkFieldName);
        tb.addMethod(MethodSpec.methodBuilder(pkGetterName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(DYNAMO_DB_PARTITION_KEY)
            .returns(pkType)
            .addStatement("return $L", pkFieldName)
            .build());

        // Generate explicit getter for sort key with @DynamoDbSortKey (if defined)
        if (hasSortKey) {
            String skGetterName = "get" + cap(skFieldName);
            ClassName skType = findFieldType(schema, skFieldName);
            tb.addMethod(MethodSpec.methodBuilder(skGetterName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(DYNAMO_DB_SORT_KEY)
                .returns(skType)
                .addStatement("return $L", skFieldName)
                .build());
        }

        // All other getters/setters are generated by Lombok @Data

        JavaFile.builder(pkg, tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Find the type of a field by name in the schema.
     */
    private ClassName findFieldType(BprintSchema schema, String fieldName) {
        for (BprintSchema.Field field : schema.entity.fields) {
            if (field.name.equals(fieldName)) {
                return mapType(field.type);
            }
        }
        // Default to String if not found (shouldn't happen with valid schemas)
        return ClassName.get(String.class);
    }

    /**
     * Generate key constants helper.
     * 
     * Provides constants for the partition key and sort key field names,
     * plus a convenience method to build DynamoDB Key objects.
     */
    private void generateEntityKeys(BprintSchema schema, String entityName, String pkg, Path outDir) throws IOException {
        String keysClassName = entityName + "Keys";
        
        String pkFieldName = schema.entity.primaryKey.partitionKey;
        String skFieldName = schema.entity.primaryKey.sortKey;
        boolean hasSortKey = skFieldName != null && !skFieldName.isEmpty();

        TypeSpec.Builder tb = TypeSpec.classBuilder(keysClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addJavadoc("Key constants for $L entity.\n", entityName)
            .addJavadoc("Partition key: $L\n", pkFieldName)
            .addJavadoc(hasSortKey ? "Sort key: $L\n" : "No sort key defined.\n", skFieldName);

        // Private constructor (utility class)
        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .build());

        // PARTITION_KEY_FIELD constant
        tb.addField(FieldSpec.builder(String.class, "PARTITION_KEY_FIELD", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", pkFieldName)
            .addJavadoc("The field name used as partition key.\n")
            .build());

        // SORT_KEY_FIELD constant (if defined)
        if (hasSortKey) {
            tb.addField(FieldSpec.builder(String.class, "SORT_KEY_FIELD", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", skFieldName)
                .addJavadoc("The field name used as sort key.\n")
                .build());
        }

        // key() method - build DynamoDB Key object from field values
        if (hasSortKey) {
            // With sort key
            tb.addMethod(MethodSpec.methodBuilder("key")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("Build a Key object for DynamoDB operations.\n")
                .addJavadoc("@param $L partition key value\n", pkFieldName)
                .addJavadoc("@param $L sort key value\n", skFieldName)
                .addParameter(String.class, pkFieldName)
                .addParameter(String.class, skFieldName)
                .returns(KEY)
                .addStatement("return $T.builder()\n.partitionValue($L)\n.sortValue($L)\n.build()", 
                    KEY, pkFieldName, skFieldName)
                .build());
        } else {
            // Without sort key
            tb.addMethod(MethodSpec.methodBuilder("key")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("Build a Key object for DynamoDB operations.\n")
                .addJavadoc("@param $L partition key value\n", pkFieldName)
                .addParameter(String.class, pkFieldName)
                .returns(KEY)
                .addStatement("return $T.builder()\n.partitionValue($L)\n.build()", 
                    KEY, pkFieldName)
                .build());
        }

        JavaFile.builder(pkg + ".keys", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Generate entity-specific repository with key-based operations.
     * 
     * Uses the schema-defined partition key and sort key fields directly.
     * No scan() by default - only explicit access patterns.
     */
    private void generateRepository(BprintSchema schema, String entityName, String pkg, Path outDir) throws IOException {
        String repoClassName = entityName + "Repository";
        String pkFieldName = schema.entity.primaryKey.partitionKey;
        String skFieldName = schema.entity.primaryKey.sortKey;
        boolean hasSortKey = skFieldName != null && !skFieldName.isEmpty();
        
        ClassName entityClass = ClassName.get(pkg, entityName);
        ClassName keysClass = ClassName.get(pkg + ".keys", entityName + "Keys");
        ClassName clientClass = ClassName.get(pkg + ".client", "ChaimDynamoDbClient");
        ParameterizedTypeName tableType = ParameterizedTypeName.get(DYNAMO_DB_TABLE, entityClass);
        ParameterizedTypeName optionalEntity = ParameterizedTypeName.get(
            ClassName.get("java.util", "Optional"), entityClass);

        TypeSpec.Builder tb = TypeSpec.classBuilder(repoClassName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Repository for $L entity with key-based operations.\n", entityName)
            .addJavadoc("Partition key: $L\n", pkFieldName)
            .addJavadoc(hasSortKey ? "Sort key: $L\n" : "No sort key.\n", skFieldName)
            .addJavadoc("No scan operations by default - use explicit access patterns.\n");

        // Table field
        tb.addField(FieldSpec.builder(tableType, "table", Modifier.PRIVATE, Modifier.FINAL).build());

        // Constructor with ChaimDynamoDbClient
        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(clientClass, "client")
            .addStatement("this.table = client.getEnhancedClient()\n.table(client.getTableName(), $T.fromBean($T.class))",
                TABLE_SCHEMA, entityClass)
            .build());

        // Constructor for DI/testing - accepts existing enhanced client
        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Constructor for dependency injection and testing.\n")
            .addParameter(DYNAMO_DB_ENHANCED_CLIENT, "enhancedClient")
            .addParameter(String.class, "tableName")
            .addStatement("this.table = enhancedClient.table(tableName, $T.fromBean($T.class))",
                TABLE_SCHEMA, entityClass)
            .build());

        // save() method
        tb.addMethod(MethodSpec.methodBuilder("save")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Save entity to DynamoDB.\n")
            .addParameter(entityClass, "entity")
            .addStatement("table.putItem(entity)")
            .build());

        // findByKey() method - uses schema-defined keys
        if (hasSortKey) {
            // With sort key - need both PK and SK
            tb.addMethod(MethodSpec.methodBuilder("findByKey")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Find entity by partition key and sort key.\n")
                .addParameter(String.class, pkFieldName)
                .addParameter(String.class, skFieldName)
                .returns(optionalEntity)
                .addStatement("$T key = $T.key($L, $L)", KEY, keysClass, pkFieldName, skFieldName)
                .addStatement("return $T.ofNullable(table.getItem(key))", ClassName.get("java.util", "Optional"))
                .build());

            // deleteByKey() method
            tb.addMethod(MethodSpec.methodBuilder("deleteByKey")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Delete entity by partition key and sort key.\n")
                .addParameter(String.class, pkFieldName)
                .addParameter(String.class, skFieldName)
                .addStatement("$T key = $T.key($L, $L)", KEY, keysClass, pkFieldName, skFieldName)
                .addStatement("table.deleteItem(key)")
                .build());
        } else {
            // Without sort key - just PK
            tb.addMethod(MethodSpec.methodBuilder("findByKey")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Find entity by partition key.\n")
                .addParameter(String.class, pkFieldName)
                .returns(optionalEntity)
                .addStatement("$T key = $T.key($L)", KEY, keysClass, pkFieldName)
                .addStatement("return $T.ofNullable(table.getItem(key))", ClassName.get("java.util", "Optional"))
                .build());

            // deleteByKey() method
            tb.addMethod(MethodSpec.methodBuilder("deleteByKey")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Delete entity by partition key.\n")
                .addParameter(String.class, pkFieldName)
                .addStatement("$T key = $T.key($L)", KEY, keysClass, pkFieldName)
                .addStatement("table.deleteItem(key)")
                .build());
        }

        // NOTE: No findAll() or scan() generated by default

        JavaFile.builder(pkg + ".repository", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Generate DI-friendly DynamoDB client wrapper with builder pattern.
     */
    private void generateChaimDynamoDbClient(String pkg, Path outDir) throws IOException {
        TypeSpec.Builder tb = TypeSpec.classBuilder("ChaimDynamoDbClient")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("DI-friendly DynamoDB Enhanced Client wrapper.\n")
            .addJavadoc("Supports builder pattern, endpoint override, and client injection.\n");

        // Fields
        tb.addField(FieldSpec.builder(DYNAMO_DB_ENHANCED_CLIENT, "enhancedClient", Modifier.PRIVATE, Modifier.FINAL).build());
        tb.addField(FieldSpec.builder(String.class, "tableName", Modifier.PRIVATE, Modifier.FINAL).build());

        // Private constructor
        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(DYNAMO_DB_ENHANCED_CLIENT, "enhancedClient")
            .addParameter(String.class, "tableName")
            .addStatement("this.enhancedClient = enhancedClient")
            .addStatement("this.tableName = tableName")
            .build());

        // getEnhancedClient()
        tb.addMethod(MethodSpec.methodBuilder("getEnhancedClient")
            .addModifiers(Modifier.PUBLIC)
            .returns(DYNAMO_DB_ENHANCED_CLIENT)
            .addStatement("return enhancedClient")
            .build());

        // getTableName()
        tb.addMethod(MethodSpec.methodBuilder("getTableName")
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return tableName")
            .build());

        // Static builder() factory
        ClassName builderClass = ClassName.get(pkg + ".client", "ChaimDynamoDbClient", "Builder");
        tb.addMethod(MethodSpec.methodBuilder("builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Create a builder for configuration.\n")
            .returns(builderClass)
            .addStatement("return new Builder()")
            .build());

        // Static wrap() factory for DI
        ClassName clientClass = ClassName.get(pkg + ".client", "ChaimDynamoDbClient");
        tb.addMethod(MethodSpec.methodBuilder("wrap")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Wrap an existing EnhancedClient (for testing/DI).\n")
            .addParameter(DYNAMO_DB_ENHANCED_CLIENT, "client")
            .addParameter(String.class, "tableName")
            .returns(clientClass)
            .addStatement("return new $T(client, tableName)", clientClass)
            .build());

        // Inner Builder class
        TypeSpec.Builder builderBuilder = TypeSpec.classBuilder("Builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        builderBuilder.addField(String.class, "tableName", Modifier.PRIVATE);
        builderBuilder.addField(String.class, "region", Modifier.PRIVATE);
        builderBuilder.addField(String.class, "endpoint", Modifier.PRIVATE);
        builderBuilder.addField(DYNAMO_DB_ENHANCED_CLIENT, "existingClient", Modifier.PRIVATE);

        builderBuilder.addMethod(MethodSpec.methodBuilder("tableName")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(String.class, "tableName")
            .returns(builderClass)
            .addStatement("this.tableName = tableName")
            .addStatement("return this")
            .build());

        builderBuilder.addMethod(MethodSpec.methodBuilder("region")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(String.class, "region")
            .returns(builderClass)
            .addStatement("this.region = region")
            .addStatement("return this")
            .build());

        builderBuilder.addMethod(MethodSpec.methodBuilder("endpoint")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Override endpoint for local DynamoDB testing.\n")
            .addJavadoc("Example: \"http://localhost:8000\"\n")
            .addParameter(String.class, "endpoint")
            .returns(builderClass)
            .addStatement("this.endpoint = endpoint")
            .addStatement("return this")
            .build());

        builderBuilder.addMethod(MethodSpec.methodBuilder("existingClient")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Use an existing client (for dependency injection).\n")
            .addParameter(DYNAMO_DB_ENHANCED_CLIENT, "client")
            .returns(builderClass)
            .addStatement("this.existingClient = client")
            .addStatement("return this")
            .build());

        // build() method
        builderBuilder.addMethod(MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC)
            .returns(clientClass)
            .beginControlFlow("if (existingClient != null)")
            .addStatement("return new $T(existingClient, tableName)", clientClass)
            .endControlFlow()
            .addCode("\n// Check environment overrides\n")
            .addStatement("String resolvedTable = resolve(tableName, \"CHAIM_TABLE_NAME\")")
            .addStatement("String resolvedRegion = resolve(region, \"AWS_REGION\", \"AWS_DEFAULT_REGION\")")
            .addStatement("String resolvedEndpoint = resolve(endpoint, \"DYNAMODB_ENDPOINT\")")
            .addCode("\n")
            .addStatement("$T.Builder ddbBuilder = $T.builder()", DYNAMO_DB_CLIENT, DYNAMO_DB_CLIENT)
            .beginControlFlow("if (resolvedRegion != null)")
            .addStatement("ddbBuilder.region($T.of(resolvedRegion))", REGION)
            .endControlFlow()
            .beginControlFlow("if (resolvedEndpoint != null)")
            .addStatement("ddbBuilder.endpointOverride($T.create(resolvedEndpoint))", ClassName.get("java.net", "URI"))
            .endControlFlow()
            .addCode("\n")
            .addStatement("$T enhanced = $T.builder()\n.dynamoDbClient(ddbBuilder.build())\n.build()",
                DYNAMO_DB_ENHANCED_CLIENT, DYNAMO_DB_ENHANCED_CLIENT)
            .addStatement("return new $T(enhanced, resolvedTable)", clientClass)
            .build());

        // resolve() helper method
        builderBuilder.addMethod(MethodSpec.methodBuilder("resolve")
            .addModifiers(Modifier.PRIVATE)
            .addParameter(String.class, "value")
            .addParameter(ArrayTypeName.of(String.class), "envVars")
            .varargs(true)
            .returns(String.class)
            .beginControlFlow("if (value != null)")
            .addStatement("return value")
            .endControlFlow()
            .beginControlFlow("for (String env : envVars)")
            .addStatement("String v = System.getenv(env)")
            .beginControlFlow("if (v != null && !v.isEmpty())")
            .addStatement("return v")
            .endControlFlow()
            .endControlFlow()
            .addStatement("return null")
            .build());

        tb.addType(builderBuilder.build());

        JavaFile.builder(pkg + ".client", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Generate ChaimConfig with table constants and repository factory methods.
     */
    private void generateChaimConfig(TableMetadata tableMetadata, String pkg, List<String> entityNames, Path outDir) throws IOException {
        ClassName clientClass = ClassName.get(pkg + ".client", "ChaimDynamoDbClient");
        ClassName builderClass = ClassName.get(pkg + ".client", "ChaimDynamoDbClient", "Builder");

        TypeSpec.Builder tb = TypeSpec.classBuilder("ChaimConfig")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Configuration class with table constants and repository factories.\n");

        // Constants from CDK metadata
        tb.addField(FieldSpec.builder(String.class, "TABLE_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.getTableName())
            .build());

        tb.addField(FieldSpec.builder(String.class, "TABLE_ARN", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.getTableArn())
            .build());

        tb.addField(FieldSpec.builder(String.class, "REGION", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.getRegion())
            .build());

        // Shared client field (volatile for thread-safety)
        tb.addField(FieldSpec.builder(clientClass, "sharedClient", Modifier.PRIVATE, Modifier.STATIC, Modifier.VOLATILE)
            .build());

        // getClient() - lazy singleton with double-checked locking
        tb.addMethod(MethodSpec.methodBuilder("getClient")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Get or create the shared client (lazy singleton).\n")
            .returns(clientClass)
            .beginControlFlow("if (sharedClient == null)")
            .beginControlFlow("synchronized ($T.class)", ClassName.get(pkg + ".config", "ChaimConfig"))
            .beginControlFlow("if (sharedClient == null)")
            .addStatement("sharedClient = $T.builder()\n.tableName(TABLE_NAME)\n.region(REGION)\n.build()", clientClass)
            .endControlFlow()
            .endControlFlow()
            .endControlFlow()
            .addStatement("return sharedClient")
            .build());

        // clientBuilder() - for custom configuration
        tb.addMethod(MethodSpec.methodBuilder("clientBuilder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Create a custom client builder (for testing or custom config).\n")
            .returns(builderClass)
            .addStatement("return $T.builder()\n.tableName(TABLE_NAME)\n.region(REGION)", clientClass)
            .build());

        // Repository factory methods for each entity
        for (String entityName : entityNames) {
            String methodName = uncap(entityName) + "Repository";
            ClassName repoClass = ClassName.get(pkg + ".repository", entityName + "Repository");

            // Factory with default client
            tb.addMethod(MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(repoClass)
                .addStatement("return new $T(getClient())", repoClass)
                .build());

            // Factory with custom client
            tb.addMethod(MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(clientClass, "client")
                .returns(repoClass)
                .addStatement("return new $T(client)", repoClass)
                .build());
        }

        JavaFile.builder(pkg + ".config", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    private static ClassName mapType(String type) {
        return switch (type) {
            case "string" -> ClassName.get(String.class);
            case "number" -> ClassName.get(Double.class);
            case "boolean", "bool" -> ClassName.get(Boolean.class);
            case "timestamp" -> ClassName.get(java.time.Instant.class);
            default -> ClassName.get(Object.class);
        };
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static String uncap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }
}
