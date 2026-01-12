package co.chaim.generators.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata about a DynamoDB table passed from the CLI.
 * 
 * This is a simple data class - all values come from the OS cache snapshot
 * that was written by chaim-cdk. No AWS API calls are needed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TableMetadata(
    String tableName,
    String tableArn,
    String region
) {
    @JsonCreator
    public TableMetadata(
        @JsonProperty("tableName") String tableName,
        @JsonProperty("tableArn") String tableArn,
        @JsonProperty("region") String region
    ) {
        this.tableName = tableName;
        this.tableArn = tableArn;
        this.region = region;
    }
}
