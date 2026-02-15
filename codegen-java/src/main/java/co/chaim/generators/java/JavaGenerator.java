package co.chaim.generators.java;

import com.squareup.javapoet.*;
import co.chaim.core.model.BprintSchema;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Java code generator for DynamoDB Enhanced Client.
 * 
 * Uses schema-defined primary keys directly - no invented fields.
 * The partition key and sort key defined in the .bprint schema are
 * annotated with @DynamoDbPartitionKey and @DynamoDbSortKey respectively.
 * 
 * Supports nameOverride for fields whose DynamoDB attribute names are not
 * valid Java identifiers. When the resolved code name differs from the
 * original DynamoDB attribute name, a @DynamoDbAttribute annotation is emitted.
 * 
 * Generates:
 * - Entity DTOs with DynamoDB Enhanced Client annotations on schema-defined keys
 * - Key constants helper ({Entity}Keys.java) with field name references
 * - Entity-specific repositories with key-based operations
 * - Shared DI-friendly DynamoDB client (ChaimDynamoDbClient)
 * - Configuration with repository factory methods (ChaimConfig)
 */
public class JavaGenerator {

    private static final Pattern VALID_JAVA_IDENTIFIER = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*$");

    // DynamoDB Enhanced Client annotation class names
    private static final ClassName DYNAMO_DB_BEAN = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb.mapper.annotations", "DynamoDbBean");
    private static final ClassName DYNAMO_DB_PARTITION_KEY = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb.mapper.annotations", "DynamoDbPartitionKey");
    private static final ClassName DYNAMO_DB_SORT_KEY = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb.mapper.annotations", "DynamoDbSortKey");
    private static final ClassName DYNAMO_DB_ATTRIBUTE = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb.mapper.annotations", "DynamoDbAttribute");
    
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
        // Validate all schemas for name collisions before generating any code
        for (BprintSchema schema : schemas) {
            detectCollisions(schema.fields);
        }

        // Collect entity names for shared infrastructure generation
        List<String> entityNames = new ArrayList<>();
        for (BprintSchema schema : schemas) {
            entityNames.add(deriveEntityName(schema));
        }

        // 1. Generate shared infrastructure ONCE
        if (tableMetadata != null) {
            generateChaimDynamoDbClient(pkg, outDir);
            generateChaimConfig(tableMetadata, pkg, entityNames, outDir);
        }

