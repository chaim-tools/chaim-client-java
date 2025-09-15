package io.chaim.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

public class BprintSchemaTest {

  private BprintSchema schema;
  private BprintSchema.Entity entity;
  private BprintSchema.PrimaryKey primaryKey;
  private BprintSchema.Field field;

  @BeforeEach
  void setUp() {
    schema = new BprintSchema();
    entity = new BprintSchema.Entity();
    primaryKey = new BprintSchema.PrimaryKey();
    field = new BprintSchema.Field();

    // Setup valid schema
    schema.schemaVersion = "v1";
    schema.namespace = "acme.orders";
    schema.description = "Basic order management system";

    entity.name = "Order";
    entity.description = "Order entity";

    primaryKey.partitionKey = "orderId";
    primaryKey.sortKey = "timestamp";

    field.name = "orderId";
    field.type = "string";
    field.required = true;
    field.description = "Unique order identifier";

    entity.primaryKey = primaryKey;
    entity.fields = List.of(field);
    schema.entity = entity;
  }

  @Test
  void shouldCreateValidSchema() {
    assertThat(schema).isNotNull();
    assertThat(schema.schemaVersion).isEqualTo("v1");
    assertThat(schema.namespace).isEqualTo("acme.orders");
    assertThat(schema.description).isEqualTo("Basic order management system");
    assertThat(schema.entity).isNotNull();
  }

  @Test
  void shouldCreateValidEntity() {
    assertThat(entity).isNotNull();
    assertThat(entity.name).isEqualTo("Order");
    assertThat(entity.description).isEqualTo("Order entity");
    assertThat(entity.primaryKey).isNotNull();
    assertThat(entity.fields).isNotNull();
    assertThat(entity.fields).hasSize(1);
  }

  @Test
  void shouldCreateValidPrimaryKey() {
    assertThat(primaryKey).isNotNull();
    assertThat(primaryKey.partitionKey).isEqualTo("orderId");
    assertThat(primaryKey.sortKey).isEqualTo("timestamp");
  }

  @Test
  void shouldCreateValidField() {
    assertThat(field).isNotNull();
    assertThat(field.name).isEqualTo("orderId");
    assertThat(field.type).isEqualTo("string");
    assertThat(field.required).isTrue();
    assertThat(field.description).isEqualTo("Unique order identifier");
  }

  @Test
  void shouldHandleNullValues() {
    schema.schemaVersion = null;
    schema.namespace = null;
    schema.description = null;
    schema.entity = null;

    assertThat(schema.schemaVersion).isNull();
    assertThat(schema.namespace).isNull();
    assertThat(schema.description).isNull();
    assertThat(schema.entity).isNull();
  }

  @Test
  void shouldHandleEmptyStrings() {
    schema.schemaVersion = "";
    schema.namespace = "";
    schema.description = "";

    assertThat(schema.schemaVersion).isEmpty();
    assertThat(schema.namespace).isEmpty();
    assertThat(schema.description).isEmpty();
  }

  @Test
  void shouldHandleWhitespaceStrings() {
    schema.schemaVersion = "  v1  ";
    schema.namespace = "  acme.orders  ";
    schema.description = "  Basic order management system  ";

    assertThat(schema.schemaVersion).isEqualTo("  v1  ");
    assertThat(schema.namespace).isEqualTo("  acme.orders  ");
    assertThat(schema.description).isEqualTo("  Basic order management system  ");
  }

  @Test
  void shouldHandleSpecialCharacters() {
    schema.schemaVersion = "v1.0-beta";
    schema.namespace = "acme.orders.v2";
    schema.description = "Order management system with special chars: @#$%^&*()";

    assertThat(schema.schemaVersion).isEqualTo("v1.0-beta");
    assertThat(schema.namespace).isEqualTo("acme.orders.v2");
    assertThat(schema.description).isEqualTo("Order management system with special chars: @#$%^&*()");
  }

  @Test
  void shouldHandleUnicodeCharacters() {
    schema.description = "Order management system with unicode: 🚀📦💳";

    assertThat(schema.description).isEqualTo("Order management system with unicode: 🚀📦💳");
  }

