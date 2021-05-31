package org.apache.dubbo.auth.spi;

import org.apache.dubbo.auth.model.AccessKeyPair;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.rpc.Invocation;


/**
 * This SPI Extension support us to store our {@link AccessKeyPair} or load {@link AccessKeyPair} from other
 * storage, such as filesystem.
 */
@SPI
public interface AccessKeyStorage {

    /**
     * get AccessKeyPair of this request
     *
     * @param url
     * @param invocation
     * @return
     */
    AccessKeyPair getAccessKey(URL url, Invocation invocation);
}
