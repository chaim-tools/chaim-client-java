package io.chaim.generators.java;

import com.squareup.javapoet.*;
import io.chaim.core.model.BprintSchema;
import io.chaim.cdk.TableMetadata;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Enhanced Java generator that creates DTOs, ChaimConfig, and ChaimMapperClient
 */
public class JavaGenerator {

    public void generate(BprintSchema schema, String pkg, Path outDir) throws IOException {
        generate(schema, pkg, outDir, null);
    }

    public void generate(BprintSchema schema, String pkg, Path outDir, TableMetadata tableMetadata) throws IOException {
        // Derive entity name from namespace or entity.name
        String entityName = deriveEntityName(schema);
        
        // Generate entity DTO
        TypeSpec entityType = generateEntity(schema.schemaVersion, schema.entity, entityName);
        JavaFile.builder(pkg, entityType)
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);

        // Generate ChaimConfig if we have table metadata
        if (tableMetadata != null) {
            TypeSpec configType = generateChaimConfig(tableMetadata, pkg);
            JavaFile.builder(pkg + ".config", configType)
                .skipJavaLangImports(true)
                .build()
                .writeTo(outDir);

            // Generate ChaimMapperClient
            TypeSpec mapperType = generateChaimMapperClient(entityName, pkg);
            JavaFile.builder(pkg + ".mapper", mapperType)
                .skipJavaLangImports(true)
                .build()
                .writeTo(outDir);
        }
    }
    
    /**
     * Derive entity name from schema.
     * Priority: entity.name > last part of namespace (capitalized) > "Entity"
     */
    private String deriveEntityName(BprintSchema schema) {
        // First try entity.name
        if (schema.entity != null && schema.entity.name != null && !schema.entity.name.isEmpty()) {
            return schema.entity.name;
        }
        
        // Then try deriving from namespace (e.g., "example.users" -> "Users")
        if (schema.namespace != null && !schema.namespace.isEmpty()) {
            String[] parts = schema.namespace.split("\\.");
            String lastPart = parts[parts.length - 1];
            // Capitalize first letter
            return lastPart.substring(0, 1).toUpperCase() + lastPart.substring(1);
        }
        
        // Fallback
        return "Entity";
    }

    private TypeSpec generateEntity(Double schemaVersion, BprintSchema.Entity entity, String entityName) {
        TypeSpec.Builder tb = TypeSpec.classBuilder(entityName)
            .addModifiers(Modifier.PUBLIC);

        // chaimVersion constant
        FieldSpec cv = FieldSpec.builder(Double.class, "chaimVersion", Modifier.PRIVATE, Modifier.FINAL)
            .initializer("$L", schemaVersion)
            .build();
        tb.addField(cv);

        // fields + getters/setters
        for (BprintSchema.Field field : entity.fields) {
            ClassName type = mapType(field.type);
            FieldSpec fieldSpec = FieldSpec.builder(type, field.name, Modifier.PRIVATE)
                .build();
            tb.addField(fieldSpec);

            // Add getter
            MethodSpec getter = MethodSpec.methodBuilder("get" + cap(field.name))
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addStatement("return this.$L", field.name)
                .build();
            tb.addMethod(getter);

            // Add setter
            MethodSpec setter = MethodSpec.methodBuilder("set" + cap(field.name))
                .addModifiers(Modifier.PUBLIC)
                .addParameter(type, field.name)
                .addStatement("this.$L = $L", field.name, field.name)
                .build();
            tb.addMethod(setter);
        }

        // getChaimVersion
        tb.addMethod(MethodSpec.methodBuilder("getChaimVersion")
            .addModifiers(Modifier.PUBLIC)
            .returns(Double.class)
            .addStatement("return this.chaimVersion")
            .build());

        // validate()
        tb.addMethod(MethodSpec.methodBuilder("validate")
            .addModifiers(Modifier.PUBLIC)
            .addException(IllegalArgumentException.class)
            .addCode(buildValidateBody(entity, entityName))
            .build());

        return tb.build();
    }

    private TypeSpec generateChaimConfig(TableMetadata tableMetadata, String pkg) {
        TypeSpec.Builder tb = TypeSpec.classBuilder("ChaimConfig")
            .addModifiers(Modifier.PUBLIC);

        // Static fields for table metadata
        FieldSpec tableName = FieldSpec.builder(String.class, "TABLE_NAME", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.getTableName())
            .build();
        tb.addField(tableName);

        FieldSpec tableArn = FieldSpec.builder(String.class, "TABLE_ARN", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.getTableArn())
            .build();
        tb.addField(tableArn);

        FieldSpec region = FieldSpec.builder(String.class, "REGION", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$S", tableMetadata.getRegion())
            .build();
        tb.addField(region);

        // createMapper() method - note: returns mapper from same package hierarchy
        MethodSpec createMapper = MethodSpec.methodBuilder("createMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ClassName.get(pkg + ".mapper", "ChaimMapperClient"))
            .addStatement("return new $T()", ClassName.get(pkg + ".mapper", "ChaimMapperClient"))
            .build();
        tb.addMethod(createMapper);

        // getTableName() method
        MethodSpec getTableName = MethodSpec.methodBuilder("getTableName")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(String.class)
            .addStatement("return TABLE_NAME")
            .build();
        tb.addMethod(getTableName);

        // getTableArn() method
        MethodSpec getTableArn = MethodSpec.methodBuilder("getTableArn")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(String.class)
            .addStatement("return TABLE_ARN")
            .build();
        tb.addMethod(getTableArn);

        // getRegion() method
        MethodSpec getRegion = MethodSpec.methodBuilder("getRegion")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(String.class)
            .addStatement("return REGION")
            .build();
        tb.addMethod(getRegion);

        return tb.build();
    }

    private TypeSpec generateChaimMapperClient(String entityName, String modelPackage) {
        TypeSpec.Builder tb = TypeSpec.classBuilder("ChaimMapperClient")
            .addModifiers(Modifier.PUBLIC);

        // Constructor
        MethodSpec constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addStatement("// TODO: Initialize DynamoDB client")
            .build();
        tb.addMethod(constructor);

        // save() method
        MethodSpec save = MethodSpec.methodBuilder("save")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get(modelPackage, entityName), "item")
            .addStatement("// TODO: Implement save operation")
            .addStatement("throw new $T(\"Not implemented yet\")", UnsupportedOperationException.class)
            .build();
        tb.addMethod(save);

        // findById() method
        MethodSpec findById = MethodSpec.methodBuilder("findById")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(TypeVariableName.get("T"))
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), TypeVariableName.get("T")), "entityClass")
            .addParameter(String.class, "id")
            .returns(ParameterizedTypeName.get(ClassName.get("java.util", "Optional"), TypeVariableName.get("T")))
            .addStatement("// TODO: Implement findById operation")
            .addStatement("throw new $T(\"Not implemented yet\")", UnsupportedOperationException.class)
            .build();
        tb.addMethod(findById);

        // findByField() method
        MethodSpec findByField = MethodSpec.methodBuilder("findByField")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(TypeVariableName.get("T"))
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), TypeVariableName.get("T")), "entityClass")
            .addParameter(String.class, "fieldName")
            .addParameter(Object.class, "fieldValue")
            .returns(ParameterizedTypeName.get(ClassName.get("java.util", "List"), TypeVariableName.get("T")))
            .addStatement("// TODO: Implement field-based query")
            .addStatement("throw new $T(\"Not implemented yet\")", UnsupportedOperationException.class)
            .build();
        tb.addMethod(findByField);

        return tb.build();
    }

    private CodeBlock buildValidateBody(BprintSchema.Entity entity, String entityName) {
        CodeBlock.Builder cb = CodeBlock.builder();
        for (BprintSchema.Field field : entity.fields) {
            // Required field check
            if (field.required) {
                cb.addStatement("if (this.$L == null) throw new IllegalArgumentException($S)",
                    field.name, entityName + "." + field.name + " is required");
            }
            
            // Enum validation for string fields
            if (field.enumValues != null && !field.enumValues.isEmpty() && "string".equals(field.type)) {
                String enumValuesStr = String.join(", ", field.enumValues);
                cb.beginControlFlow("if (this.$L != null)", field.name);
                // Build Set.of("val1", "val2", ...) with proper formatting
                StringBuilder setOfArgs = new StringBuilder();
                for (int i = 0; i < field.enumValues.size(); i++) {
                    if (i > 0) setOfArgs.append(", ");
                    setOfArgs.append("\"").append(field.enumValues.get(i)).append("\"");
                }
                cb.addStatement("java.util.Set<String> allowedValues = java.util.Set.of($L)", setOfArgs.toString());
                cb.addStatement("if (!allowedValues.contains(this.$L)) throw new IllegalArgumentException($S + this.$L)",
                    field.name, 
                    entityName + "." + field.name + " must be one of [" + enumValuesStr + "], got: ",
                    field.name);
                cb.endControlFlow();
            }
            
            // Constraints validation
            if (field.constraints != null) {
                BprintSchema.Constraints c = field.constraints;
                
                // String constraints
                if ("string".equals(field.type)) {
                    if (c.minLength != null) {
                        cb.beginControlFlow("if (this.$L != null && this.$L.length() < $L)", field.name, field.name, c.minLength);
                        cb.addStatement("throw new IllegalArgumentException($S)",
                            entityName + "." + field.name + " must be at least " + c.minLength + " characters");
                        cb.endControlFlow();
                    }
                    if (c.maxLength != null) {
                        cb.beginControlFlow("if (this.$L != null && this.$L.length() > $L)", field.name, field.name, c.maxLength);
                        cb.addStatement("throw new IllegalArgumentException($S)",
                            entityName + "." + field.name + " must be at most " + c.maxLength + " characters");
                        cb.endControlFlow();
                    }
                    if (c.pattern != null) {
                        cb.beginControlFlow("if (this.$L != null && !this.$L.matches($S))", field.name, field.name, c.pattern);
                        cb.addStatement("throw new IllegalArgumentException($S)",
                            entityName + "." + field.name + " must match pattern: " + c.pattern);
                        cb.endControlFlow();
                    }
                }
                
                // Number constraints
                if ("number".equals(field.type)) {
                    if (c.min != null) {
                        cb.beginControlFlow("if (this.$L != null && this.$L < $L)", field.name, field.name, c.min);
                        cb.addStatement("throw new IllegalArgumentException($S)",
                            entityName + "." + field.name + " must be >= " + c.min);
                        cb.endControlFlow();
                    }
                    if (c.max != null) {
                        cb.beginControlFlow("if (this.$L != null && this.$L > $L)", field.name, field.name, c.max);
                        cb.addStatement("throw new IllegalArgumentException($S)",
                            entityName + "." + field.name + " must be <= " + c.max);
                        cb.endControlFlow();
                    }
                }
            }
        }
        return cb.build();
    }

    private static ClassName mapType(String type) {
        return switch (type) {
            case "string" -> ClassName.get(String.class);
            case "number" -> ClassName.get(Double.class);
            case "boolean", "bool" -> ClassName.get(Boolean.class);
            case "timestamp" -> ClassName.get(java.time.Instant.class);
            default -> ClassName.get(Object.class);
        };
    }

    private static String cap(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