  @Test
  void shouldHandleLongStrings() {
    String longDescription = "A".repeat(1000);
    schema.description = longDescription;

    assertThat(schema.description).isEqualTo(longDescription);
    assertThat(schema.description).hasSize(1000);
  }

  @Test
  void shouldHandleEntityWithNullValues() {
    entity.name = null;
    entity.description = null;
    entity.primaryKey = null;
    entity.fields = null;
    entity.annotations = null;

    assertThat(entity.name).isNull();
    assertThat(entity.description).isNull();
    assertThat(entity.primaryKey).isNull();
    assertThat(entity.fields).isNull();
    assertThat(entity.annotations).isNull();
  }

  @Test
  void shouldHandlePrimaryKeyWithNullValues() {
    primaryKey.partitionKey = null;
    primaryKey.sortKey = null;

    assertThat(primaryKey.partitionKey).isNull();
    assertThat(primaryKey.sortKey).isNull();
  }

  @Test
  void shouldHandleFieldWithNullValues() {
    field.name = null;
    field.type = null;
    field.description = null;
    field.defaultValue = null;
    field.enumValues = null;

    assertThat(field.name).isNull();
    assertThat(field.type).isNull();
    assertThat(field.description).isNull();
    assertThat(field.defaultValue).isNull();
    assertThat(field.enumValues).isNull();
  }

  @Test
  void shouldHandleFieldWithDefaultValues() {
    field.defaultValue = "default";
    field.enumValues = List.of("option1", "option2", "option3");

    assertThat(field.defaultValue).isEqualTo("default");
    assertThat(field.enumValues).containsExactly("option1", "option2", "option3");
  }

  @Test
  void shouldHandleFieldWithBooleanDefaultValue() {
    field.type = "bool";
    field.defaultValue = true;

    assertThat(field.type).isEqualTo("bool");
    assertThat(field.defaultValue).isEqualTo(true);
  }

  @Test
  void shouldHandleFieldWithNumberDefaultValue() {
    field.type = "number";
    field.defaultValue = 42.5;

    assertThat(field.type).isEqualTo("number");
    assertThat(field.defaultValue).isEqualTo(42.5);
  }

  @Test
  void shouldHandleFieldWithIntegerDefaultValue() {
    field.type = "number";
    field.defaultValue = 100;

    assertThat(field.type).isEqualTo("number");
    assertThat(field.defaultValue).isEqualTo(100);
  }

  @Test
  void shouldHandleFieldWithEmptyEnumValues() {
    field.enumValues = new ArrayList<>();

    assertThat(field.enumValues).isEmpty();
  }

  @Test
  void shouldHandleFieldWithSingleEnumValue() {
    field.enumValues = List.of("single");

    assertThat(field.enumValues).containsExactly("single");
    assertThat(field.enumValues).hasSize(1);
  }

  @Test
  void shouldHandleFieldWithMultipleEnumValues() {
    field.enumValues = List.of("option1", "option2", "option3", "option4", "option5");

    assertThat(field.enumValues).containsExactly("option1", "option2", "option3", "option4", "option5");
    assertThat(field.enumValues).hasSize(5);
  }

  @Test
  void shouldHandleFieldWithDuplicateEnumValues() {
    field.enumValues = List.of("option1", "option2", "option1", "option3");

    assertThat(field.enumValues).containsExactly("option1", "option2", "option1", "option3");
    assertThat(field.enumValues).hasSize(4);
  }

  @Test
  void shouldHandleFieldWithSpecialCharactersInEnum() {
    field.enumValues = List.of("option-1", "option_2", "option.3", "option@4");

    assertThat(field.enumValues).containsExactly("option-1", "option_2", "option.3", "option@4");
  }

  @Test
  void shouldHandleFieldWithUnicodeInEnum() {
    field.enumValues = List.of("option🚀", "option📦", "option💳");

    assertThat(field.enumValues).containsExactly("option🚀", "option📦", "option💳");
  }

  @Test
  void shouldHandleAnnotations() {
    BprintSchema.Annotations annotations = new BprintSchema.Annotations();
    annotations.pii = true;
    annotations.retention = "7years";
    annotations.encryption = "required";

    entity.annotations = annotations;

    assertThat(entity.annotations).isNotNull();
    assertThat(entity.annotations.pii).isTrue();
    assertThat(entity.annotations.retention).isEqualTo("7years");
    assertThat(entity.annotations.encryption).isEqualTo("required");
  }

