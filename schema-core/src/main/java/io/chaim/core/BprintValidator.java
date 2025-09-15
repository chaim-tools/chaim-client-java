package io.chaim.core;

import io.chaim.core.model.BprintSchema;

import java.util.HashSet;
import java.util.Set;

public final class BprintValidator {

  public static void validate(BprintSchema s) {
    if (isBlank(s.schemaVersion)) fail("schemaVersion required");
    if (isBlank(s.namespace)) fail("namespace required");
    if (isBlank(s.description)) fail("description required");
    if (s.entity == null) fail("entity is required");

    BprintSchema.Entity e = s.entity;
    if (isBlank(e.name)) fail("entity.name required");
    if (e.primaryKey == null || isBlank(e.primaryKey.partitionKey))
      fail("entity.primaryKey.partitionKey required for " + e.name);
    if (e.fields == null || e.fields.isEmpty()) fail("entity.fields must have at least one field for " + e.name);

    Set<String> fieldNames = new HashSet<>();
    for (BprintSchema.Field f : e.fields) {
      if (isBlank(f.name)) fail(e.name + ": field.name required");
      if (!fieldNames.add(f.name)) fail(e.name + ": duplicate field " + f.name);
      if (isBlank(f.type)) fail(e.name + "." + f.name + ": type required");
      if (!FieldType.isValid(f.type)) fail(e.name + "." + f.name + ": unsupported type " + f.type);

      // Validate enum values if present
      if (f.enumValues != null) {
        if (f.enumValues.isEmpty()) {
          fail(e.name + "." + f.name + ": enum values cannot be empty");
        }
        for (String enumValue : f.enumValues) {
          if (isBlank(enumValue)) fail(e.name + "." + f.name + ": enum values cannot be empty");
        }
      }
    }
  }

  private static boolean isBlank(String s) { return s == null || s.isEmpty(); }
  private static void fail(String msg) { throw new IllegalArgumentException(msg); }
}
