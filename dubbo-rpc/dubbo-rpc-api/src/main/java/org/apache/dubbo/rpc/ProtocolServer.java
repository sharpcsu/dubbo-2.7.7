package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.RemotingServer;

/**
 * RemotingServer的一层简单封装
 * Distinct from {@link RemotingServer}, each protocol holds one or more ProtocolServers(the number usually decides by port numbers),
 * while each ProtocolServer holds zero or one RemotingServer.
 */
public interface ProtocolServer {

    default RemotingServer getRemotingServer() {
        return null;
    }

    default void setRemotingServers(RemotingServer server) {
    }

    String getAddress();

    void setAddress(String address);

    default URL getUrl() {
        return null;
    }

    default void reset(URL url) {
    }

    void close();
}
