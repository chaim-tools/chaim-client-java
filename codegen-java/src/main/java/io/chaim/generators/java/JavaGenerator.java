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
        // Generate entity DTO
        TypeSpec entityType = generateEntity(schema.schemaVersion, schema.entity);
        JavaFile.builder(pkg, entityType)
            .skipJavaLangImports(true)
            .build()
            .writeTo(outDir);

        // Generate ChaimConfig if we have table metadata
        if (tableMetadata != null) {
            TypeSpec configType = generateChaimConfig(tableMetadata);
            JavaFile.builder(pkg + ".config", configType)
                .skipJavaLangImports(true)
                .build()
                .writeTo(outDir);

            // Generate ChaimMapperClient
            TypeSpec mapperType = generateChaimMapperClient(schema.entity.name, pkg + ".model");
            JavaFile.builder(pkg + ".mapper", mapperType)
                .skipJavaLangImports(true)
                .build()
                .writeTo(outDir);
        }
    }

    private TypeSpec generateEntity(String schemaVersion, BprintSchema.Entity entity) {
        TypeSpec.Builder tb = TypeSpec.classBuilder(entity.name)
            .addModifiers(Modifier.PUBLIC);

        // chaimVersion constant
        FieldSpec cv = FieldSpec.builder(String.class, "chaimVersion", Modifier.PRIVATE, Modifier.FINAL)
            .initializer("$S", schemaVersion)
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
            .returns(String.class)
            .addStatement("return this.chaimVersion")
            .build());

        // validate()
        tb.addMethod(MethodSpec.methodBuilder("validate")
            .addModifiers(Modifier.PUBLIC)
            .addException(IllegalArgumentException.class)
            .addCode(buildValidateBody(entity))
            .build());

        return tb.build();
    }

    private TypeSpec generateChaimConfig(TableMetadata tableMetadata) {
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

        // createMapper() method
        MethodSpec createMapper = MethodSpec.methodBuilder("createMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ClassName.get("io.chaim.mapper", "ChaimMapperClient"))
            .addStatement("return new $T()", ClassName.get("io.chaim.mapper", "ChaimMapperClient"))
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

    private CodeBlock buildValidateBody(BprintSchema.Entity entity) {
        CodeBlock.Builder cb = CodeBlock.builder();
        for (BprintSchema.Field field : entity.fields) {
            if (field.required) {
                cb.addStatement("if (this.$L == null) throw new IllegalArgumentException($S)",
                    field.name, entity.name + "." + field.name + " is required");
            }
        }
        return cb.build();
    }

    private static ClassName mapType(String type) {
        return switch (type) {
            case "string" -> ClassName.get(String.class);
            case "number" -> ClassName.get(Double.class);
            case "bool" -> ClassName.get(Boolean.class);
            case "timestamp" -> ClassName.get(java.time.Instant.class);
            default -> ClassName.get(Object.class);
        };
    }

    private static String cap(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
