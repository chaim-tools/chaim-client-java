package io.chaim.core.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BprintSchema {
  public String schemaVersion;
  public String namespace;
  public String description;
  public Entity entity;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Entity {
    public String name;
    public PrimaryKey primaryKey;
    public List<Field> fields;
    public String description;
    public Annotations annotations;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PrimaryKey {
    public String partitionKey;
    public String sortKey;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Field {
    public String name;
    public String type;
    public boolean required;
    public String description;
    @JsonAlias({"default", "defaultValue"})
    public Object defaultValue;
    @JsonAlias({"enum", "enumValues"})
    public List<String> enumValues;
  }

  public static class Annotations {
    public Boolean pii;
    public String retention;
    public String encryption;
  }

  public void require() {
    Objects.requireNonNull(schemaVersion, "schemaVersion is required");
    Objects.requireNonNull(namespace, "namespace is required");
    Objects.requireNonNull(description, "description is required");
    Objects.requireNonNull(entity, "entity is required");
  }
}