  @Test
  void shouldHandleNullAnnotations() {
    entity.annotations = null;

    assertThat(entity.annotations).isNull();
  }

  @Test
  void shouldHandleAnnotationsWithNullValues() {
    BprintSchema.Annotations annotations = new BprintSchema.Annotations();
    annotations.pii = null;
    annotations.retention = null;
    annotations.encryption = null;

    entity.annotations = annotations;

    assertThat(entity.annotations.pii).isNull();
    assertThat(entity.annotations.retention).isNull();
    assertThat(entity.annotations.encryption).isNull();
  }

  @Test
  void shouldHandleAnnotationsWithBooleanValues() {
    BprintSchema.Annotations annotations = new BprintSchema.Annotations();
    annotations.pii = false;

    entity.annotations = annotations;

    assertThat(entity.annotations.pii).isFalse();
  }

  @Test
  void shouldHandleAnnotationsWithSpecialCharacters() {
    BprintSchema.Annotations annotations = new BprintSchema.Annotations();
    annotations.retention = "7-years_with.special@chars";
    annotations.encryption = "required: AES-256";

    entity.annotations = annotations;

    assertThat(entity.annotations.retention).isEqualTo("7-years_with.special@chars");
    assertThat(entity.annotations.encryption).isEqualTo("required: AES-256");
  }

  @Test
  void shouldHandleMultipleFields() {
    BprintSchema.Field field2 = new BprintSchema.Field();
    field2.name = "customerId";
    field2.type = "string";
    field2.required = true;

    BprintSchema.Field field3 = new BprintSchema.Field();
    field3.name = "amount";
    field3.type = "number";
    field3.required = false;

    entity.fields = List.of(field, field2, field3);

    assertThat(entity.fields).hasSize(3);
    assertThat(entity.fields.get(0).name).isEqualTo("orderId");
    assertThat(entity.fields.get(1).name).isEqualTo("customerId");
    assertThat(entity.fields.get(2).name).isEqualTo("amount");
  }

  @Test
  void shouldHandleEmptyFieldsList() {
    entity.fields = new ArrayList<>();

    assertThat(entity.fields).isEmpty();
  }

  @Test
  void shouldHandleNullFieldsList() {
    entity.fields = null;

    assertThat(entity.fields).isNull();
  }

  @Test
  void shouldHandleFieldWithAllTypes() {
    List<BprintSchema.Field> allTypeFields = new ArrayList<>();

    BprintSchema.Field stringField = new BprintSchema.Field();
    stringField.name = "stringField";
    stringField.type = "string";
    allTypeFields.add(stringField);

    BprintSchema.Field numberField = new BprintSchema.Field();
    numberField.name = "numberField";
    numberField.type = "number";
    allTypeFields.add(numberField);

    BprintSchema.Field boolField = new BprintSchema.Field();
    boolField.name = "boolField";
    boolField.type = "bool";
    allTypeFields.add(boolField);

    BprintSchema.Field timestampField = new BprintSchema.Field();
    timestampField.name = "timestampField";
    timestampField.type = "timestamp";
    allTypeFields.add(timestampField);

    entity.fields = allTypeFields;

    assertThat(entity.fields).hasSize(4);
    assertThat(entity.fields.get(0).type).isEqualTo("string");
    assertThat(entity.fields.get(1).type).isEqualTo("number");
    assertThat(entity.fields.get(2).type).isEqualTo("bool");
    assertThat(entity.fields.get(3).type).isEqualTo("timestamp");
  }

  @Test
  void shouldHandleFieldWithMixedRequiredValues() {
    field.required = true;

    BprintSchema.Field optionalField = new BprintSchema.Field();
    optionalField.name = "optionalField";
    optionalField.type = "string";
    optionalField.required = false;

    entity.fields = List.of(field, optionalField);

    assertThat(entity.fields.get(0).required).isTrue();
    assertThat(entity.fields.get(1).required).isFalse();
  }

