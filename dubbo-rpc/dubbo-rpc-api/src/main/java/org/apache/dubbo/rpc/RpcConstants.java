package org.apache.dubbo.rpc;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.constants.FilterConstants;
import org.apache.dubbo.common.constants.QosConstants;
import org.apache.dubbo.common.constants.RegistryConstants;
import org.apache.dubbo.common.constants.RemotingConstants;

/**
 * RpcConstants
 *
 * @deprecated Replace to org.apache.dubbo.common.Constants
 */
@Deprecated
public final class RpcConstants implements CommonConstants, QosConstants, FilterConstants,
        RegistryConstants, RemotingConstants {

    private RpcConstants() {
    }

}
