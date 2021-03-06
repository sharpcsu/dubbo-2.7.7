package org.apache.dubbo.rpc;

import java.util.HashMap;
import java.util.Map;

/**
 * This class provides map adapters to support attachments in RpcContext, Invocation and Result switch from
 * <String, String> to <String, Object>
 */
public class AttachmentsAdapter {

    public static class ObjectToStringMap extends HashMap<String, String> {
        private Map<String, Object> attachments;

        public ObjectToStringMap(Map<String, Object> attachments) {
            this.attachments = attachments;
        }

        @Override
        public String get(Object key) {
            Object obj = attachments.get(key);
            return convert(obj);
        }

        @Override
        public String put(String key, String value) {
            Object obj = attachments.put(key, value);
            return convert(obj);
        }

        @Override
        public String remove(Object key) {
            Object obj = attachments.remove(key);
            return convert(obj);
        }

        private String convert(Object obj) {
            if (obj instanceof String) {
                return (String) obj;
            }
            return null; // or JSON.toString(obj);
        }
    }

    public static class StringToObjectMap extends HashMap<String, Object> {
        private Map<String, String> attachments;

        public StringToObjectMap(Map<String, String> attachments) {
            this.attachments = attachments;
        }

        @Override
        public Object get(Object key) {
            return attachments.get(key);
        }

        @Override
        public Object put(String key, Object value) {
            if (value instanceof String) {
                return attachments.put(key, (String) value);
            }
            return null; // or JSON.toString(obj);
        }

        @Override
        public Object remove(Object key) {
            return attachments.remove(key);
        }
    }
}
