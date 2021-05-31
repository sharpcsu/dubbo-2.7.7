package org.apache.dubbo.auth;


public interface Constants {

    String SERVICE_AUTH = "auth";

    String AUTHENTICATOR = "authenticator";

    String DEFAULT_AUTHENTICATOR = "accesskey";

    String DEFAULT_ACCESS_KEY_STORAGE = "urlstorage";

    String ACCESS_KEY_STORAGE_KEY = "accessKey.storage";
    // the key starting  with "." shouldn't be output
    String ACCESS_KEY_ID_KEY = ".accessKeyId";
    // the key starting  with "." shouldn't be output
    String SECRET_ACCESS_KEY_KEY = ".secretAccessKey";

    String REQUEST_TIMESTAMP_KEY = "timestamp";

    String REQUEST_SIGNATURE_KEY = "signature";

    String AK_KEY = "ak";

    String SIGNATURE_STRING_FORMAT = "%s#%s#%s#%s";

    String PARAMETER_SIGNATURE_ENABLE_KEY = "param.sign";
}
