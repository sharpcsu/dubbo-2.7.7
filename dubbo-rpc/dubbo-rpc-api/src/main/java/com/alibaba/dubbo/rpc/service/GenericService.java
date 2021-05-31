package com.alibaba.dubbo.rpc.service;

@Deprecated
public interface GenericService extends org.apache.dubbo.rpc.service.GenericService {

    @Override
    Object $invoke(String method, String[] parameterTypes, Object[] args)
            throws com.alibaba.dubbo.rpc.service.GenericException;
}
