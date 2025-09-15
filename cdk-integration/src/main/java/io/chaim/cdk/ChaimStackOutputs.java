package io.chaim.cdk;

import java.util.Map;
import java.util.Optional;

/**
 * Represents the outputs from a CloudFormation stack that contains ChaimBinder constructs
 */
public class ChaimStackOutputs {

    private final String stackName;
    private final String region;
    private final String accountId;
    private final Map<String, String> outputs;

    public ChaimStackOutputs(String stackName, String region, String accountId, Map<String, String> outputs) {
        this.stackName = stackName;
        this.region = region;
        this.accountId = accountId;
        this.outputs = Map.copyOf(outputs);
    }

    public String getStackName() {
        return stackName;
    }

    public String getRegion() {
        return region;
    }

    public String getAccountId() {
        return accountId;
    }

    public Map<String, String> getOutputs() {
        return outputs;
    }

    public Optional<String> getOutput(String key) {
        return Optional.ofNullable(outputs.get(key));
    }

    public String getOutputOrThrow(String key) {
        return getOutput(key)
            .orElseThrow(() -> new RuntimeException("Required output not found: " + key));
    }

    /**
     * Gets the operating mode (oss or saas) from the stack outputs
     */
    public String getMode() {
        return getOutput("ChaimMode").orElse("oss");
    }

    /**
     * Checks if this stack is in SaaS mode
     */
    public boolean isSaaSMode() {
        return "saas".equals(getMode());
    }
}
