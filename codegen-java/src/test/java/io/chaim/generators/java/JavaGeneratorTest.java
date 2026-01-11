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
  private TableMetadata tableMetadata;
  private JavaGenerator generator;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @BeforeEach
  void setUp() {
    generator = new JavaGenerator();

    // Create User schema
    userSchema = new BprintSchema();
    userSchema.schemaVersion = 1.0;
    userSchema.namespace = "example.users";
    userSchema.description = "User entity";

    BprintSchema.Entity userEntity = new BprintSchema.Entity();
    userEntity.name = "User";
    BprintSchema.PrimaryKey userPk = new BprintSchema.PrimaryKey();
    userPk.partitionKey = "userId";
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

    // Create Order schema
    orderSchema = new BprintSchema();
    orderSchema.schemaVersion = 1.0;
    orderSchema.namespace = "example.orders";
    orderSchema.description = "Order entity";

    BprintSchema.Entity orderEntity = new BprintSchema.Entity();
    orderEntity.name = "Order";
    BprintSchema.PrimaryKey orderPk = new BprintSchema.PrimaryKey();
    orderPk.partitionKey = "orderId";
    orderEntity.primaryKey = orderPk;
    
    BprintSchema.Field orderIdField = new BprintSchema.Field();
    orderIdField.name = "orderId";
    orderIdField.type = "string";
    orderIdField.required = true;
    
    BprintSchema.Field amountField = new BprintSchema.Field();
    amountField.name = "amount";
    amountField.type = "number";
    amountField.required = true;
    
    orderEntity.fields = List.of(orderIdField, amountField);
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
  void generatesEntityWithPkSkFields() throws Exception {
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
    
    // Check pk/sk fields
    assertThat(content).contains("private String pk");
    assertThat(content).contains("private String sk");
    
    // Check DynamoDB key annotations on getters
    assertThat(content).contains("@DynamoDbPartitionKey");
    assertThat(content).contains("public String getPk()");
    assertThat(content).contains("@DynamoDbSortKey");
    assertThat(content).contains("public String getSk()");
    
    // Check domain fields
    assertThat(content).contains("private String userId");
    assertThat(content).contains("private String email");
  }

  @Test
  void generatesKeysHelper() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userSchema), "com.example.model", out, tableMetadata);

    Path file = out.resolve("com/example/model/keys/UserKeys.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    
    // Check class structure
    assertThat(content).contains("public final class UserKeys");
    
    // Check constants
    assertThat(content).contains("public static final String ENTITY_PREFIX = \"USER#\"");
    assertThat(content).contains("public static final String DEFAULT_SK");
    
    // Check pk() method
    assertThat(content).contains("public static String pk(String userId)");
    assertThat(content).contains("return ENTITY_PREFIX + userId");
    
    // Check sk() method
    assertThat(content).contains("public static String sk()");
    
    // Check key() method
    assertThat(content).contains("public static Key key(String userId)");
  }

  @Test
  void generatesRepository() throws Exception {
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
    
    // Check PK/SK-based methods
    assertThat(content).contains("public void save(User entity)");
    assertThat(content).contains("public Optional<User> findByPkSk(String pk, String sk)");
    assertThat(content).contains("public void deleteByPkSk(String pk, String sk)");
    
    // Check convenience methods
    assertThat(content).contains("public Optional<User> findByUserId(String userId)");
    assertThat(content).contains("public void deleteByUserId(String userId)");
    
    // Should NOT contain findAll or scan
    assertThat(content).doesNotContain("findAll");
    assertThat(content).doesNotContain("scan()");
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
    
    // Generate both User and Order for the same table
    generator.generateForTable(List.of(userSchema, orderSchema), "com.example.model", out, tableMetadata);

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
  void generatesCorrectKeyPrefixes() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generateForTable(List.of(userSchema, orderSchema), "com.example.model", out, tableMetadata);

    // Check User key prefix
    String userKeysContent = Files.readString(out.resolve("com/example/model/keys/UserKeys.java"));
    assertThat(userKeysContent).contains("ENTITY_PREFIX = \"USER#\"");
    
    // Check Order key prefix
    String orderKeysContent = Files.readString(out.resolve("com/example/model/keys/OrderKeys.java"));
    assertThat(orderKeysContent).contains("ENTITY_PREFIX = \"ORDER#\"");
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
    assertThat(keysContent).contains("ENTITY_PREFIX = \"PRODUCTS#\"");
  }
}
