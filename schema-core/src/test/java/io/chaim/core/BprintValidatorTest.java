package io.chaim.core;

import io.chaim.core.model.BprintSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;

public class BprintValidatorTest {

  @Test
  void validatesHappyPath() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatCode(() -> BprintValidator.validate(s)).doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingSchemaVersion() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = null;
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("schemaVersion required");
  }

  @Test
  void rejectsEmptySchemaVersion() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("schemaVersion required");
  }

  @Test
  void rejectsMissingNamespace() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = null;
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("namespace required");
  }

  @Test
  void rejectsEmptyNamespace() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("namespace required");
  }

  @Test
  void rejectsMissingDescription() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = null;

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("description required");
  }

  @Test
  void rejectsEmptyDescription() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("description required");
  }

  @Test
  void rejectsMissingEntity() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";
    s.entity = null;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity is required");
  }

  @Test
  void rejectsMissingEntityName() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = null;
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity.name required");
  }

  @Test
  void rejectsEmptyEntityName() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity.name required");
  }

  @Test
  void rejectsMissingPrimaryKey() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    e.primaryKey = null;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity.primaryKey.partitionKey required");
  }

  @Test
  void rejectsMissingPartitionKey() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = null;
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity.primaryKey.partitionKey required");
  }

  @Test
  void rejectsEmptyPartitionKey() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity.primaryKey.partitionKey required");
  }

  @Test
  void rejectsMissingFields() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    e.fields = null;
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity.fields must have at least one field");
  }

  @Test
  void rejectsEmptyFields() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    e.fields = new ArrayList<>();
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("entity.fields must have at least one field");
  }

  @Test
  void rejectsFieldWithMissingName() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = null;
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Order: field.name required");
  }

  @Test
  void rejectsFieldWithEmptyName() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Order: field.name required");
  }

  @Test
  void rejectsFieldWithMissingType() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = null;
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Order.orderId: type required");
  }

  @Test
  void rejectsFieldWithEmptyType() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Order.orderId: type required");
  }

  @Test
  void rejectsBadType() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "money";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("unsupported type");
  }

  @Test
  void rejectsDuplicateFieldNames() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;

    BprintSchema.Field f1 = new BprintSchema.Field();
    f1.name = "orderId";
    f1.type = "string";
    f1.required = true;

    BprintSchema.Field f2 = new BprintSchema.Field();
    f2.name = "orderId"; // Same name as first field
    f2.type = "string";
    f2.required = false;

    e.fields = List.of(f1, f2);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("duplicate field");
  }

  @Test
  void validatesAllSupportedFieldTypes() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;

    BprintSchema.Field stringField = new BprintSchema.Field();
    stringField.name = "name";
    stringField.type = "string";
    stringField.required = false;

    BprintSchema.Field numberField = new BprintSchema.Field();
    numberField.name = "amount";
    numberField.type = "number";
    numberField.required = false;

    BprintSchema.Field boolField = new BprintSchema.Field();
    boolField.name = "isActive";
    boolField.type = "bool";
    boolField.required = false;

    BprintSchema.Field timestampField = new BprintSchema.Field();
    timestampField.name = "createdAt";
    timestampField.type = "timestamp";
    timestampField.required = false;

    e.fields = List.of(stringField, numberField, boolField, timestampField);
    s.entity = e;

    assertThatCode(() -> BprintValidator.validate(s)).doesNotThrowAnyException();
  }

  @Test
  void validatesEnumValues() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;

    BprintSchema.Field f1 = new BprintSchema.Field();
    f1.name = "orderId";
    f1.type = "string";
    f1.required = true;

    BprintSchema.Field enumField = new BprintSchema.Field();
    enumField.name = "status";
    enumField.type = "string";
    enumField.required = false;
    enumField.enumValues = List.of("pending", "processing", "completed", "cancelled");

    e.fields = List.of(f1, enumField);
    s.entity = e;

    assertThatCode(() -> BprintValidator.validate(s)).doesNotThrowAnyException();
  }

  @Test
  void rejectsEmptyEnumValues() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;

    BprintSchema.Field f1 = new BprintSchema.Field();
    f1.name = "orderId";
    f1.type = "string";
    f1.required = true;

    BprintSchema.Field enumField = new BprintSchema.Field();
    enumField.name = "status";
    enumField.type = "string";
    enumField.required = false;
    enumField.enumValues = new ArrayList<>();

    e.fields = List.of(f1, enumField);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("enum values cannot be empty");
  }

  @Test
  void rejectsEnumWithEmptyString() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;

    BprintSchema.Field f1 = new BprintSchema.Field();
    f1.name = "orderId";
    f1.type = "string";
    f1.required = true;

    BprintSchema.Field enumField = new BprintSchema.Field();
    enumField.name = "status";
    enumField.type = "string";
    enumField.required = false;
    enumField.enumValues = List.of("pending", "", "completed");

    e.fields = List.of(f1, enumField);
    s.entity = e;

    assertThatThrownBy(() -> BprintValidator.validate(s))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("enum values cannot be empty");
  }

  @Test
  void validatesSortKey() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    pk.sortKey = "timestamp";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    s.entity = e;

    assertThatCode(() -> BprintValidator.validate(s)).doesNotThrowAnyException();
  }

  @Test
  void validatesMultipleFields() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;

    BprintSchema.Field f1 = new BprintSchema.Field();
    f1.name = "orderId";
    f1.type = "string";
    f1.required = true;

    BprintSchema.Field f2 = new BprintSchema.Field();
    f2.name = "customerId";
    f2.type = "string";
    f2.required = true;

    BprintSchema.Field f3 = new BprintSchema.Field();
    f3.name = "amount";
    f3.type = "number";
    f3.required = true;

    e.fields = List.of(f1, f2, f3);
    s.entity = e;

    assertThatCode(() -> BprintValidator.validate(s)).doesNotThrowAnyException();
  }

  @Test
  void validatesAnnotations() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);

    BprintSchema.Annotations annotations = new BprintSchema.Annotations();
    annotations.pii = true;
    annotations.retention = "7years";
    annotations.encryption = "required";
    e.annotations = annotations;

    s.entity = e;

    assertThatCode(() -> BprintValidator.validate(s)).doesNotThrowAnyException();
  }

  @Test
  void validatesNullAnnotations() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;
    BprintSchema.Field f = new BprintSchema.Field();
    f.name = "orderId";
    f.type = "string";
    f.required = true;
    e.fields = List.of(f);
    e.annotations = null;
    s.entity = e;

    assertThatCode(() -> BprintValidator.validate(s)).doesNotThrowAnyException();
  }

  @Test
  void validatesFieldWithDefaultValue() {
    BprintSchema s = new BprintSchema();
    s.schemaVersion = "v1";
    s.namespace = "acme.orders";
    s.description = "Basic order management system";

    BprintSchema.Entity e = new BprintSchema.Entity();
    e.name = "Order";
    BprintSchema.PrimaryKey pk = new BprintSchema.PrimaryKey();
    pk.partitionKey = "orderId";
    e.primaryKey = pk;

    BprintSchema.Field f1 = new BprintSchema.Field();
    f1.name = "orderId";
    f1.type = "string";
    f1.required = true;

    BprintSchema.Field fieldWithDefault = new BprintSchema.Field();
    fieldWithDefault.name = "isActive";
    fieldWithDefault.type = "bool";
    fieldWithDefault.required = false;
    fieldWithDefault.defaultValue = true;

    e.fields = List.of(f1, fieldWithDefault);
    s.entity = e;

    assertThatCode(() -> BprintValidator.validate(s)).doesNotThrowAnyException();
  }
}
