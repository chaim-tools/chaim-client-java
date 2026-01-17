package co.chaim.core.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BprintSchema {
  public Double schemaVersion;
  public String entityName;
  public String description;
  public PrimaryKey primaryKey;
  public List<Field> fields;
  public Annotations annotations;

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
    public Constraints constraints;
    public FieldAnnotations annotations;
  }

  /**
   * Field-level constraints for validation.
   * String constraints: minLength, maxLength, pattern
   * Number constraints: min, max
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Constraints {
    // String constraints
    public Integer minLength;
    public Integer maxLength;
    public String pattern;
    
    // Number constraints
    public Double min;
    public Double max;
  }

  /**
   * Field-level annotations for custom metadata.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class FieldAnnotations {
    // Extensible - add specific annotation fields as needed
  }

  public static class Annotations {
    public Boolean pii;
    public String retention;
    public String encryption;
  }

  public void require() {
    Objects.requireNonNull(schemaVersion, "schemaVersion is required");
    Objects.requireNonNull(entityName, "entityName is required");
    Objects.requireNonNull(description, "description is required");
    Objects.requireNonNull(primaryKey, "primaryKey is required");
    Objects.requireNonNull(fields, "fields is required");
  }
}
