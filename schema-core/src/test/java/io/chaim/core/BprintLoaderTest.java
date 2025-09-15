package io.chaim.core;

import io.chaim.core.model.BprintSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class BprintLoaderTest {

  @TempDir
  Path tempDir;

  private String validJsonSchema;

  @BeforeEach
  void setUp() {
    validJsonSchema = """
      {
        "schemaVersion": "v1",
        "namespace": "acme.orders",
        "description": "Basic order management system",
        "entity": {
          "name": "Order",
          "primaryKey": { "partitionKey": "orderId" },
          "fields": [
            { "name": "orderId", "type": "string", "required": true }
          ]
        }
      }
      """;
  }

  @Test
  void shouldLoadValidJsonSchema() throws IOException {
    Path jsonFile = tempDir.resolve("schema.json");
    Files.writeString(jsonFile, validJsonSchema);

    BprintSchema schema = BprintLoader.load(jsonFile);

    assertThat(schema).isNotNull();
    assertThat(schema.schemaVersion).isEqualTo("v1");
    assertThat(schema.namespace).isEqualTo("acme.orders");
    assertThat(schema.description).isEqualTo("Basic order management system");
    assertThat(schema.entity).isNotNull();
    assertThat(schema.entity.name).isEqualTo("Order");
    assertThat(schema.entity.primaryKey.partitionKey).isEqualTo("orderId");
    assertThat(schema.entity.fields).hasSize(1);
    assertThat(schema.entity.fields.get(0).name).isEqualTo("orderId");
    assertThat(schema.entity.fields.get(0).type).isEqualTo("string");
    assertThat(schema.entity.fields.get(0).required).isTrue();
  }

  @Test
  void shouldLoadBprintExtensionAsJson() throws IOException {
    Path bprintFile = tempDir.resolve("schema.bprint");
    Files.writeString(bprintFile, validJsonSchema);

    BprintSchema schema = BprintLoader.load(bprintFile);

    assertThat(schema).isNotNull();
    assertThat(schema.schemaVersion).isEqualTo("v1");
  }

  @Test
  void shouldLoadJsonExtension() throws IOException {
    Path jsonFile = tempDir.resolve("schema.json");
    Files.writeString(jsonFile, validJsonSchema);

    BprintSchema schema = BprintLoader.load(jsonFile);

    assertThat(schema).isNotNull();
    assertThat(schema.schemaVersion).isEqualTo("v1");
  }

  @Test
  void shouldRejectInvalidJson() throws IOException {
    String invalidJson = """
      {
        "schemaVersion": "v1",
        "namespace": "acme.orders",
        "description": "Basic order management system",
        "entity": {
          "name": "Order",
          "primaryKey": { "partitionKey": "orderId" },
          "fields": [
            { "name": "orderId", "type": "string", "required": true }
          ]
        }
      """; // Missing closing brace

    Path jsonFile = tempDir.resolve("invalid.json");
    Files.writeString(jsonFile, invalidJson);

    assertThatThrownBy(() -> BprintLoader.load(jsonFile))
      .isInstanceOf(IOException.class);
  }

  @Test
  void shouldRejectSchemaWithMissingRequiredFields() throws IOException {
    String incompleteJson = """
      {
        "schemaVersion": "v1",
        "namespace": "acme.orders"
      }
      """;

    Path jsonFile = tempDir.resolve("incomplete.json");
    Files.writeString(jsonFile, incompleteJson);

    assertThatThrownBy(() -> BprintLoader.load(jsonFile))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("description required");
  }

  @Test
  void shouldRejectSchemaWithInvalidEntity() throws IOException {
    String invalidEntityJson = """
      {
        "schemaVersion": "v1",
        "namespace": "acme.orders",
        "description": "Basic order management system",
        "entity": {
          "name": "Order",
          "fields": [
            { "name": "orderId", "type": "string", "required": true }
          ]
        }
      }
      """;

    Path jsonFile = tempDir.resolve("invalid-entity.json");
    Files.writeString(jsonFile, invalidEntityJson);

    assertThatThrownBy(() -> BprintLoader.load(jsonFile))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity.primaryKey.partitionKey required");
  }

  @Test
  void shouldHandleComplexSchemaWithAllFeatures() throws IOException {
    String complexJson = """
      {
        "schemaVersion": "v1",
        "namespace": "ecommerce.store.customer",
        "description": "Customer account information and profile data",
        "entity": {
          "name": "Customer",
          "primaryKey": {
            "partitionKey": "customerId",
            "sortKey": "email"
          },
          "fields": [
            { "name": "customerId", "type": "string", "required": true },
            { "name": "email", "type": "string", "required": true },
            { "name": "firstName", "type": "string", "required": true },
            { "name": "lastName", "type": "string", "required": true },
            { "name": "membershipTier", "type": "string", "required": false, "enumValues": ["bronze", "silver", "gold", "platinum"] },
            { "name": "isActive", "type": "bool", "required": false, "defaultValue": true },
            { "name": "createdAt", "type": "timestamp", "required": true },
            { "name": "lastLoginAt", "type": "timestamp", "required": false },
            { "name": "totalOrders", "type": "number", "required": false, "defaultValue": 0 },
            { "name": "totalSpent", "type": "number", "required": false, "defaultValue": 0.0 }
          ],
          "annotations": {
            "pii": true,
            "retention": "7years",
            "encryption": "required"
          }
        }
      }
      """;

    Path jsonFile = tempDir.resolve("complex.json");
    Files.writeString(jsonFile, complexJson);

    BprintSchema schema = BprintLoader.load(jsonFile);

    assertThat(schema).isNotNull();
    assertThat(schema.schemaVersion).isEqualTo("v1");
    assertThat(schema.namespace).isEqualTo("ecommerce.store.customer");
    assertThat(schema.description).isEqualTo("Customer account information and profile data");
    assertThat(schema.entity.name).isEqualTo("Customer");
    assertThat(schema.entity.primaryKey.partitionKey).isEqualTo("customerId");
    assertThat(schema.entity.primaryKey.sortKey).isEqualTo("email");
    assertThat(schema.entity.fields).hasSize(10);
    assertThat(schema.entity.annotations).isNotNull();
    assertThat(schema.entity.annotations.pii).isTrue();
    assertThat(schema.entity.annotations.retention).isEqualTo("7years");
    assertThat(schema.entity.annotations.encryption).isEqualTo("required");
  }

  @Test
  void shouldHandleEmptyFile() throws IOException {
    Path emptyFile = tempDir.resolve("empty.json");
    Files.writeString(emptyFile, "");

    assertThatThrownBy(() -> BprintLoader.load(emptyFile))
      .isInstanceOf(IOException.class);
  }

  @Test
  void shouldHandleFileWithOnlyWhitespace() throws IOException {
    Path whitespaceFile = tempDir.resolve("whitespace.json");
    Files.writeString(whitespaceFile, "   \n\t  ");

    assertThatThrownBy(() -> BprintLoader.load(whitespaceFile))
      .isInstanceOf(IOException.class);
  }

  @Test
  void shouldHandleMalformedJsonWithExtraCommas() throws IOException {
    String malformedJson = """
      {
        "schemaVersion": "v1",
        "namespace": "acme.orders",
        "description": "Basic order management system",
        "entity": {
          "name": "Order",
          "primaryKey": { "partitionKey": "orderId" },
          "fields": [
            { "name": "orderId", "type": "string", "required": true },
          ]
        }
      }
      """;

    Path jsonFile = tempDir.resolve("malformed.json");
    Files.writeString(jsonFile, malformedJson);

    assertThatThrownBy(() -> BprintLoader.load(jsonFile))
      .isInstanceOf(IOException.class);
  }
}
