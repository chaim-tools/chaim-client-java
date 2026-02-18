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
import java.util.stream.Collectors;

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
 * Supports collection types: list, map, stringSet, numberSet.
 * For list-of-map and standalone map fields, generates inner @DynamoDbBean classes.
 * 
 * Generates:
 * - Entity DTOs with DynamoDB Enhanced Client annotations on schema-defined keys
 * - Key constants helper ({Entity}Keys.java) with field name references
 * - Entity-specific repositories with key-based operations (with auto-validation on save)
 * - GSI/LSI query methods in repositories when index metadata is available
 * - Shared DI-friendly DynamoDB client (ChaimDynamoDbClient)
 * - Configuration with repository factory methods (ChaimConfig)
 * - Shared ChaimValidationException for structured field-level errors
 * - Per-entity {Entity}Validator with constraint checks from .bprint schema
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
    
    /**
     * Per-field descriptor used when generating plain-Java boilerplate
     * (constructors, setters, Builder, equals/hashCode/toString).
     */
    private record PlainField(
        String codeName,
        TypeName type,
        Object defaultValue,    // null → no initializer
        String bprintType,      // used by formatDefaultInitializer
        boolean isEnumType      // true → emit EnumType.VALUE initializer
    ) {}

    // AWS SDK class names
    private static final ClassName DYNAMO_DB_ENHANCED_CLIENT = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "DynamoDbEnhancedClient");
    private static final ClassName DYNAMO_DB_TABLE = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "DynamoDbTable");
    private static final ClassName TABLE_SCHEMA = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "TableSchema");
    private static final ClassName KEY = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "Key");
    private static final ClassName DYNAMO_DB_INDEX = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb", "DynamoDbIndex");
    private static final ClassName QUERY_CONDITIONAL = ClassName.get(
        "software.amazon.awssdk.enhanced.dynamodb.model", "QueryConditional");
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
     * @param tableMetadata Table metadata (name, ARN, region, GSI/LSI info)
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

        // 2. Generate shared validation exception ONCE
        generateChaimValidationException(pkg, outDir);

        // 3. Generate entity + keys + validator + repository for each schema
        for (BprintSchema schema : schemas) {
            String entityName = deriveEntityName(schema);
            generateEntity(schema, entityName, pkg, outDir);
            generateEntityKeys(schema, entityName, pkg, outDir, tableMetadata);
            generateValidator(schema, entityName, pkg, outDir);
            if (tableMetadata != null) {
                generateRepository(schema, entityName, pkg, outDir, tableMetadata);
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
     * 
     * For collection types (list-of-map, standalone map), generates inner
     * @DynamoDbBean static classes to represent the nested structure.
     */
    private void generateEntity(BprintSchema schema, String entityName, String pkg, Path outDir) throws IOException {
        String pkFieldName = schema.primaryKey.partitionKey;
        String skFieldName = schema.primaryKey.sortKey;
        boolean hasSortKey = skFieldName != null && !skFieldName.isEmpty();

        String pkCodeName = resolveKeyCodeName(schema, pkFieldName);
        String skCodeName = hasSortKey ? resolveKeyCodeName(schema, skFieldName) : null;

        ClassName entityClass = ClassName.get(pkg, entityName);

        // Generate standalone enum files for top-level string fields that carry enum values.
        // Named {EntityName}{CapCodeName} (e.g. OrderStatus, OrderCurrency) so they are
        // clearly scoped to the entity and live in the same package.
        for (BprintSchema.Field field : schema.fields) {
            if ("string".equals(field.type) && hasEnumValues(field)) {
                String codeName = resolveCodeName(field);
                String enumName = entityName + cap(codeName);
                generateEnumFile(enumName, field.enumValues, field.description, pkg, outDir);
            }
        }

        TypeSpec.Builder tb = TypeSpec.classBuilder(entityName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(DYNAMO_DB_BEAN);

        // Resolve code names and types for all fields. Map/list-of-map fields write
        // standalone model classes to {pkg}.model and return their ClassName references.
        // String fields with enum values use the generated enum type instead of String.
        record ResolvedField(BprintSchema.Field field, String codeName, TypeName type) {}
        List<ResolvedField> resolvedFields = new ArrayList<>();
        for (BprintSchema.Field field : schema.fields) {
            String codeName = resolveCodeName(field);
            TypeName type;
            if ("string".equals(field.type) && hasEnumValues(field)) {
                String enumName = entityName + cap(codeName);
                type = ClassName.get(pkg, enumName);
            } else {
                type = mapFieldType(field, pkg, outDir);
            }
            resolvedFields.add(new ResolvedField(field, codeName, type));
        }

        // Add all fields. Default values are emitted as inline field initializers —
        // no @Builder.Default needed since we generate the Builder explicitly.
        List<PlainField> plainFields = new ArrayList<>();
        for (ResolvedField rf : resolvedFields) {
            FieldSpec.Builder fieldBuilder = FieldSpec.builder(rf.type, rf.codeName, Modifier.PRIVATE);

            if (rf.field.description != null && !rf.field.description.isEmpty()) {
                fieldBuilder.addJavadoc("$L\n", rf.field.description);
            }

            boolean isEnumType = "string".equals(rf.field.type) && hasEnumValues(rf.field);
            if (rf.field.defaultValue != null) {
                if (isEnumType) {
                    fieldBuilder.initializer("$T.$L", rf.type, rf.field.defaultValue.toString());
                } else {
                    fieldBuilder.initializer(formatDefaultInitializer(rf.field.type, rf.field.defaultValue));
                }
            }

            tb.addField(fieldBuilder.build());
            plainFields.add(new PlainField(rf.codeName, rf.type, rf.field.defaultValue, rf.field.type, isEnumType));
        }

        // Constructors (no-arg required by DynamoDB Enhanced Client; all-args for the Builder)
        addConstructors(tb, entityName, plainFields);

        // Getters — PK and SK carry DynamoDB key annotations; all others are plain getters
        // with @DynamoDbAttribute only when the code name differs from the DDB attribute name.
        String pkGetterName = "get" + cap(pkCodeName);
        TypeName pkType = resolvedFields.stream()
            .filter(rf -> rf.field.name.equals(pkFieldName))
            .map(rf -> rf.type)
            .findFirst().orElse(ClassName.get(String.class));
        MethodSpec.Builder pkGetter = MethodSpec.methodBuilder(pkGetterName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(DYNAMO_DB_PARTITION_KEY)
            .returns(pkType)
            .addStatement("return $L", pkCodeName);
        if (!pkCodeName.equals(pkFieldName)) {
            pkGetter.addAnnotation(dynamoDbAttributeAnnotation(pkFieldName));
        }
        tb.addMethod(pkGetter.build());

        if (hasSortKey) {
            String skGetterName = "get" + cap(skCodeName);
            TypeName skType = resolvedFields.stream()
                .filter(rf -> rf.field.name.equals(skFieldName))
                .map(rf -> rf.type)
                .findFirst().orElse(ClassName.get(String.class));
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

        for (ResolvedField rf : resolvedFields) {
            boolean isPk = rf.field.name.equals(pkFieldName);
            boolean isSk = hasSortKey && rf.field.name.equals(skFieldName);
            if (isPk || isSk) {
                continue;
            }
            MethodSpec.Builder getter = MethodSpec.methodBuilder("get" + cap(rf.codeName))
                .addModifiers(Modifier.PUBLIC)
                .returns(rf.type)
                .addStatement("return $L", rf.codeName);
            if (needsAttributeAnnotation(rf.field, rf.codeName)) {
                getter.addAnnotation(dynamoDbAttributeAnnotation(rf.field.name));
            }
            tb.addMethod(getter.build());
        }

        // Setters (required for mutable DynamoDB beans), Builder, equals/hashCode/toString
        addSetters(tb, plainFields);
        addBuilderClass(tb, entityName, plainFields);
        addEqualsHashCodeToString(tb, entityName, plainFields);

        JavaFile.builder(pkg, tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
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
     * 
     * Also generates INDEX_ constants for each GSI/LSI.
     */
    private void generateEntityKeys(BprintSchema schema, String entityName, String pkg, Path outDir, TableMetadata tableMetadata) throws IOException {
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

        // INDEX_ constants for GSIs
        if (tableMetadata != null && tableMetadata.globalSecondaryIndexes() != null) {
            for (TableMetadata.GSIMetadata gsi : tableMetadata.globalSecondaryIndexes()) {
                String constName = "INDEX_" + toConstantCase(gsi.indexName());
                tb.addField(FieldSpec.builder(String.class, constName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", gsi.indexName())
                    .addJavadoc("GSI index name: $L\n", gsi.indexName())
                    .build());
            }
        }

        // INDEX_ constants for LSIs
        if (tableMetadata != null && tableMetadata.localSecondaryIndexes() != null) {
            for (TableMetadata.LSIMetadata lsi : tableMetadata.localSecondaryIndexes()) {
                String constName = "INDEX_" + toConstantCase(lsi.indexName());
                tb.addField(FieldSpec.builder(String.class, constName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", lsi.indexName())
                    .addJavadoc("LSI index name: $L\n", lsi.indexName())
                    .build());
            }
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
     * Generates queryBy methods for each GSI/LSI.
     */
    private void generateRepository(BprintSchema schema, String entityName, String pkg, Path outDir, TableMetadata tableMetadata) throws IOException {
        String repoClassName = entityName + "Repository";
        String pkFieldName = schema.primaryKey.partitionKey;
        String skFieldName = schema.primaryKey.sortKey;
        boolean hasSortKey = skFieldName != null && !skFieldName.isEmpty();

        String pkCodeName = resolveKeyCodeName(schema, pkFieldName);
        String skCodeName = hasSortKey ? resolveKeyCodeName(schema, skFieldName) : null;
        
        ClassName entityClass = ClassName.get(pkg, entityName);
        ClassName keysClass = ClassName.get(pkg + ".keys", entityName + "Keys");
        ClassName clientClass = ClassName.get(pkg + ".client", "ChaimDynamoDbClient");
        ClassName validatorClass = ClassName.get(pkg + ".validation", entityName + "Validator");
        ParameterizedTypeName tableType = ParameterizedTypeName.get(DYNAMO_DB_TABLE, entityClass);
        ParameterizedTypeName optionalEntity = ParameterizedTypeName.get(
            ClassName.get("java.util", "Optional"), entityClass);
        ParameterizedTypeName listOfEntity = ParameterizedTypeName.get(
            ClassName.get("java.util", "List"), entityClass);

        TypeSpec.Builder tb = TypeSpec.classBuilder(repoClassName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Repository for $L entity with key-based operations.\n", entityName)
            .addJavadoc("Partition key: $L\n", pkFieldName)
            .addJavadoc(hasSortKey ? "Sort key: $L\n" : "No sort key.\n", skFieldName)
            .addJavadoc("Validates constraints before save. No scan operations by default.\n");

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

        // save() method - validates constraints before persisting
        tb.addMethod(MethodSpec.methodBuilder("save")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Save entity to DynamoDB. Validates constraints before persisting.\n")
            .addParameter(entityClass, "entity")
            .addStatement("$T.validate(entity)", validatorClass)
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

        // Generate query methods for GSIs
        if (tableMetadata.globalSecondaryIndexes() != null) {
            for (TableMetadata.GSIMetadata gsi : tableMetadata.globalSecondaryIndexes()) {
                addIndexQueryMethods(tb, gsi.indexName(), gsi.partitionKey(), gsi.sortKey(),
                    entityClass, listOfEntity, schema);
            }
        }

        // Generate query methods for LSIs (LSIs share the table's partition key)
        if (tableMetadata.localSecondaryIndexes() != null) {
            for (TableMetadata.LSIMetadata lsi : tableMetadata.localSecondaryIndexes()) {
                addIndexQueryMethods(tb, lsi.indexName(), pkFieldName, lsi.sortKey(),
                    entityClass, listOfEntity, schema);
            }
        }

        JavaFile.builder(pkg + ".repository", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Add queryBy{IndexName} methods for a GSI or LSI.
     * Generates a PK-only query method, plus an overloaded PK+SK method if the index has a sort key.
     * Uses the field's actual Java type for each key parameter (String, Double, or Instant)
     * so the generated API is type-safe. Timestamp parameters are converted to their ISO-8601
     * string representation before being passed to Key.Builder (which accepts String values).
     */
    private void addIndexQueryMethods(TypeSpec.Builder tb, String indexName, String partitionKey,
            String sortKey, ClassName entityClass, ParameterizedTypeName listOfEntity,
            BprintSchema schema) {
        String methodName = "queryBy" + cap(toCamelCase(indexName));
        String pkParamName = toCamelCase(partitionKey);
        ParameterizedTypeName indexType = ParameterizedTypeName.get(DYNAMO_DB_INDEX, entityClass);

        TypeName pkParamType = resolveKeyParamType(partitionKey, schema);
        String pkKeyExpr = toKeyExpression(pkParamName, partitionKey, schema);

        // PK-only query
        tb.addMethod(MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Query $L index by partition key.\n", indexName)
            .addParameter(pkParamType, pkParamName)
            .returns(listOfEntity)
            .addStatement("$T index = table.index($S)", indexType, indexName)
            .addStatement("$T condition = $T.keyEqualTo($T.builder().partitionValue($L).build())",
                QUERY_CONDITIONAL, QUERY_CONDITIONAL, KEY, pkKeyExpr)
            .addStatement("$T<$T> results = new $T<>()",
                ClassName.get("java.util", "List"), entityClass, ClassName.get("java.util", "ArrayList"))
            .addStatement("index.query(condition).forEach(page -> results.addAll(page.items()))")
            .addStatement("return results")
            .build());

        // PK+SK query (if sort key exists)
        if (sortKey != null && !sortKey.isEmpty()) {
            String skParamName = toCamelCase(sortKey);
            TypeName skParamType = resolveKeyParamType(sortKey, schema);
            String skKeyExpr = toKeyExpression(skParamName, sortKey, schema);
            tb.addMethod(MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Query $L index by partition key and sort key.\n", indexName)
                .addParameter(pkParamType, pkParamName)
                .addParameter(skParamType, skParamName)
                .returns(listOfEntity)
                .addStatement("$T index = table.index($S)", indexType, indexName)
                .addStatement("$T condition = $T.keyEqualTo($T.builder().partitionValue($L).sortValue($L).build())",
                    QUERY_CONDITIONAL, QUERY_CONDITIONAL, KEY, pkKeyExpr, skKeyExpr)
                .addStatement("$T<$T> results = new $T<>()",
                    ClassName.get("java.util", "List"), entityClass, ClassName.get("java.util", "ArrayList"))
                .addStatement("index.query(condition).forEach(page -> results.addAll(page.items()))")
                .addStatement("return results")
                .build());
        }
    }

    /**
     * Resolve the Java parameter type for a DynamoDB key field by looking it up in the schema.
     * Returns String for string/unknown fields, Double for number fields, Instant for timestamp fields.
     * DynamoDB key attributes are almost always strings, but timestamps and numbers are valid too.
     */
    private static TypeName resolveKeyParamType(String fieldName, BprintSchema schema) {
        if (schema != null && schema.fields != null) {
            for (BprintSchema.Field f : schema.fields) {
                if (fieldName.equals(f.name)) {
                    return mapScalarType(f.type);
                }
            }
        }
        return ClassName.get(String.class);
    }

    /**
     * Return the Java expression to pass a key parameter to Key.Builder.partitionValue() /
     * sortValue(). String and number fields pass the variable directly. Timestamp (Instant)
     * fields must be converted to ISO-8601 string first because Key.Builder only accepts
     * String or Number, not Instant.
     */
    private static String toKeyExpression(String paramName, String fieldName, BprintSchema schema) {
        if (schema != null && schema.fields != null) {
            for (BprintSchema.Field f : schema.fields) {
                if (fieldName.equals(f.name) && "timestamp".equals(f.type)) {
                    return paramName + ".toString()";
                }
            }
        }
        return paramName;
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

        // TABLE_ARN is an unresolved CDK token during local synth (before cdk deploy).
        // Emit the raw string as a compile-time placeholder and read the real ARN from the
        // CHAIM_TABLE_ARN environment variable at runtime if it is set.
        String rawArn = tableMetadata.tableArn() != null ? tableMetadata.tableArn() : "";
        boolean arnIsToken = rawArn.startsWith("${Token[") || rawArn.startsWith("arn:aws") == false && rawArn.contains("Token");
        if (arnIsToken) {
            tb.addField(FieldSpec.builder(String.class, "TABLE_ARN", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addJavadoc("Table ARN. Resolved from CHAIM_TABLE_ARN env var at runtime; "
                    + "compile-time value is a CDK placeholder and will not be valid until after cdk deploy.\n")
                .initializer("$T.getenv($S) != null ? $T.getenv($S) : $S",
                    System.class, "CHAIM_TABLE_ARN",
                    System.class, "CHAIM_TABLE_ARN",
                    rawArn)
                .build());
        } else {
            tb.addField(FieldSpec.builder(String.class, "TABLE_ARN", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", rawArn)
                .build());
        }

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
    // Validation Generation
    // =========================================================================

    /**
     * Generate the shared ChaimValidationException class.
     * Contains a list of FieldError records with field name, constraint type, and message.
     * Generated once per table into the validation sub-package.
     */
    private void generateChaimValidationException(String pkg, Path outDir) throws IOException {
        ClassName fieldErrorClass = ClassName.get(pkg + ".validation", "ChaimValidationException", "FieldError");
        ParameterizedTypeName listOfFieldError = ParameterizedTypeName.get(
            ClassName.get("java.util", "List"), fieldErrorClass);

        // Build FieldError inner class
        TypeSpec.Builder fieldErrorBuilder = TypeSpec.classBuilder("FieldError")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addField(FieldSpec.builder(String.class, "fieldName", Modifier.PRIVATE, Modifier.FINAL).build())
            .addField(FieldSpec.builder(String.class, "constraint", Modifier.PRIVATE, Modifier.FINAL).build())
            .addField(FieldSpec.builder(String.class, "message", Modifier.PRIVATE, Modifier.FINAL).build());

        fieldErrorBuilder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(String.class, "fieldName")
            .addParameter(String.class, "constraint")
            .addParameter(String.class, "message")
            .addStatement("this.fieldName = fieldName")
            .addStatement("this.constraint = constraint")
            .addStatement("this.message = message")
            .build());

        fieldErrorBuilder.addMethod(MethodSpec.methodBuilder("getFieldName")
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return fieldName")
            .build());

        fieldErrorBuilder.addMethod(MethodSpec.methodBuilder("getConstraint")
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return constraint")
            .build());

        fieldErrorBuilder.addMethod(MethodSpec.methodBuilder("getMessage")
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return message")
            .build());

        fieldErrorBuilder.addMethod(MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return fieldName + $S + message", ": ")
            .build());

        // Build main exception class
        TypeSpec.Builder tb = TypeSpec.classBuilder("ChaimValidationException")
            .addModifiers(Modifier.PUBLIC)
            .superclass(RuntimeException.class)
            .addJavadoc("Validation exception with structured field-level errors.\n")
            .addJavadoc("Collects all constraint violations before throwing.\n");

        tb.addField(FieldSpec.builder(listOfFieldError, "errors", Modifier.PRIVATE, Modifier.FINAL).build());

        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(String.class, "entityName")
            .addParameter(listOfFieldError, "errors")
            .addStatement("super(entityName + $S + errors.size() + $S)", " validation failed: ", " error(s)")
            .addStatement("this.errors = $T.unmodifiableList(errors)", ClassName.get("java.util", "Collections"))
            .build());

        tb.addMethod(MethodSpec.methodBuilder("getErrors")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Get the list of field-level validation errors.\n")
            .returns(listOfFieldError)
            .addStatement("return errors")
            .build());

        tb.addType(fieldErrorBuilder.build());

        JavaFile.builder(pkg + ".validation", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Generate a per-entity Validator class with constraint checks.
     * The validator is a utility class with a static validate() method that
     * checks required fields, constraints, and enum values defined in the
     * .bprint schema. Throws ChaimValidationException with all violations collected.
     * Collection-type fields are skipped for constraint/enum checks.
     */
    private void generateValidator(BprintSchema schema, String entityName, String pkg, Path outDir) throws IOException {
        String validatorClassName = entityName + "Validator";
        ClassName entityClass = ClassName.get(pkg, entityName);
        ClassName exceptionClass = ClassName.get(pkg + ".validation", "ChaimValidationException");
        ClassName fieldErrorClass = ClassName.get(pkg + ".validation", "ChaimValidationException", "FieldError");
        ParameterizedTypeName listOfFieldError = ParameterizedTypeName.get(
            ClassName.get("java.util", "List"), fieldErrorClass);

        // Check if any fields need validation (required, constraints, or enum) - skip collection type constraints
        boolean needsValidation = false;
        for (BprintSchema.Field field : schema.fields) {
            if (field.required || (!isCollectionType(field.type) && (hasFieldConstraints(field) || hasEnumValues(field)))) {
                needsValidation = true;
                break;
            }
        }

        MethodSpec.Builder validateMethod = MethodSpec.methodBuilder("validate")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Validate entity against .bprint schema rules (required, constraints, enum).\n")
            .addJavadoc("@param entity the entity to validate\n")
            .addJavadoc("@throws ChaimValidationException if any validations fail\n")
            .addParameter(entityClass, "entity");

        if (needsValidation) {
            validateMethod.addStatement("$T errors = new $T<>()", listOfFieldError,
                ClassName.get("java.util", "ArrayList"));

            for (BprintSchema.Field field : schema.fields) {
                boolean isRequired = field.required;
                boolean isCollection = isCollectionType(field.type);
                boolean hasConstraints = !isCollection && hasFieldConstraints(field);
                boolean hasEnum = !isCollection && hasEnumValues(field);

                if (!isRequired && !hasConstraints && !hasEnum) continue;

                String codeName = resolveCodeName(field);
                String getterName = "get" + cap(codeName);
                String originalName = field.name;

                // Required null-check (runs before constraint/enum checks)
                if (isRequired) {
                    validateMethod.beginControlFlow("if (entity.$L() == null)", getterName)
                        .addStatement("errors.add(new $T($S, $S, $S))",
                            fieldErrorClass, originalName, "required", "is required but was null")
                        .endControlFlow();
                }

                // Constraint checks (null-safe) - not applicable to collection types.
                // String constraints (length, pattern) are also skipped for enum fields —
                // the enum type makes them redundant.
                if (hasConstraints && !hasEnumValues(field)) {
                    BprintSchema.Constraints c = field.constraints;
                    if ("string".equals(field.type)) {
                        addStringConstraintChecks(validateMethod, getterName, originalName, c, fieldErrorClass);
                    } else if ("number".equals(field.type)) {
                        addNumberConstraintChecks(validateMethod, getterName, originalName, c, fieldErrorClass);
                    }
                }

                // Enum fields use a Java enum type — the type system and DynamoDB Enhanced
                // Client enforce valid values at compile/serialization time. String-set
                // validation is therefore no longer needed here.
            }

            validateMethod.beginControlFlow("if (!errors.isEmpty())")
                .addStatement("throw new $T($S, errors)", exceptionClass, entityName)
                .endControlFlow();
        }

        TypeSpec.Builder tb = TypeSpec.classBuilder(validatorClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addJavadoc("Validator for $L entity.\n", entityName)
            .addJavadoc("Checks required fields, constraints, and enum values from the .bprint schema.\n");

        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .build());

        tb.addMethod(validateMethod.build());

        JavaFile.builder(pkg + ".validation", tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Check if a field has any active constraints.
     */
    private static boolean hasFieldConstraints(BprintSchema.Field field) {
        if (field.constraints == null) return false;
        BprintSchema.Constraints c = field.constraints;
        return c.minLength != null || c.maxLength != null || c.pattern != null
            || c.min != null || c.max != null;
    }

    /**
     * Add string constraint validation checks (minLength, maxLength, pattern) to the validate method.
     */
    private void addStringConstraintChecks(MethodSpec.Builder method, String getterName,
            String originalName, BprintSchema.Constraints c, ClassName fieldErrorClass) {
        method.beginControlFlow("if (entity.$L() != null)", getterName);

        if (c.minLength != null) {
            method.beginControlFlow("if (entity.$L().length() < $L)", getterName, c.minLength)
                .addStatement("errors.add(new $T($S, $S, $S + entity.$L().length()))",
                    fieldErrorClass, originalName, "minLength",
                    "must have minimum length " + c.minLength + ", got ", getterName)
                .endControlFlow();
        }

        if (c.maxLength != null) {
            method.beginControlFlow("if (entity.$L().length() > $L)", getterName, c.maxLength)
                .addStatement("errors.add(new $T($S, $S, $S + entity.$L().length()))",
                    fieldErrorClass, originalName, "maxLength",
                    "must have maximum length " + c.maxLength + ", got ", getterName)
                .endControlFlow();
        }

        if (c.pattern != null) {
            method.beginControlFlow("if (!entity.$L().matches($S))", getterName, c.pattern)
                .addStatement("errors.add(new $T($S, $S, $S))",
                    fieldErrorClass, originalName, "pattern",
                    "must match pattern '" + c.pattern + "'")
                .endControlFlow();
        }

        method.endControlFlow();
    }

    /**
     * Add number constraint validation checks (min, max) to the validate method.
     */
    private void addNumberConstraintChecks(MethodSpec.Builder method, String getterName,
            String originalName, BprintSchema.Constraints c, ClassName fieldErrorClass) {
        method.beginControlFlow("if (entity.$L() != null)", getterName);

        if (c.min != null) {
            method.beginControlFlow("if (entity.$L() < $L)", getterName, c.min)
                .addStatement("errors.add(new $T($S, $S, $S + entity.$L()))",
                    fieldErrorClass, originalName, "min",
                    "must be >= " + c.min + ", got ", getterName)
                .endControlFlow();
        }

        if (c.max != null) {
            method.beginControlFlow("if (entity.$L() > $L)", getterName, c.max)
                .addStatement("errors.add(new $T($S, $S, $S + entity.$L()))",
                    fieldErrorClass, originalName, "max",
                    "must be <= " + c.max + ", got ", getterName)
                .endControlFlow();
        }

        method.endControlFlow();
    }

    /**
     * Add enum allowed-value validation check to the validate method.
     */
    private void addEnumValidationCheck(MethodSpec.Builder method, String getterName,
            String originalName, List<String> enumValues, ClassName fieldErrorClass) {
        method.beginControlFlow("if (entity.$L() != null)", getterName);

        // Build Set.of(...) with all allowed values
        StringBuilder setArgs = new StringBuilder();
        for (int i = 0; i < enumValues.size(); i++) {
            if (i > 0) setArgs.append(", ");
            setArgs.append("$S");
        }

        String allowedList = String.join(", ", enumValues);
        Object[] args = new Object[enumValues.size() + 2];
        args[0] = fieldErrorClass;
        args[1] = originalName;
        System.arraycopy(enumValues.toArray(), 0, args, 2, enumValues.size());

        // Build the if-check with Set.of(...)
        CodeBlock.Builder setOfBlock = CodeBlock.builder();
        setOfBlock.add("$T.of(", ClassName.get("java.util", "Set"));
        for (int i = 0; i < enumValues.size(); i++) {
            if (i > 0) setOfBlock.add(", ");
            setOfBlock.add("$S", enumValues.get(i));
        }
        setOfBlock.add(")");

        method.beginControlFlow("if (!$L.contains(entity.$L()))", setOfBlock.build(), getterName)
            .addStatement("errors.add(new $T($S, $S, $S + entity.$L()))",
                fieldErrorClass, originalName, "enum",
                "must be one of [" + allowedList + "], got ", getterName)
            .endControlFlow();

        method.endControlFlow();
    }

    // =========================================================================
    // Type Mapping
    // =========================================================================

    /**
     * Check if a type is a collection type (list, map, stringSet, numberSet).
     */
    private static boolean isCollectionType(String type) {
        return "list".equals(type) || "map".equals(type)
            || "stringSet".equals(type) || "numberSet".equals(type);
    }

    /**
     * Map a field to its Java type, handling both scalar and collection types.
     * For list-of-map and standalone map, writes standalone {@code @DynamoDbBean}
     * classes to the {@code {pkg}.model} sub-package.
     *
     * @param field  The bprint field
     * @param pkg    Root entity package (model classes go in {@code pkg.model})
     * @param outDir Output root for JavaPoet
     * @return The Java TypeName for this field
     */
    private TypeName mapFieldType(BprintSchema.Field field, String pkg, Path outDir) throws IOException {
        return switch (field.type) {
            case "list" -> mapListType(field, pkg, outDir);
            case "map" -> mapMapType(field, pkg, outDir);
            case "stringSet" -> ParameterizedTypeName.get(
                ClassName.get("java.util", "Set"), ClassName.get(String.class));
            case "numberSet" -> ParameterizedTypeName.get(
                ClassName.get("java.util", "Set"), ClassName.get(Double.class));
            default -> mapScalarType(field.type);
        };
    }

    /**
     * Map a list field to its Java type.
     * For list-of-scalars: {@code List<String>}, {@code List<Double>}, etc.
     * For list-of-map: writes a standalone model class and returns {@code List<ModelClass>}.
     *
     * @param field  The bprint field
     * @param pkg    Root entity package
     * @param outDir Output root for JavaPoet
     * @return The Java TypeName for this field
     */
    private TypeName mapListType(BprintSchema.Field field, String pkg, Path outDir) throws IOException {
        if (field.items == null) {
            return ParameterizedTypeName.get(
                ClassName.get("java.util", "List"), ClassName.get(Object.class));
        }

        if ("map".equals(field.items.type) && field.items.fields != null) {
            String codeName = resolveCodeName(field);
            String innerClassName = cap(codeName) + "Item";
            ClassName innerRef = writeModelClass(innerClassName, field.items.fields, pkg, outDir);
            return ParameterizedTypeName.get(ClassName.get("java.util", "List"), innerRef);
        }

        ClassName elementType = mapScalarType(field.items.type);
        return ParameterizedTypeName.get(ClassName.get("java.util", "List"), elementType);
    }

    /**
     * Map a standalone map field to its Java type.
     * Writes a standalone {@code @DynamoDbBean} model class and returns its ClassName.
     *
     * @param field  The bprint field
     * @param pkg    Root entity package
     * @param outDir Output root for JavaPoet
     * @return The Java TypeName for this field
     */
    private TypeName mapMapType(BprintSchema.Field field, String pkg, Path outDir) throws IOException {
        if (field.fields == null || field.fields.isEmpty()) {
            return ClassName.get(Object.class);
        }

        String codeName = resolveCodeName(field);
        String innerClassName = cap(codeName);
        return writeModelClass(innerClassName, field.fields, pkg, outDir);
    }

    /**
     * Write a standalone {@code @DynamoDbBean} class to the {@code {pkg}.model} sub-package
     * and return its fully-qualified {@link ClassName}.
     *
     * <p>Nested map fields are written as their own files using a parent-prefixed class name to
     * avoid collisions (e.g. {@code ShippingAddress.coordinates} → {@code ShippingAddressCoordinates}).
     * Nested enum fields (string + enumValues) remain as inner enums within their containing class.
     *
     * @param className    Simple class name; outer class name is prepended for deep nesting
     * @param nestedFields Fields to generate
     * @param pkg          Root entity package; the model sub-package is derived by appending {@code .model}
     * @param outDir       Output root for JavaPoet
     * @return The fully-qualified {@link ClassName} of the written class
     */
    private ClassName writeModelClass(String className, List<BprintSchema.NestedField> nestedFields,
            String pkg, Path outDir) throws IOException {
        String modelPkg = pkg + ".model";
        ClassName classRef = ClassName.get(modelPkg, className);

        TypeSpec.Builder tb = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(DYNAMO_DB_BEAN);

        List<PlainField> plainFields = new ArrayList<>();

        for (BprintSchema.NestedField nf : nestedFields) {
            // Resolve Java identifier — mirrors resolveCodeName() for top-level fields
            String codeName;
            if (nf.nameOverride != null && !nf.nameOverride.isEmpty()) {
                codeName = nf.nameOverride;
            } else if (VALID_JAVA_IDENTIFIER.matcher(nf.name).matches()) {
                codeName = nf.name;
            } else {
                codeName = toJavaCamelCase(nf.name);
            }

            TypeName fieldType;
            boolean isEnumType = false;

            if ("map".equals(nf.type) && nf.fields != null && !nf.fields.isEmpty()) {
                // Nested map → separate file in the model package, name-qualified to avoid collisions
                String nestedClassName = className + cap(codeName);
                fieldType = writeModelClass(nestedClassName, nf.fields, pkg, outDir);
            } else if ("list".equals(nf.type) && nf.items != null) {
                if ("map".equals(nf.items.type) && nf.items.fields != null && !nf.items.fields.isEmpty()) {
                    String nestedClassName = className + cap(codeName) + "Item";
                    ClassName nestedRef = writeModelClass(nestedClassName, nf.items.fields, pkg, outDir);
                    fieldType = ParameterizedTypeName.get(ClassName.get("java.util", "List"), nestedRef);
                } else {
                    ClassName elementType = mapScalarType(nf.items.type);
                    fieldType = ParameterizedTypeName.get(ClassName.get("java.util", "List"), elementType);
                }
            } else if ("string".equals(nf.type) && nf.enumValues != null && !nf.enumValues.isEmpty()) {
                // Enum stays as a nested type in the containing class file
                String innerEnumName = cap(codeName);
                TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(innerEnumName)
                    .addModifiers(Modifier.PUBLIC);
                if (nf.description != null && !nf.description.isEmpty()) {
                    enumBuilder.addJavadoc("$L\n", nf.description);
                }
                for (String v : nf.enumValues) {
                    enumBuilder.addEnumConstant(v);
                }
                tb.addType(enumBuilder.build());
                fieldType = classRef.nestedClass(innerEnumName);
                isEnumType = true;
            } else {
                fieldType = mapScalarType(nf.type);
            }

            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, codeName, Modifier.PRIVATE);

            if (nf.description != null && !nf.description.isEmpty()) {
                fieldBuilder.addJavadoc("$L\n", nf.description);
            }

            if (nf.defaultValue != null) {
                if (isEnumType) {
                    fieldBuilder.initializer("$T.$L", classRef.nestedClass(cap(codeName)), nf.defaultValue.toString());
                } else {
                    fieldBuilder.initializer(formatDefaultInitializer(nf.type, nf.defaultValue));
                }
            }

            tb.addField(fieldBuilder.build());
            plainFields.add(new PlainField(codeName, fieldType, nf.defaultValue, nf.type, isEnumType));
        }

        // Constructors
        addConstructors(tb, className, plainFields);

        // Getters — @DynamoDbAttribute on getters where code name differs from DDB attribute name
        for (int i = 0; i < nestedFields.size(); i++) {
            BprintSchema.NestedField nf = nestedFields.get(i);
            PlainField pf = plainFields.get(i);
            MethodSpec.Builder getter = MethodSpec.methodBuilder("get" + cap(pf.codeName))
                .addModifiers(Modifier.PUBLIC)
                .returns(pf.type)
                .addStatement("return $L", pf.codeName);
            if (!pf.codeName.equals(nf.name)) {
                getter.addAnnotation(dynamoDbAttributeAnnotation(nf.name));
            }
            tb.addMethod(getter.build());
        }

        // Setters, Builder, equals/hashCode/toString
        addSetters(tb, plainFields);
        addBuilderClass(tb, className, plainFields);
        addEqualsHashCodeToString(tb, className, plainFields);

        JavaFile.builder(modelPkg, tb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);

        return classRef;
    }

    // =========================================================================
    // Plain-Java Boilerplate Helpers
    // =========================================================================

    /**
     * Emit a no-arg constructor and an all-args constructor (field order matches declaration).
     */
    private static void addConstructors(TypeSpec.Builder tb, String className, List<PlainField> fields) {
        tb.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Default no-arg constructor (required by DynamoDB Enhanced Client).\n")
            .build());

        if (!fields.isEmpty()) {
            MethodSpec.Builder allArgs = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("All-args constructor.\n");
            for (PlainField f : fields) {
                allArgs.addParameter(f.type, f.codeName);
            }
            for (PlainField f : fields) {
                allArgs.addStatement("this.$L = $L", f.codeName, f.codeName);
            }
            tb.addMethod(allArgs.build());
        }
    }

    /**
     * Emit a public setter for every field (required for mutable DynamoDB beans).
     */
    private static void addSetters(TypeSpec.Builder tb, List<PlainField> fields) {
        for (PlainField f : fields) {
            tb.addMethod(MethodSpec.methodBuilder("set" + cap(f.codeName))
                .addModifiers(Modifier.PUBLIC)
                .addParameter(f.type, f.codeName)
                .addStatement("this.$L = $L", f.codeName, f.codeName)
                .build());
        }
    }

    /**
     * Emit a static {@code Builder} inner class and a {@code builder()} factory method.
     * The builder mirrors the Lombok @Builder API so consumer call-sites are unchanged.
     */
    private static void addBuilderClass(TypeSpec.Builder tb, String className, List<PlainField> fields) {
        ClassName builderRef = ClassName.bestGuess("Builder");
        ClassName entityRef = ClassName.bestGuess(className);

        tb.addMethod(MethodSpec.methodBuilder("builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(builderRef)
            .addStatement("return new Builder()")
            .build());

        TypeSpec.Builder builderTb = TypeSpec.classBuilder("Builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        for (PlainField f : fields) {
            FieldSpec.Builder fb = FieldSpec.builder(f.type, f.codeName, Modifier.PRIVATE);
            if (f.defaultValue != null) {
                if (f.isEnumType) {
                    fb.initializer("$T.$L", f.type, f.defaultValue.toString());
                } else {
                    fb.initializer(formatDefaultInitializer(f.bprintType, f.defaultValue));
                }
            }
            builderTb.addField(fb.build());
        }

        for (PlainField f : fields) {
            builderTb.addMethod(MethodSpec.methodBuilder(f.codeName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(f.type, f.codeName)
                .returns(builderRef)
                .addStatement("this.$L = $L", f.codeName, f.codeName)
                .addStatement("return this")
                .build());
        }

        MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC)
            .returns(entityRef);
        if (fields.isEmpty()) {
            buildMethod.addStatement("return new $T()", entityRef);
        } else {
            String argList = fields.stream().map(f -> f.codeName).collect(Collectors.joining(", "));
            buildMethod.addStatement("return new $T($L)", entityRef, argList);
        }
        builderTb.addMethod(buildMethod.build());

        tb.addType(builderTb.build());
    }

    /**
     * Emit {@code equals}, {@code hashCode}, and {@code toString} using {@code java.util.Objects}.
     */
    private static void addEqualsHashCodeToString(TypeSpec.Builder tb, String className, List<PlainField> fields) {
        ClassName objectsClass = ClassName.get("java.util", "Objects");
        ClassName entityRef = ClassName.bestGuess(className);

        // equals()
        MethodSpec.Builder equalsMethod = MethodSpec.methodBuilder("equals")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addParameter(ClassName.get(Object.class), "o");
        equalsMethod.addStatement("if (this == o) return true");
        equalsMethod.addStatement("if (!(o instanceof $T)) return false", entityRef);
        equalsMethod.addStatement("$T that = ($T) o", entityRef, entityRef);
        if (fields.isEmpty()) {
            equalsMethod.addStatement("return true");
        } else {
            StringBuilder condExpr = new StringBuilder("return ");
            List<Object> condArgs = new ArrayList<>();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    condExpr.append("\n    && ");
                }
                condExpr.append("$T.equals($L, that.$L)");
                condArgs.add(objectsClass);
                condArgs.add(fields.get(i).codeName);
                condArgs.add(fields.get(i).codeName);
            }
            equalsMethod.addStatement(condExpr.toString(), condArgs.toArray());
        }
        tb.addMethod(equalsMethod.build());

        // hashCode()
        MethodSpec.Builder hashCodeMethod = MethodSpec.methodBuilder("hashCode")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(int.class);
        if (fields.isEmpty()) {
            hashCodeMethod.addStatement("return 0");
        } else {
            String hashArgs = fields.stream().map(f -> f.codeName).collect(Collectors.joining(", "));
            hashCodeMethod.addStatement("return $T.hash($L)", objectsClass, hashArgs);
        }
        tb.addMethod(hashCodeMethod.build());

        // toString()
        MethodSpec.Builder toStringMethod = MethodSpec.methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get(String.class));
        if (fields.isEmpty()) {
            toStringMethod.addStatement("return $S", className + "{}");
        } else {
            StringBuilder tsExpr = new StringBuilder("return $S");
            List<Object> tsArgs = new ArrayList<>();
            tsArgs.add(className + "{" + fields.get(0).codeName + "=");
            tsExpr.append(" + $L");
            tsArgs.add(fields.get(0).codeName);
            for (int i = 1; i < fields.size(); i++) {
                tsExpr.append(" + $S + $L");
                tsArgs.add(", " + fields.get(i).codeName + "=");
                tsArgs.add(fields.get(i).codeName);
            }
            tsExpr.append(" + $S");
            tsArgs.add("}");
            toStringMethod.addStatement(tsExpr.toString(), tsArgs.toArray());
        }
        tb.addMethod(toStringMethod.build());
    }

    /**
     * Generate a top-level Java enum file for a string field that carries a fixed set of
     * allowed values. The enum is placed in the same package as the entity class.
     *
     * @param enumName   Simple class name (e.g. "OrderStatus")
     * @param values     Enum constant names from the .bprint enumValues array
     * @param description Optional Javadoc string; may be null
     * @param pkg        Java package
     * @param outDir     Output root directory
     */
    private void generateEnumFile(String enumName, List<String> values, String description,
            String pkg, Path outDir) throws IOException {
        TypeSpec.Builder eb = TypeSpec.enumBuilder(enumName)
            .addModifiers(Modifier.PUBLIC);
        if (description != null && !description.isEmpty()) {
            eb.addJavadoc("$L\n", description);
        }
        for (String v : values) {
            eb.addEnumConstant(v);
        }
        JavaFile.builder(pkg, eb.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);
    }

    /**
     * Map a scalar bprint type to its Java ClassName.
     */
    private static ClassName mapScalarType(String type) {
        return switch (type) {
            case "string" -> ClassName.get(String.class);
            case "number" -> ClassName.get(Double.class);
            case "boolean", "bool" -> ClassName.get(Boolean.class);
            case "timestamp" -> ClassName.get(java.time.Instant.class);
            default -> ClassName.get(Object.class);
        };
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Format a default value as a Java field initializer code block.
     */
    private static CodeBlock formatDefaultInitializer(String fieldType, Object defaultValue) {
        return switch (fieldType) {
            case "string" -> CodeBlock.of("$S", defaultValue.toString());
            case "number" -> {
                if (defaultValue instanceof Number num) {
                    yield CodeBlock.of("$L", num.doubleValue());
                }
                yield CodeBlock.of("$L", Double.parseDouble(defaultValue.toString()));
            }
            case "boolean", "bool" -> CodeBlock.of("$L", Boolean.valueOf(defaultValue.toString()));
            case "timestamp" -> CodeBlock.of("$T.parse($S)", java.time.Instant.class, defaultValue.toString());
            default -> CodeBlock.of("$L", defaultValue);
        };
    }

    /**
     * Check if a field has enum values defined.
     */
    private static boolean hasEnumValues(BprintSchema.Field field) {
        return field.enumValues != null && !field.enumValues.isEmpty();
    }

    /**
     * Convert a string to UPPER_SNAKE_CASE for constant names.
     * Handles hyphens, underscores, and camelCase boundaries.
     */
    static String toConstantCase(String name) {
        if (name == null || name.isEmpty()) return name;
        // Replace hyphens with underscores, then insert underscore before uppercase letters
        String result = name.replace("-", "_");
        // Insert underscore before uppercase letters preceded by lowercase
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(result.charAt(i - 1))) {
                sb.append('_');
            }
            sb.append(c);
        }
        return sb.toString().toUpperCase();
    }

    /**
     * Convert a hyphenated or underscored string to camelCase.
     */
    static String toCamelCase(String name) {
        if (name == null || name.isEmpty()) return name;
        String[] parts = name.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (sb.isEmpty()) {
                sb.append(parts[i].substring(0, 1).toLowerCase());
                if (parts[i].length() > 1) sb.append(parts[i].substring(1));
            } else {
                sb.append(parts[i].substring(0, 1).toUpperCase());
                if (parts[i].length() > 1) sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
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
