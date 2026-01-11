package io.chaim.generators.java;

import io.chaim.core.model.BprintSchema;
import io.chaim.cdk.TableMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;

/**
 * CLI entry point for the Java code generator.
 * 
 * Usage: java -jar codegen-java.jar --schema <json> --package <pkg> --output <dir> [--table-metadata <json>]
 */
public class Main {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    public static void main(String[] args) {
        try {
            String schemaJson = null;
            String packageName = null;
            String outputDir = null;
            String tableMetadataJson = null;
            
            // Parse arguments
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--schema":
                        schemaJson = args[++i];
                        break;
                    case "--package":
                        packageName = args[++i];
                        break;
                    case "--output":
                        outputDir = args[++i];
                        break;
                    case "--table-metadata":
                        tableMetadataJson = args[++i];
                        break;
                    default:
                        // Skip unknown args
                        break;
                }
            }
            
            // Validate required args
            if (schemaJson == null || packageName == null || outputDir == null) {
                System.err.println("Usage: java -jar codegen-java.jar --schema <json> --package <pkg> --output <dir> [--table-metadata <json>]");
                System.exit(1);
            }
            
            // Parse schema from JSON string
            BprintSchema schema = MAPPER.readValue(schemaJson, BprintSchema.class);
            
            // Parse table metadata if provided
            TableMetadata tableMetadata = null;
            if (tableMetadataJson != null && !tableMetadataJson.isEmpty()) {
                tableMetadata = parseTableMetadata(tableMetadataJson);
            }
            
            // Generate code
            JavaGenerator generator = new JavaGenerator();
            generator.generate(schema, packageName, Paths.get(outputDir), tableMetadata);
            
            System.out.println("Generated Java code for " + schema.entity.name + " in " + outputDir);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static TableMetadata parseTableMetadata(String json) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        
        String tableName = node.has("tableName") ? node.get("tableName").asText() : null;
        String tableArn = node.has("tableArn") ? node.get("tableArn").asText() : null;
        String region = node.has("region") ? node.get("region").asText() : null;
        
        // TableMetadata constructor requires schemaData as JsonNode, we can pass null or the node itself
        return new TableMetadata(tableName, tableArn, region, node);
    }
}
