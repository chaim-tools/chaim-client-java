package io.chaim.cdk;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents metadata about a DynamoDB table and its associated schema
 */
public class TableMetadata {

    private final String tableName;
    private final String tableArn;
    private final String region;
    private final JsonNode schemaData;

    public TableMetadata(String tableName, String tableArn, String region, JsonNode schemaData) {
        this.tableName = tableName;
        this.tableArn = tableArn;
        this.region = region;
        this.schemaData = schemaData;
    }

    public TableMetadata(String tableName, String tableArn, String tableNameFromMetadata, String region, JsonNode schemaData) {
        this.tableName = tableName;
        this.tableArn = tableArn;
        this.region = region;
        this.schemaData = schemaData;
    }

    public String getTableName() {
        return tableName;
    }

    public String getTableArn() {
        return tableArn;
    }

    public String getRegion() {
        return region;
    }

    public JsonNode getSchemaData() {
        return schemaData;
    }

    /**
     * Gets the entity name from the schema data
     */
    public String getEntityName() {
        return schemaData.get("entity").get("name").asText();
    }

    /**
     * Gets the namespace from the schema data
     */
    public String getNamespace() {
        return schemaData.get("namespace").asText();
    }

    /**
     * Gets the schema version from the schema data
     */
    public String getSchemaVersion() {
        return schemaData.get("schemaVersion").asText();
    }
}
