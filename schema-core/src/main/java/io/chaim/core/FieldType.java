package io.chaim.core;

public enum FieldType {
  string, number, bool, timestamp;

  public static boolean isValid(String s) {
    for (FieldType f : values()) {
      if (f.name().equals(s)) return true;
    }
    return false;
  }
}
