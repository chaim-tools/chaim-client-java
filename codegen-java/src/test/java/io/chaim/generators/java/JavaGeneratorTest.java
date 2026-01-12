package io.chaim.generators.java;

import io.chaim.core.model.BprintSchema;
import io.chaim.cdk.TableMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @BeforeEach
  void setUp() {
    generator = new JavaGenerator();

    // Create User schema (partition key only)
    userSchema = new BprintSchema();
    userSchema.schemaVersion = 1.0;
    userSchema.namespace = "example.users";
    userSchema.description = "User entity";

    BprintSchema.Entity userEntity = new BprintSchema.Entity();
    userEntity.name = "User";
    BprintSchema.PrimaryKey userPk = new BprintSchema.PrimaryKey();
    userPk.partitionKey = "userId";  // Schema-defined partition key
    userEntity.primaryKey = userPk;
    
    BprintSchema.Field userIdField = new BprintSchema.Field();
    userIdField.name = "userId";
    userIdField.type = "string";
    userIdField.required = true;
    
    BprintSchema.Field emailField = new BprintSchema.Field();
    emailField.name = "email";
    emailField.type = "string";
    emailField.required = true;
    
    userEntity.fields = List.of(userIdField, emailField);
    userSchema.entity = userEntity;

    // Create User schema with sort key (for composite key tests)
    userWithSortKeySchema = new BprintSchema();
    userWithSortKeySchema.schemaVersion = 1.0;
    userWithSortKeySchema.namespace = "example.users";
    userWithSortKeySchema.description = "User entity with sort key";

    BprintSchema.Entity userWithSkEntity = new BprintSchema.Entity();
    userWithSkEntity.name = "User";
    BprintSchema.PrimaryKey userWithSkPk = new BprintSchema.PrimaryKey();
    userWithSkPk.partitionKey = "userId";
    userWithSkPk.sortKey = "entityType";  // Schema-defined sort key
    userWithSkEntity.primaryKey = userWithSkPk;
    
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
    
    userWithSkEntity.fields = List.of(userIdField2, entityTypeField, emailField2);
    userWithSortKeySchema.entity = userWithSkEntity;

    // Create Order schema (same keys as userWithSortKeySchema for multi-entity tests)
    orderSchema = new BprintSchema();
    orderSchema.schemaVersion = 1.0;
    orderSchema.namespace = "example.orders";
    orderSchema.description = "Order entity";

    BprintSchema.Entity orderEntity = new BprintSchema.Entity();
    orderEntity.name = "Order";
    BprintSchema.PrimaryKey orderPk = new BprintSchema.PrimaryKey();
    orderPk.partitionKey = "userId";     // Same PK as User for multi-entity table
    orderPk.sortKey = "entityType";       // Same SK as User for multi-entity table
    orderEntity.primaryKey = orderPk;
    
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
    
    orderEntity.fields = List.of(orderUserIdField, orderEntityTypeField, amountField);
    orderSchema.entity = orderEntity;

    // Create table metadata
    ObjectNode schemaNode = MAPPER.createObjectNode();
    tableMetadata = new TableMetadata(
        "DataTable",
        "arn:aws:dynamodb:us-east-1:123456789012:table/DataTable",
        "us-east-1",
        schemaNode
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
    // Create schema without explicit entity.name
    BprintSchema schemaWithoutName = new BprintSchema();
    schemaWithoutName.schemaVersion = 1.0;
    schemaWithoutName.namespace = "example.products";
    schemaWithoutName.description = "Products";

    BprintSchema.Entity entity = new BprintSchema.Entity();
    // Note: entity.name is NOT set
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "productId";
    entity.primaryKey = pk;
    
    BprintSchema.Field field = new BprintSchema.Field();
    field.name = "productId";
    field.type = "string";
    field.required = true;
    entity.fields = List.of(field);
    
    schemaWithoutName.entity = entity;

    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(schemaWithoutName), "com.example.model", out, tableMetadata);

    // Should derive "Products" from namespace "example.products"
    assertThat(Files.exists(out.resolve("com/example/model/Products.java"))).isTrue();
    assertThat(Files.exists(out.resolve("com/example/model/keys/ProductsKeys.java"))).isTrue();
    
    String keysContent = Files.readString(out.resolve("com/example/model/keys/ProductsKeys.java"));
    assertThat(keysContent).contains("PARTITION_KEY_FIELD = \"productId\"");
  }
}
