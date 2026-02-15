package co.chaim.generators.java;

import co.chaim.core.model.BprintSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class JavaGeneratorTest {

  @TempDir
  Path tempDir;

  private BprintSchema userSchema;
  private BprintSchema orderSchema;
  private BprintSchema userWithSortKeySchema;
  private TableMetadata tableMetadata;
  private JavaGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new JavaGenerator();

    // Create User schema (partition key only)
    userSchema = new BprintSchema();
    userSchema.schemaVersion = 1.1;
    userSchema.entityName = "User";
    userSchema.description = "User entity";

    BprintSchema.PrimaryKey userPk = new BprintSchema.PrimaryKey();
    userPk.partitionKey = "userId";  // Schema-defined partition key
    userSchema.primaryKey = userPk;
    
    BprintSchema.Field userIdField = new BprintSchema.Field();
    userIdField.name = "userId";
    userIdField.type = "string";
    userIdField.required = true;
    
    BprintSchema.Field emailField = new BprintSchema.Field();
    emailField.name = "email";
    emailField.type = "string";
    emailField.required = true;
    
    userSchema.fields = List.of(userIdField, emailField);

    // Create User schema with sort key (for composite key tests)
    userWithSortKeySchema = new BprintSchema();
    userWithSortKeySchema.schemaVersion = 1.1;
    userWithSortKeySchema.entityName = "User";
    userWithSortKeySchema.description = "User entity with sort key";

    BprintSchema.PrimaryKey userWithSkPk = new BprintSchema.PrimaryKey();
    userWithSkPk.partitionKey = "userId";
    userWithSkPk.sortKey = "entityType";  // Schema-defined sort key
    userWithSortKeySchema.primaryKey = userWithSkPk;
    
    BprintSchema.Field userIdField2 = new BprintSchema.Field();
    userIdField2.name = "userId";
    userIdField2.type = "string";
    userIdField2.required = true;
    
    BprintSchema.Field entityTypeField = new BprintSchema.Field();
    entityTypeField.name = "entityType";
    entityTypeField.type = "string";
    entityTypeField.required = true;
    
    BprintSchema.Field emailField2 = new BprintSchema.Field();
    emailField2.name = "email";
    emailField2.type = "string";
    emailField2.required = true;
    
    userWithSortKeySchema.fields = List.of(userIdField2, entityTypeField, emailField2);

    // Create Order schema (same keys as userWithSortKeySchema for multi-entity tests)
    orderSchema = new BprintSchema();
    orderSchema.schemaVersion = 1.1;
    orderSchema.entityName = "Order";
    orderSchema.description = "Order entity";

    BprintSchema.PrimaryKey orderPk = new BprintSchema.PrimaryKey();
    orderPk.partitionKey = "userId";     // Same PK as User for multi-entity table
    orderPk.sortKey = "entityType";       // Same SK as User for multi-entity table
    orderSchema.primaryKey = orderPk;
    
    BprintSchema.Field orderUserIdField = new BprintSchema.Field();
    orderUserIdField.name = "userId";
    orderUserIdField.type = "string";
    orderUserIdField.required = true;
    
    BprintSchema.Field orderEntityTypeField = new BprintSchema.Field();
    orderEntityTypeField.name = "entityType";
    orderEntityTypeField.type = "string";
    orderEntityTypeField.required = true;
    
    BprintSchema.Field amountField = new BprintSchema.Field();
    amountField.name = "amount";
    amountField.type = "number";
    amountField.required = true;
    
    orderSchema.fields = List.of(orderUserIdField, orderEntityTypeField, amountField);

    // Create table metadata (simple record with just table info)
    tableMetadata = new TableMetadata(
        "DataTable",
        "arn:aws:dynamodb:us-east-1:123456789012:table/DataTable",
        "us-east-1"
    );
  }

  @Test
  void generatesEntityWithSchemaDefinedPartitionKey() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userSchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/User.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check Lombok annotations
    assertThat(content).contains("@Data");
    assertThat(content).contains("@Builder");
    assertThat(content).contains("@NoArgsConstructor");
    assertThat(content).contains("@AllArgsConstructor");
    
    // Check DynamoDB annotation
    assertThat(content).contains("@DynamoDbBean");
    
    // Check domain fields (NO invented pk/sk fields!)
    assertThat(content).contains("private String userId");
    assertThat(content).contains("private String email");
    
    // Should NOT have invented pk/sk fields
    assertThat(content).doesNotContain("private String pk;");
    assertThat(content).doesNotContain("private String sk;");
    
    // Check @DynamoDbPartitionKey on schema-defined key getter
    assertThat(content).contains("@DynamoDbPartitionKey");
    assertThat(content).contains("public String getUserId()");
    
    // No sort key for this schema
    assertThat(content).doesNotContain("@DynamoDbSortKey");
  }

  @Test
  void generatesEntityWithSchemaDefinedCompositeKey() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userWithSortKeySchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/User.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check DynamoDB annotations on schema-defined key getters
    assertThat(content).contains("@DynamoDbPartitionKey");
    assertThat(content).contains("public String getUserId()");
    
    assertThat(content).contains("@DynamoDbSortKey");
    assertThat(content).contains("public String getEntityType()");
    
    // Check domain fields
    assertThat(content).contains("private String userId");
    assertThat(content).contains("private String entityType");
    assertThat(content).contains("private String email");
  }

  @Test
  void generatesKeysHelperWithFieldConstants() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userSchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/keys/UserKeys.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check class structure
    assertThat(content).contains("public final class UserKeys");
    
    // Check field name constant (no prefixes!)
    assertThat(content).contains("public static final String PARTITION_KEY_FIELD = \"userId\"");
    
    // Should NOT have entity prefix (old behavior)
    assertThat(content).doesNotContain("ENTITY_PREFIX");
    assertThat(content).doesNotContain("USER#");
    
    // Check key() method
    assertThat(content).contains("public static Key key(String userId)");
    assertThat(content).contains("partitionValue(userId)");
  }

  @Test
  void generatesKeysHelperWithSortKey() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userWithSortKeySchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/keys/UserKeys.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check both field constants
    assertThat(content).contains("PARTITION_KEY_FIELD = \"userId\"");
    assertThat(content).contains("SORT_KEY_FIELD = \"entityType\"");
    
    // Check key() method takes both parameters
    assertThat(content).contains("public static Key key(String userId, String entityType)");
    assertThat(content).contains("partitionValue(userId)");
    assertThat(content).contains("sortValue(entityType)");
  }

  @Test
  void generatesRepositoryWithFindByKey() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userSchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/repository/UserRepository.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check class structure
    assertThat(content).contains("public class UserRepository");
    
    // Check constructors
    assertThat(content).contains("public UserRepository(ChaimDynamoDbClient client)");
    assertThat(content).contains("public UserRepository(DynamoDbEnhancedClient enhancedClient, String tableName)");
    
    // Check key-based methods (no pk/sk arguments!)
    assertThat(content).contains("public void save(User entity)");
    assertThat(content).contains("public Optional<User> findByKey(String userId)");
    assertThat(content).contains("public void deleteByKey(String userId)");
    
    // Should NOT have old pk/sk methods
    assertThat(content).doesNotContain("findByPkSk");
    assertThat(content).doesNotContain("deleteByPkSk");
    
    // Should NOT contain findAll or scan
    assertThat(content).doesNotContain("findAll");
    assertThat(content).doesNotContain("scan()");
  }

  @Test
  void generatesRepositoryWithCompositeKey() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userWithSortKeySchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/repository/UserRepository.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check findByKey takes both PK and SK
    assertThat(content).contains("public Optional<User> findByKey(String userId, String entityType)");
    assertThat(content).contains("public void deleteByKey(String userId, String entityType)");
  }

  @Test
  void generatesChaimDynamoDbClient() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userSchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/client/ChaimDynamoDbClient.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check class structure
    assertThat(content).contains("public class ChaimDynamoDbClient");
    
    // Check getters
    assertThat(content).contains("public DynamoDbEnhancedClient getEnhancedClient()");
    assertThat(content).contains("public String getTableName()");
    
    // Check builder pattern
    assertThat(content).contains("public static Builder builder()");
    assertThat(content).contains("public static class Builder");
    
    // Check wrap() for DI
    assertThat(content).contains("public static ChaimDynamoDbClient wrap(DynamoDbEnhancedClient client, String tableName)");
    
    // Check builder methods
    assertThat(content).contains("public Builder tableName(String tableName)");
    assertThat(content).contains("public Builder region(String region)");
    assertThat(content).contains("public Builder endpoint(String endpoint)");
    assertThat(content).contains("public Builder existingClient(DynamoDbEnhancedClient client)");
    
    // Check environment variable resolution
    assertThat(content).contains("CHAIM_TABLE_NAME");
    assertThat(content).contains("AWS_REGION");
    assertThat(content).contains("DYNAMODB_ENDPOINT");
  }

  @Test
  void generatesChaimConfigWithFactoryMethods() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userSchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/config/ChaimConfig.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check class structure
    assertThat(content).contains("public class ChaimConfig");
    
    // Check constants
    assertThat(content).contains("public static final String TABLE_NAME = \"DataTable\"");
    assertThat(content).contains("public static final String TABLE_ARN");
    assertThat(content).contains("public static final String REGION = \"us-east-1\"");
    
    // Check getClient()
    assertThat(content).contains("public static ChaimDynamoDbClient getClient()");
    
    // Check clientBuilder()
    assertThat(content).contains("public static ChaimDynamoDbClient.Builder clientBuilder()");
    
    // Check repository factory methods
    assertThat(content).contains("public static UserRepository userRepository()");
    assertThat(content).contains("public static UserRepository userRepository(ChaimDynamoDbClient client)");
  }

  @Test
  void generatesMultipleEntitiesForSingleTable() throws Exception {
    Path out = tempDir.resolve("generated");
    
    // Generate both User and Order for the same table (both have same PK/SK)
    generator.generateForTable(List.of(userWithSortKeySchema, orderSchema), "com.example.model", out, tableMetadata);

    // Check both entity files exist
    assertThat(Files.exists(out.resolve("com/example/model/User.java"))).isTrue();
    assertThat(Files.exists(out.resolve("com/example/model/Order.java"))).isTrue();
    
    // Check both keys helpers exist
    assertThat(Files.exists(out.resolve("com/example/model/keys/UserKeys.java"))).isTrue();
    assertThat(Files.exists(out.resolve("com/example/model/keys/OrderKeys.java"))).isTrue();
    
    // Check both repositories exist
    assertThat(Files.exists(out.resolve("com/example/model/repository/UserRepository.java"))).isTrue();
    assertThat(Files.exists(out.resolve("com/example/model/repository/OrderRepository.java"))).isTrue();
    
    // Shared infrastructure should exist only once
    assertThat(Files.exists(out.resolve("com/example/model/client/ChaimDynamoDbClient.java"))).isTrue();
    assertThat(Files.exists(out.resolve("com/example/model/config/ChaimConfig.java"))).isTrue();
    
    // Check ChaimConfig has factory methods for BOTH entities
    String configContent = Files.readString(out.resolve("com/example/model/config/ChaimConfig.java"));
    assertThat(configContent).contains("public static UserRepository userRepository()");
    assertThat(configContent).contains("public static OrderRepository orderRepository()");
  }

  @Test
  void multiEntitySchemasSameKeyFields() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userWithSortKeySchema, orderSchema), "com.example.model", out, tableMetadata);

    // Both entities should use same key field names
    String userKeysContent = Files.readString(out.resolve("com/example/model/keys/UserKeys.java"));
    assertThat(userKeysContent).contains("PARTITION_KEY_FIELD = \"userId\"");
    assertThat(userKeysContent).contains("SORT_KEY_FIELD = \"entityType\"");
    
    String orderKeysContent = Files.readString(out.resolve("com/example/model/keys/OrderKeys.java"));
    assertThat(orderKeysContent).contains("PARTITION_KEY_FIELD = \"userId\"");
    assertThat(orderKeysContent).contains("SORT_KEY_FIELD = \"entityType\"");
  }

  @Test
  void derivesEntityNameFromNamespace() throws Exception {
    // Create schema without explicit entityName - should default to "Entity"
    BprintSchema schemaWithoutName = new BprintSchema();
    schemaWithoutName.schemaVersion = 1.1;
    schemaWithoutName.entityName = null;  // Not set
    schemaWithoutName.description = "Products";

    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "productId";
    schemaWithoutName.primaryKey = pk;
    
    BprintSchema.Field field = new BprintSchema.Field();
    field.name = "productId";
    field.type = "string";
    field.required = true;
    schemaWithoutName.fields = List.of(field);

    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(schemaWithoutName), "com.example.model", out, tableMetadata);

    // Should default to "Entity" when entityName is not set
    assertThat(Files.exists(out.resolve("com/example/model/Entity.java"))).isTrue();
    assertThat(Files.exists(out.resolve("com/example/model/keys/EntityKeys.java"))).isTrue();
    
    String keysContent = Files.readString(out.resolve("com/example/model/keys/EntityKeys.java"));
    assertThat(keysContent).contains("PARTITION_KEY_FIELD = \"productId\"");
  }

  // =========================================================================
  // nameOverride and auto-conversion tests
  // =========================================================================

  @Test
  void generatesEntityWithHyphenatedFieldNames() throws Exception {
    BprintSchema schema = new BprintSchema();
    schema.schemaVersion = 1.1;
    schema.entityName = "Order";
    schema.description = "Order entity with hyphenated fields";

    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "order-id";
    schema.primaryKey = pk;

    BprintSchema.Field orderId = new BprintSchema.Field();
    orderId.name = "order-id";
    orderId.type = "string";
    orderId.required = true;

    BprintSchema.Field orderDate = new BprintSchema.Field();
    orderDate.name = "order-date";
    orderDate.type = "timestamp";
    orderDate.required = true;

    BprintSchema.Field customerId = new BprintSchema.Field();
    customerId.name = "customerId";
    customerId.type = "string";
    customerId.required = true;

    schema.fields = List.of(orderId, orderDate, customerId);

    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(schema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/Order.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);

    // Hyphenated fields should be auto-converted to camelCase
    assertThat(content).contains("private String orderId");
    assertThat(content).contains("private Instant orderDate");
    assertThat(content).contains("private String customerId");

    // Should NOT contain the raw hyphenated names as Java fields
    assertThat(content).doesNotContain("private String order-id");
    assertThat(content).doesNotContain("private Instant order-date");

    // Auto-converted fields need @DynamoDbAttribute annotation
    assertThat(content).contains("@DynamoDbAttribute(\"order-id\")");
    assertThat(content).contains("@DynamoDbAttribute(\"order-date\")");

    // Clean field should NOT have @DynamoDbAttribute
    // customerId maps to customerId - no annotation needed
    assertThat(content).doesNotContain("@DynamoDbAttribute(\"customerId\")");

    // PK getter should have both @DynamoDbPartitionKey and @DynamoDbAttribute
    assertThat(content).contains("@DynamoDbPartitionKey");
    assertThat(content).contains("public String getOrderId()");
  }

  @Test
  void generatesEntityWithNameOverride() throws Exception {
    BprintSchema schema = new BprintSchema();
    schema.schemaVersion = 1.1;
    schema.entityName = "Order";
    schema.description = "Order with nameOverride";

    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    schema.primaryKey = pk;

    BprintSchema.Field orderId = new BprintSchema.Field();
    orderId.name = "orderId";
    orderId.type = "string";
    orderId.required = true;

    BprintSchema.Field tfa = new BprintSchema.Field();
    tfa.name = "2fa-verified";
    tfa.nameOverride = "twoFactorVerified";
    tfa.type = "boolean";
    tfa.required = false;

    schema.fields = List.of(orderId, tfa);

    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(schema), "com.example.model", out, tableMetadata);

    String content = Files.readString(out.resolve("com/example/model/Order.java"));

    // nameOverride should be used as the Java field name
    assertThat(content).contains("private Boolean twoFactorVerified");

    // @DynamoDbAttribute should map back to the original DynamoDB name
    assertThat(content).contains("@DynamoDbAttribute(\"2fa-verified\")");

    // Clean field should NOT have @DynamoDbAttribute
    assertThat(content).doesNotContain("@DynamoDbAttribute(\"orderId\")");
    assertThat(content).contains("private String orderId");
  }

  @Test
  void generatesEntityWithCleanName_noAnnotation() throws Exception {
    BprintSchema schema = new BprintSchema();
    schema.schemaVersion = 1.1;
    schema.entityName = "Customer";
    schema.description = "Clean field names";

    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "customerId";
    schema.primaryKey = pk;

    BprintSchema.Field custId = new BprintSchema.Field();
    custId.name = "customerId";
    custId.type = "string";
    custId.required = true;

    BprintSchema.Field email = new BprintSchema.Field();
    email.name = "email";
    email.type = "string";
    email.required = true;

    schema.fields = List.of(custId, email);

    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(schema), "com.example.model", out, tableMetadata);

    String content = Files.readString(out.resolve("com/example/model/Customer.java"));

    // Clean names should NOT have @DynamoDbAttribute at all
    assertThat(content).doesNotContain("@DynamoDbAttribute");
    assertThat(content).contains("private String customerId");
    assertThat(content).contains("private String email");
  }

  @Test
  void detectsCollisionBetweenAutoConvertedNames() {
    // Two fields that resolve to the same Java identifier should throw
    BprintSchema.Field field1 = new BprintSchema.Field();
    field1.name = "order-date";
    field1.type = "string";

    BprintSchema.Field field2 = new BprintSchema.Field();
    field2.name = "orderDate";
    field2.type = "string";

    List<BprintSchema.Field> fields = new ArrayList<>();
    fields.add(field1);
    fields.add(field2);

    assertThatThrownBy(() -> JavaGenerator.detectCollisions(fields))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Name collision")
        .hasMessageContaining("order-date")
        .hasMessageContaining("orderDate");
  }

  @Test
  void generatesEntityWithMixedFieldNames() throws Exception {
    BprintSchema schema = new BprintSchema();
    schema.schemaVersion = 1.1;
    schema.entityName = "Order";
    schema.description = "Mixed field names";

    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "order-id";
    schema.primaryKey = pk;

    BprintSchema.Field orderId = new BprintSchema.Field();
    orderId.name = "order-id";
    orderId.type = "string";
    orderId.required = true;

    BprintSchema.Field orderDate = new BprintSchema.Field();
    orderDate.name = "order-date";
    orderDate.type = "timestamp";
    orderDate.required = true;

    BprintSchema.Field tfa = new BprintSchema.Field();
    tfa.name = "2fa-verified";
    tfa.nameOverride = "twoFactorVerified";
    tfa.type = "boolean";
    tfa.required = false;

    BprintSchema.Field customerId = new BprintSchema.Field();
    customerId.name = "customerId";
    customerId.type = "string";
    customerId.required = true;

    schema.fields = List.of(orderId, orderDate, tfa, customerId);

    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(schema), "com.example.model", out, tableMetadata);

    String content = Files.readString(out.resolve("com/example/model/Order.java"));

    // Auto-converted: order-id -> orderId with annotation
    assertThat(content).contains("private String orderId");
    assertThat(content).contains("@DynamoDbAttribute(\"order-id\")");

    // Auto-converted: order-date -> orderDate with annotation
    assertThat(content).contains("private Instant orderDate");
    assertThat(content).contains("@DynamoDbAttribute(\"order-date\")");

    // nameOverride: 2fa-verified -> twoFactorVerified with annotation
    assertThat(content).contains("private Boolean twoFactorVerified");
    assertThat(content).contains("@DynamoDbAttribute(\"2fa-verified\")");

    // Clean: customerId -> customerId, no annotation
    assertThat(content).contains("private String customerId");
    assertThat(content).doesNotContain("@DynamoDbAttribute(\"customerId\")");
  }

  @Test
  void toJavaCamelCaseConvertsCorrectly() {
    // Hyphens trigger camelCase
    assertThat(JavaGenerator.toJavaCamelCase("order-date")).isEqualTo("orderDate");
    assertThat(JavaGenerator.toJavaCamelCase("user-id")).isEqualTo("userId");

    // Underscores trigger camelCase
    assertThat(JavaGenerator.toJavaCamelCase("order_date")).isEqualTo("orderDate");

    // Leading digits get underscore prefix
    assertThat(JavaGenerator.toJavaCamelCase("2fa-enabled")).isEqualTo("_2faEnabled");

    // All-caps lowered
    assertThat(JavaGenerator.toJavaCamelCase("TTL")).isEqualTo("ttl");

    // Already valid identifier stays as-is via resolveCodeName
    BprintSchema.Field cleanField = new BprintSchema.Field();
    cleanField.name = "customerId";
    cleanField.type = "string";
    assertThat(JavaGenerator.resolveCodeName(cleanField)).isEqualTo("customerId");
  }

  @Test
  void resolveCodeNameUsesNameOverrideWhenSet() {
    BprintSchema.Field field = new BprintSchema.Field();
    field.name = "2fa-verified";
    field.nameOverride = "twoFactorVerified";
    field.type = "boolean";

    assertThat(JavaGenerator.resolveCodeName(field)).isEqualTo("twoFactorVerified");
  }

  @Test
  void resolveCodeNameAutoConvertsWhenNoOverride() {
    BprintSchema.Field field = new BprintSchema.Field();
    field.name = "order-date";
    field.type = "string";

    assertThat(JavaGenerator.resolveCodeName(field)).isEqualTo("orderDate");
  }

  @Test
  void keysHelperPreservesOriginalDynamoDbAttributeNames() throws Exception {
    BprintSchema schema = new BprintSchema();
    schema.schemaVersion = 1.1;
    schema.entityName = "Order";
    schema.description = "Order with hyphenated PK";

    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "order-id";
    schema.primaryKey = pk;

    BprintSchema.Field orderId = new BprintSchema.Field();
    orderId.name = "order-id";
    orderId.type = "string";
    orderId.required = true;

    schema.fields = List.of(orderId);

    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(schema), "com.example.model", out, tableMetadata);

    String keysContent = Files.readString(out.resolve("com/example/model/keys/OrderKeys.java"));

    // Constants should use original DynamoDB attribute name
    assertThat(keysContent).contains("PARTITION_KEY_FIELD = \"order-id\"");

    // Method parameter should use resolved code name
    assertThat(keysContent).contains("public static Key key(String orderId)");
  }

  @Test
  void repositoryUsesResolvedCodeNamesForParameters() throws Exception {
    BprintSchema schema = new BprintSchema();
    schema.schemaVersion = 1.1;
    schema.entityName = "Order";
    schema.description = "Order with hyphenated PK";

    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "order-id";
    schema.primaryKey = pk;

    BprintSchema.Field orderId = new BprintSchema.Field();
    orderId.name = "order-id";
    orderId.type = "string";
    orderId.required = true;

    schema.fields = List.of(orderId);

    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(schema), "com.example.model", out, tableMetadata);

    String repoContent = Files.readString(out.resolve("com/example/model/repository/OrderRepository.java"));

    // Parameters should use resolved code name, not raw hyphenated name
    assertThat(repoContent).contains("findByKey(String orderId)");
    assertThat(repoContent).contains("deleteByKey(String orderId)");

    // Method signatures should not use hyphenated names as parameter names
    assertThat(repoContent).doesNotContain("findByKey(String order-id)");
    assertThat(repoContent).doesNotContain("deleteByKey(String order-id)");
  }
}
