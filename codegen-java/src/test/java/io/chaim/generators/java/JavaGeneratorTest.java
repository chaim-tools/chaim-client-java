package io.chaim.generators.java;

import io.chaim.core.model.BprintSchema;
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

  private BprintSchema validSchema;
  private JavaGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new JavaGenerator();

    validSchema = new BprintSchema();
    validSchema.schemaVersion = "v1";
    validSchema.namespace = "acme.orders";
    validSchema.description = "Basic order management system";

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
    validSchema.entity = e;
  }

  @Test
  void generatesSimpleEntity() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generate(validSchema, "com.example.model", out);

    Path file = out.resolve("com/example/model/Order.java");
    assertThat(Files.exists(file)).isTrue();

    String content = Files.readString(file);
    assertThat(content).contains("public class Order");
    assertThat(content).contains("private String orderId");
    assertThat(content).contains("public String getOrderId()");
    assertThat(content).contains("public void setOrderId(String orderId)");
  }

  @Test
  void generatesEntityWithMultipleFields() throws Exception {
    // Add more fields to the schema
    BprintSchema.Field amountField = new BprintSchema.Field();
    amountField.name = "amount";
    amountField.type = "number";
    amountField.required = true;

    BprintSchema.Field activeField = new BprintSchema.Field();
    activeField.name = "isActive";
    activeField.type = "bool";
    activeField.required = false;

    validSchema.entity.fields = List.of(
        validSchema.entity.fields.get(0), // orderId
        amountField,
        activeField
    );

    Path out = tempDir.resolve("generated");
    generator.generate(validSchema, "com.example.model", out);

    Path file = out.resolve("com/example/model/Order.java");
    String content = Files.readString(file);

    assertThat(content).contains("private Double amount");
    assertThat(content).contains("private Boolean isActive");
    assertThat(content).contains("public Double getAmount()");
    assertThat(content).contains("public Boolean getIsActive()");
  }

  @Test
  void generatesValidationMethod() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generate(validSchema, "com.example.model", out);

    Path file = out.resolve("com/example/model/Order.java");
    String content = Files.readString(file);

    assertThat(content).contains("public void validate()");
    assertThat(content).contains("if (this.orderId == null)");
    assertThat(content).contains("throw new IllegalArgumentException");
  }

  @Test
  void generatesChaimVersion() throws Exception {
    Path out = tempDir.resolve("generated");
    generator.generate(validSchema, "com.example.model", out);

    Path file = out.resolve("com/example/model/Order.java");
    String content = Files.readString(file);

    assertThat(content).contains("private final String chaimVersion = \"v1\"");
    assertThat(content).contains("public String getChaimVersion()");
  }
}
