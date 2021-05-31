package org.apache.dubbo.auth.model;

/**
 * The model of AK/SK pair
 */
public class AccessKeyPair {
    private String accessKey;
    private String secretKey;
    private String consumerSide;
    private String providerSide;
    private String creator;
    private String options;

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getConsumerSide() {
        return consumerSide;
    }

    public void setConsumerSide(String consumerSide) {
        this.consumerSide = consumerSide;
    }

    public String getProviderSide() {
        return providerSide;
    }

    public void setProviderSide(String providerSide) {
        this.providerSide = providerSide;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    @Override
    public String toString() {
        return "AccessKeyPair{" +
                "accessKey='" + accessKey + '\'' +
                ", secretKey='" + secretKey + '\'' +
                ", consumerSide='" + consumerSide + '\'' +
                ", providerSide='" + providerSide + '\'' +
                ", creator='" + creator + '\'' +
                ", options='" + options + '\'' +
                '}';
    }
}
