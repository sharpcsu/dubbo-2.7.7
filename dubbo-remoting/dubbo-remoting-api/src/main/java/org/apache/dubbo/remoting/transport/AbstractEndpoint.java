package org.apache.dubbo.remoting.transport;

import org.apache.dubbo.common.Resetable;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Codec;
import org.apache.dubbo.remoting.Codec2;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.transport.codec.CodecAdapter;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * AbstractEndpoint
 */
public abstract class AbstractEndpoint extends AbstractPeer implements Resetable {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEndpoint.class);

    private Codec2 codec;

    private int timeout;

    private int connectTimeout;

    public AbstractEndpoint(URL url, ChannelHandler handler) {
        //调用父类AbstractPeer的构造方法
        super(url, handler);
        //根据URL中的codec参数值，确定此处具体的Codec2实现类
        this.codec = getChannelCodec(url);
        //根据URL中的timeout参数确定timeout字段的值，默认1000
        this.timeout = url.getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        //根据URL中的connect.timeout参数确定connectTimeout字段的值，默认3000
        this.connectTimeout = url.getPositiveParameter(Constants.CONNECT_TIMEOUT_KEY, Constants.DEFAULT_CONNECT_TIMEOUT);
    }

    /**
     * 基于Dubbo SPI选择其扩展实现的
     */
    protected static Codec2 getChannelCodec(URL url) {
        //根据URL的codec参数获取扩展名
        String codecName = url.getParameter(Constants.CODEC_KEY, "telnet");
        if (ExtensionLoader.getExtensionLoader(Codec2.class).hasExtension(codecName)) {
            //通过ExtensionLoader加载并实例化Codec2的具体扩展实现
            return ExtensionLoader.getExtensionLoader(Codec2.class).getExtension(codecName);
        } else {
            //Codec2接口不存在相应的扩展名，就尝试从Codec这个老接口的扩展名中查找
            return new CodecAdapter(ExtensionLoader.getExtensionLoader(Codec.class)
                    .getExtension(codecName));
        }
    }

    /**
     * 根据传入的URL参数重置AbstractEndpoint的三个字段
     */
    @Override
    public void reset(URL url) {
        //检测当前AbstractEndpoint是否已关闭
        if (isClosed()) {
            throw new IllegalStateException("Failed to reset parameters "
                    + url + ", cause: Channel closed. channel: " + getLocalAddress());
        }
        try {
            if (url.hasParameter(TIMEOUT_KEY)) {
                int t = url.getParameter(TIMEOUT_KEY, 0);
                if (t > 0) {
                    this.timeout = t;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            if (url.hasParameter(Constants.CONNECT_TIMEOUT_KEY)) {
                int t = url.getParameter(Constants.CONNECT_TIMEOUT_KEY, 0);
                if (t > 0) {
                    this.connectTimeout = t;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            if (url.hasParameter(Constants.CODEC_KEY)) {
                this.codec = getChannelCodec(url);
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    @Deprecated
    public void reset(org.apache.dubbo.common.Parameters parameters) {
        reset(getUrl().addParameters(parameters.getParameters()));
    }

    protected Codec2 getCodec() {
        return codec;
    }

    protected int getTimeout() {
        return timeout;
    }

    protected int getConnectTimeout() {
        return connectTimeout;
    }

}