  @Test
  void shouldHandleFieldWithLongNames() {
    field.name = "veryLongFieldNameThatExceedsNormalLength";

    assertThat(field.name).isEqualTo("veryLongFieldNameThatExceedsNormalLength");
  }

  @Test
  void shouldHandleFieldWithSpecialCharactersInName() {
    field.name = "field-with_special.chars@123";

    assertThat(field.name).isEqualTo("field-with_special.chars@123");
  }

  @Test
  void shouldHandleFieldWithUnicodeInName() {
    field.name = "field🚀📦💳";

    assertThat(field.name).isEqualTo("field🚀📦💳");
  }

  @Test
  void shouldHandleFieldWithLongDescription() {
    String longDesc = "A".repeat(500);
    field.description = longDesc;

    assertThat(field.description).isEqualTo(longDesc);
    assertThat(field.description).hasSize(500);
  }

  @Test
  void shouldHandleFieldWithSpecialCharactersInDescription() {
    field.description = "Field description with special chars: @#$%^&*()_+-=[]{}|;':\",./<>?";

    assertThat(field.description).isEqualTo("Field description with special chars: @#$%^&*()_+-=[]{}|;':\",./<>?");
  }

  @Test
  void shouldHandleFieldWithUnicodeInDescription() {
    field.description = "Field description with unicode: 🚀📦💳";

    assertThat(field.description).isEqualTo("Field description with unicode: 🚀📦💳");
  }

  @Test
  void shouldHandleComplexNestedStructure() {
    // Create a complex nested structure
    BprintSchema.Entity nestedEntity = new BprintSchema.Entity();
    nestedEntity.name = "ComplexEntity";
    nestedEntity.description = "A complex entity with many fields";

    BprintSchema.PrimaryKey nestedPk = new BprintSchema.PrimaryKey();
    nestedPk.partitionKey = "id";
    nestedPk.sortKey = "timestamp";

    List<BprintSchema.Field> nestedFields = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
      BprintSchema.Field f = new BprintSchema.Field();
      f.name = "field" + i;
      f.type = i % 4 == 0 ? "string" : i % 4 == 1 ? "number" : i % 4 == 2 ? "bool" : "timestamp";
      f.required = i % 2 == 0;
      f.description = "Field " + i + " description";

      if (f.type.equals("string") && i % 3 == 0) {
        f.enumValues = List.of("option" + i + "a", "option" + i + "b", "option" + i + "c");
      }

      if (f.type.equals("bool") && i % 2 == 0) {
        f.defaultValue = i % 4 == 0;
      }

      if (f.type.equals("number") && i % 2 == 0) {
        f.defaultValue = (double) i;
      }

      nestedFields.add(f);
    }

    nestedEntity.primaryKey = nestedPk;
    nestedEntity.fields = nestedFields;

    BprintSchema.Annotations nestedAnnotations = new BprintSchema.Annotations();
    nestedAnnotations.pii = true;
    nestedAnnotations.retention = "10years";
    nestedAnnotations.encryption = "AES-256";

    nestedEntity.annotations = nestedAnnotations;

    // Verify the complex structure
    assertThat(nestedEntity.name).isEqualTo("ComplexEntity");
    assertThat(nestedEntity.description).isEqualTo("A complex entity with many fields");
    assertThat(nestedEntity.primaryKey.partitionKey).isEqualTo("id");
    assertThat(nestedEntity.primaryKey.sortKey).isEqualTo("timestamp");
    assertThat(nestedEntity.fields).hasSize(10);
    assertThat(nestedEntity.annotations.pii).isTrue();
    assertThat(nestedEntity.annotations.retention).isEqualTo("10years");
    assertThat(nestedEntity.annotations.encryption).isEqualTo("AES-256");

    // Verify field types
    assertThat(nestedEntity.fields.get(0).type).isEqualTo("string");
    assertThat(nestedEntity.fields.get(1).type).isEqualTo("number");
    assertThat(nestedEntity.fields.get(2).type).isEqualTo("bool");
    assertThat(nestedEntity.fields.get(3).type).isEqualTo("timestamp");
    assertThat(nestedEntity.fields.get(4).type).isEqualTo("string");
  }
}
