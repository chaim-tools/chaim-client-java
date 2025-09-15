package io.chaim.cdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads CloudFormation stack outputs created by chaim-cdk ChaimBinder constructs
 */
public class CloudFormationReader {

    private final CloudFormationClient cloudFormationClient;
    private final StsClient stsClient;
    private final ObjectMapper objectMapper;

    public CloudFormationReader() {
        this.cloudFormationClient = CloudFormationClient.create();
        this.stsClient = StsClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public CloudFormationReader(CloudFormationClient cloudFormationClient, StsClient stsClient) {
        this.cloudFormationClient = cloudFormationClient;
        this.stsClient = stsClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Reads all Chaim-related outputs from a CloudFormation stack
     */
    public ChaimStackOutputs readStackOutputs(String stackName, String region) {
        try {
            // Get stack outputs
            DescribeStacksRequest request = DescribeStacksRequest.builder()
                .stackName(stackName)
                .build();

            DescribeStacksResponse response = cloudFormationClient.describeStacks(request);

            if (response.stacks().isEmpty()) {
                throw new RuntimeException("Stack not found: " + stackName);
            }

            Stack stack = response.stacks().get(0);
            List<Output> outputs = stack.outputs();

            // Get caller identity for account info
            GetCallerIdentityResponse identity = stsClient.getCallerIdentity();

            // Parse Chaim outputs
            Map<String, String> chaimOutputs = new HashMap<>();
            for (Output output : outputs) {
                if (output.outputKey().startsWith("Chaim")) {
                    chaimOutputs.put(output.outputKey(), output.outputValue());
                }
            }

            return new ChaimStackOutputs(
                stackName,
                region,
                identity.account(),
                chaimOutputs
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to read stack outputs for " + stackName, e);
        }
    }

    /**
     * Extracts table metadata from ChaimBinder outputs
     */
    public TableMetadata extractTableMetadata(ChaimStackOutputs stackOutputs, String tableName) {
        try {
            // Look for table-specific outputs
            String schemaDataKey = "ChaimSchemaData" + (tableName != null ? "_" + tableName : "");
            String tableMetadataKey = "ChaimTableMetadata" + (tableName != null ? "_" + tableName : "");

            String schemaDataJson = stackOutputs.getOutput(schemaDataKey)
                .orElseThrow(() -> new RuntimeException("Schema data not found for table: " + tableName));

            String tableMetadataJson = stackOutputs.getOutput(tableMetadataKey)
                .orElseThrow(() -> new RuntimeException("Table metadata not found for table: " + tableName));

            // Parse JSON
            JsonNode schemaNode = objectMapper.readTree(schemaDataJson);
            JsonNode metadataNode = objectMapper.readTree(tableMetadataJson);

            return new TableMetadata(
                tableName,
                metadataNode.get("tableArn").asText(),
                metadataNode.get("tableName").asText(),
                metadataNode.get("region").asText(),
                schemaNode
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract table metadata for " + tableName, e);
        }
    }

    /**
     * Lists all Chaim tables in a stack
     */
    public List<String> listChaimTables(ChaimStackOutputs stackOutputs) {
        return stackOutputs.getOutputs().keySet().stream()
            .filter(key -> key.startsWith("ChaimTableMetadata_"))
            .map(key -> key.substring("ChaimTableMetadata_".length()))
            .toList();
    }

    public void close() {
        cloudFormationClient.close();
        stsClient.close();
    }
}