        // 2. Generate entity + keys + repository for each schema
        for (BprintSchema schema : schemas) {
            String entityName = deriveEntityName(schema);
            generateEntity(schema, entityName, pkg, outDir);
            generateEntityKeys(schema, entityName, pkg, outDir);
            if (tableMetadata != null) {
                generateRepository(schema, entityName, pkg, outDir);
            }
        }
    }
    
    /**
     * Derive entity name from schema.
     * Uses entityName field directly, or defaults to "Entity".
     */
    private String deriveEntityName(BprintSchema schema) {
        if (schema.entityName != null && !schema.entityName.isEmpty()) {
            return schema.entityName;
        }
        return "Entity";
    }

    // =========================================================================
    // Name Resolution
    // =========================================================================

    /**
     * Resolve the Java code name for a field.
     * 
     * If nameOverride is set, use it directly.
     * Otherwise, auto-convert the DynamoDB attribute name to a valid Java identifier.
     */
    static String resolveCodeName(BprintSchema.Field field) {
        if (field.nameOverride != null && !field.nameOverride.isEmpty()) {
            return field.nameOverride;
        }
        if (VALID_JAVA_IDENTIFIER.matcher(field.name).matches()) {
            return field.name;
        }
        return toJavaCamelCase(field.name);
    }

    /**
     * Resolve the Java code name for a key field referenced by name.
     * Looks up the field in the schema and resolves its code name.
     */
    private String resolveKeyCodeName(BprintSchema schema, String keyFieldName) {
        for (BprintSchema.Field field : schema.fields) {
            if (field.name.equals(keyFieldName)) {
                return resolveCodeName(field);
            }
        }
        return toJavaCamelCase(keyFieldName);
    }

    /**
     * Convert a DynamoDB attribute name to a valid Java camelCase identifier.
     * 
     * Rules:
     * - Split on hyphens and underscores
     * - First segment is lowercased, subsequent segments have first letter capitalized
     * - Leading digits get underscore prefix
     * - All-caps strings are lowercased
     */
    static String toJavaCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        // Handle all-caps: TTL -> ttl, ABC -> abc
        if (name.equals(name.toUpperCase()) && name.length() > 1 && !name.contains("-") && !name.contains("_")) {
            String result = name.toLowerCase();
            if (Character.isDigit(result.charAt(0))) {
                result = "_" + result;
            }
            return result;
        }

        // Split on hyphens and underscores
        String[] parts = name.split("[-_]");
        if (parts.length == 0) {
            return name;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            if (sb.isEmpty()) {
                // First segment: lowercase
                sb.append(part.substring(0, 1).toLowerCase());
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            } else {
                // Subsequent segments: capitalize first letter
                sb.append(part.substring(0, 1).toUpperCase());
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }

        String result = sb.toString();

        // Prefix with underscore if starts with a digit
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
            result = "_" + result;
        }

        return result;
    }

    /**
     * Check if a @DynamoDbAttribute annotation is needed for this field.
     * The annotation is needed when the Java code name differs from the DynamoDB attribute name.
     */
    static boolean needsAttributeAnnotation(BprintSchema.Field field, String codeName) {
        return !codeName.equals(field.name);
    }

    /**
     * Detect collisions in resolved code names across all fields.
     * Two fields that resolve to the same Java identifier must be caught before generation.
     */
    static void detectCollisions(List<BprintSchema.Field> fields) {
        Map<String, List<String>> codeNameToOriginals = new HashMap<>();
        for (BprintSchema.Field field : fields) {
            String codeName = resolveCodeName(field);
            codeNameToOriginals.computeIfAbsent(codeName, k -> new ArrayList<>()).add(field.name);
        }

        for (Map.Entry<String, List<String>> entry : codeNameToOriginals.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new IllegalArgumentException(
                    "Name collision: fields " + entry.getValue() +
                    " all resolve to Java identifier '" + entry.getKey() +
                    "'. Add nameOverride to one of the conflicting fields in your .bprint."
                );
            }
        }
    }

    /**
     * Build a @DynamoDbAttribute annotation for the given DynamoDB attribute name.
     */
    private static AnnotationSpec dynamoDbAttributeAnnotation(String attributeName) {
        return AnnotationSpec.builder(DYNAMO_DB_ATTRIBUTE)
            .addMember("value", "$S", attributeName)
            .build();
    }

    // =========================================================================
    // Entity Generation
    // =========================================================================

    /**
     * Generate entity DTO with schema-defined keys annotated for DynamoDB.
     * 
     * Uses resolved code names for Java fields and emits @DynamoDbAttribute
     * annotations when the code name differs from the DynamoDB attribute name.
     */
    private void generateEntity(BprintSchema schema, String entityName, String pkg, Path outDir) throws IOException {
        String pkFieldName = schema.primaryKey.partitionKey;
        String skFieldName = schema.primaryKey.sortKey;
        boolean hasSortKey = skFieldName != null && !skFieldName.isEmpty();

        String pkCodeName = resolveKeyCodeName(schema, pkFieldName);
        String skCodeName = hasSortKey ? resolveKeyCodeName(schema, skFieldName) : null;

        TypeSpec.Builder tb = TypeSpec.classBuilder(entityName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(LOMBOK_DATA)
            .addAnnotation(LOMBOK_BUILDER)
            .addAnnotation(LOMBOK_NO_ARGS_CONSTRUCTOR)
            .addAnnotation(LOMBOK_ALL_ARGS_CONSTRUCTOR)
            .addAnnotation(DYNAMO_DB_BEAN);

        // Add all fields from schema using resolved code names
        for (BprintSchema.Field field : schema.fields) {
            String codeName = resolveCodeName(field);
            ClassName type = mapType(field.type);

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(type, codeName, Modifier.PRIVATE);

            // Add @DynamoDbAttribute on non-key fields where code name differs from DynamoDB name
            boolean isPk = field.name.equals(pkFieldName);
            boolean isSk = hasSortKey && field.name.equals(skFieldName);
            if (!isPk && !isSk && needsAttributeAnnotation(field, codeName)) {
                fieldBuilder.addAnnotation(dynamoDbAttributeAnnotation(field.name));
            }

            tb.addField(fieldBuilder.build());
        }

        // Generate explicit getter for partition key with @DynamoDbPartitionKey
        String pkGetterName = "get" + cap(pkCodeName);
        ClassName pkType = findFieldType(schema, pkFieldName);
        MethodSpec.Builder pkGetter = MethodSpec.methodBuilder(pkGetterName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(DYNAMO_DB_PARTITION_KEY)
            .returns(pkType)
            .addStatement("return $L", pkCodeName);

        // Add @DynamoDbAttribute on PK getter if code name differs
        if (!pkCodeName.equals(pkFieldName)) {
            pkGetter.addAnnotation(dynamoDbAttributeAnnotation(pkFieldName));
        }
        tb.addMethod(pkGetter.build());

        // Generate explicit getter for sort key with @DynamoDbSortKey (if defined)
        if (hasSortKey) {
            String skGetterName = "get" + cap(skCodeName);
            ClassName skType = findFieldType(schema, skFieldName);
            MethodSpec.Builder skGetter = MethodSpec.methodBuilder(skGetterName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(DYNAMO_DB_SORT_KEY)
                .returns(skType)
                .addStatement("return $L", skCodeName);

            if (!skCodeName.equals(skFieldName)) {
                skGetter.addAnnotation(dynamoDbAttributeAnnotation(skFieldName));
            }
            tb.addMethod(skGetter.build());
        }

        JavaFile.builder(pkg, tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Find the type of a field by name in the schema.
     */
    private ClassName findFieldType(BprintSchema schema, String fieldName) {
        for (BprintSchema.Field field : schema.fields) {
            if (field.name.equals(fieldName)) {
                return mapType(field.type);
            }
        }
        return ClassName.get(String.class);
    }

    // =========================================================================
    // Keys Helper Generation
    // =========================================================================

    /**
     * Generate key constants helper.
     * 
     * PARTITION_KEY_FIELD and SORT_KEY_FIELD constants contain the original
     * DynamoDB attribute names (used for queries). Method parameter names
     * use the resolved Java code names for readability.
     */
    private void generateEntityKeys(BprintSchema schema, String entityName, String pkg, Path outDir) throws IOException {
        String keysClassName = entityName + "Keys";
        
        String pkFieldName = schema.primaryKey.partitionKey;
        String skFieldName = schema.primaryKey.sortKey;
        boolean hasSortKey = skFieldName != null && !skFieldName.isEmpty();

        String pkCodeName = resolveKeyCodeName(schema, pkFieldName);
        String skCodeName = hasSortKey ? resolveKeyCodeName(schema, skFieldName) : null;

        TypeSpec.Builder tb = TypeSpec.classBuilder(keysClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addJavadoc("Key constants for $L entity.\n", entityName)
            .addJavadoc("Partition key: $L\n", pkFieldName)
            .addJavadoc(hasSortKey ? "Sort key: $L\n" : "No sort key defined.\n", skFieldName);

        // Private constructor (utility class)
        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .build());

        // PARTITION_KEY_FIELD constant - uses original DynamoDB attribute name
        tb.addField(FieldSpec.builder(String.class, "PARTITION_KEY_FIELD", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", pkFieldName)
            .addJavadoc("The DynamoDB attribute name used as partition key.\n")
            .build());

        // SORT_KEY_FIELD constant (if defined) - uses original DynamoDB attribute name
        if (hasSortKey) {
            tb.addField(FieldSpec.builder(String.class, "SORT_KEY_FIELD", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", skFieldName)
                .addJavadoc("The DynamoDB attribute name used as sort key.\n")
                .build());
        }

        // key() method - parameter names use resolved code names for readability
        if (hasSortKey) {
            tb.addMethod(MethodSpec.methodBuilder("key")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("Build a Key object for DynamoDB operations.\n")
                .addJavadoc("@param $L partition key value\n", pkCodeName)
                .addJavadoc("@param $L sort key value\n", skCodeName)
                .addParameter(String.class, pkCodeName)
                .addParameter(String.class, skCodeName)
                .returns(KEY)
                .addStatement("return $T.builder()\n.partitionValue($L)\n.sortValue($L)\n.build()", 
                    KEY, pkCodeName, skCodeName)
                .build());
        } else {
            tb.addMethod(MethodSpec.methodBuilder("key")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addJavadoc("Build a Key object for DynamoDB operations.\n")
                .addJavadoc("@param $L partition key value\n", pkCodeName)
                .addParameter(String.class, pkCodeName)
                .returns(KEY)
                .addStatement("return $T.builder()\n.partitionValue($L)\n.build()", 
                    KEY, pkCodeName)
                .build());
        }

        JavaFile.builder(pkg + ".keys", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    // =========================================================================
    // Repository Generation
    // =========================================================================

    /**
     * Generate entity-specific repository with key-based operations.
     * Uses resolved code names for method parameter names.
     */
    private void generateRepository(BprintSchema schema, String entityName, String pkg, Path outDir) throws IOException {
        String repoClassName = entityName + "Repository";
        String pkFieldName = schema.primaryKey.partitionKey;
        String skFieldName = schema.primaryKey.sortKey;
        boolean hasSortKey = skFieldName != null && !skFieldName.isEmpty();

        String pkCodeName = resolveKeyCodeName(schema, pkFieldName);
        String skCodeName = hasSortKey ? resolveKeyCodeName(schema, skFieldName) : null;
        
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

        // Constructor for DI/testing
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

        // findByKey() and deleteByKey() - use resolved code names for parameter names
        if (hasSortKey) {
            tb.addMethod(MethodSpec.methodBuilder("findByKey")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Find entity by partition key and sort key.\n")
                .addParameter(String.class, pkCodeName)
                .addParameter(String.class, skCodeName)
                .returns(optionalEntity)
                .addStatement("$T key = $T.key($L, $L)", KEY, keysClass, pkCodeName, skCodeName)
                .addStatement("return $T.ofNullable(table.getItem(key))", ClassName.get("java.util", "Optional"))
                .build());

            tb.addMethod(MethodSpec.methodBuilder("deleteByKey")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Delete entity by partition key and sort key.\n")
                .addParameter(String.class, pkCodeName)
                .addParameter(String.class, skCodeName)
                .addStatement("$T key = $T.key($L, $L)", KEY, keysClass, pkCodeName, skCodeName)
                .addStatement("table.deleteItem(key)")
                .build());
        } else {
            tb.addMethod(MethodSpec.methodBuilder("findByKey")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Find entity by partition key.\n")
                .addParameter(String.class, pkCodeName)
                .returns(optionalEntity)
                .addStatement("$T key = $T.key($L)", KEY, keysClass, pkCodeName)
                .addStatement("return $T.ofNullable(table.getItem(key))", ClassName.get("java.util", "Optional"))
                .build());

            tb.addMethod(MethodSpec.methodBuilder("deleteByKey")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Delete entity by partition key.\n")
                .addParameter(String.class, pkCodeName)
                .addStatement("$T key = $T.key($L)", KEY, keysClass, pkCodeName)
                .addStatement("table.deleteItem(key)")
                .build());
        }

        JavaFile.builder(pkg + ".repository", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    // =========================================================================
    // Shared Infrastructure Generation (unchanged)
    // =========================================================================

    /**
     * Generate DI-friendly DynamoDB client wrapper with builder pattern.
     */
    private void generateChaimDynamoDbClient(String pkg, Path outDir) throws IOException {
        TypeSpec.Builder tb = TypeSpec.classBuilder("ChaimDynamoDbClient")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("DI-friendly DynamoDB Enhanced Client wrapper.\n")
            .addJavadoc("Supports builder pattern, endpoint override, and client injection.\n");

        tb.addField(FieldSpec.builder(DYNAMO_DB_ENHANCED_CLIENT, "enhancedClient", Modifier.PRIVATE, Modifier.FINAL).build());
        tb.addField(FieldSpec.builder(String.class, "tableName", Modifier.PRIVATE, Modifier.FINAL).build());

        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(DYNAMO_DB_ENHANCED_CLIENT, "enhancedClient")
            .addParameter(String.class, "tableName")
            .addStatement("this.enhancedClient = enhancedClient")
            .addStatement("this.tableName = tableName")
            .build());

        tb.addMethod(MethodSpec.methodBuilder("getEnhancedClient")
            .addModifiers(Modifier.PUBLIC)
            .returns(DYNAMO_DB_ENHANCED_CLIENT)
            .addStatement("return enhancedClient")
            .build());

        tb.addMethod(MethodSpec.methodBuilder("getTableName")
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return tableName")
            .build());

        ClassName builderClass = ClassName.get(pkg + ".client", "ChaimDynamoDbClient", "Builder");
        tb.addMethod(MethodSpec.methodBuilder("builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Create a builder for configuration.\n")
            .returns(builderClass)
            .addStatement("return new Builder()")
            .build());

        ClassName clientClass = ClassName.get(pkg + ".client", "ChaimDynamoDbClient");
        tb.addMethod(MethodSpec.methodBuilder("wrap")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Wrap an existing EnhancedClient (for testing/DI).\n")
            .addParameter(DYNAMO_DB_ENHANCED_CLIENT, "client")
            .addParameter(String.class, "tableName")
            .returns(clientClass)
            .addStatement("return new $T(client, tableName)", clientClass)
            .build());

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

        tb.addField(FieldSpec.builder(String.class, "TABLE_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.tableName())
            .build());

        tb.addField(FieldSpec.builder(String.class, "TABLE_ARN", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.tableArn())
            .build());

        tb.addField(FieldSpec.builder(String.class, "REGION", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.region())
            .build());

        tb.addField(FieldSpec.builder(clientClass, "sharedClient", Modifier.PRIVATE, Modifier.STATIC, Modifier.VOLATILE)
            .build());

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

        tb.addMethod(MethodSpec.methodBuilder("clientBuilder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Create a custom client builder (for testing or custom config).\n")
            .returns(builderClass)
            .addStatement("return $T.builder()\n.tableName(TABLE_NAME)\n.region(REGION)", clientClass)
            .build());

        for (String entityName : entityNames) {
            String methodName = uncap(entityName) + "Repository";
            ClassName repoClass = ClassName.get(pkg + ".repository", entityName + "Repository");

            tb.addMethod(MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(repoClass)
                .addStatement("return new $T(getClient())", repoClass)
                .build());

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

    // =========================================================================
    // Utility Methods
    // =========================================================================

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
