package info.jallaix.hibernate;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.hibernate.LazyInitializationException;
import org.hibernate.collection.internal.AbstractPersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.proxy.pojo.javassist.JavassistProxyFactory;

import java.lang.reflect.*;
import java.util.*;

/**
 * Utility class for Hibernate containing the method :
 * <ul>
 * <li>{@link HibernateTools#unproxyDetachedRecursively(Object)} : Remove all proxies et persistent collections from an object graph</li>
 * </ul>
 */
public class HibernateTools {

    /**
     * Recursively replace Hibernate proxies by POJOs.
     *
     * @param maybeProxy Object that may contain Hibernate proxies
     * @return Object without Hibernate proxies
     */
    @SuppressWarnings("unused")
    public static <T> T unproxyDetachedRecursively(T maybeProxy) {

        if (maybeProxy == null) {
            return null;
        }

        return unproxyDetachedRecursively(maybeProxy, new HashSet<>());
    }

    /**
     * Recursively replace Hibernate proxies by POJOs.
     *
     * @param maybeProxy Object that may contain Hibernate proxies
     * @param visited    List of objects without proxy
     * @return Object without Hibernate proxies
     */
    @SuppressWarnings("unchecked")
    private static <T> T unproxyDetachedRecursively(T maybeProxy, HashSet<Object> visited) {

        if (maybeProxy == null) {
            return null;
        }

        // Get the class to un-proxy
        Class<T> clazz;
        if (maybeProxy instanceof HibernateProxy) {

            HibernateProxy proxy = (HibernateProxy) maybeProxy;
            LazyInitializer li = proxy.getHibernateLazyInitializer();
            if (li.isUninitialized())
                return null;
            else
                clazz = (Class<T>) li.getImplementation().getClass();
        } else {
            clazz = (Class<T>) maybeProxy.getClass();
        }

        // Un-proxy the bean
        T ret = unproxyDetachedSingleBean(maybeProxy, clazz);

        // Return if the bean is already un-proxied
        if (visited.contains(ret)) {
            return ret;
        } else {
            visited.add(ret);
        }

        // Un-proxy contained fields
        for (Field field : clazz.getDeclaredFields()) {

            // Final fields aren't touched
            if ((field.getModifiers() & Modifier.FINAL) == Modifier.FINAL) {
                continue;
            }

            // Get the field value
            Object value;
            try {
                field.setAccessible(true);
                value = field.get(ret);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            // Un-proxy an proxied collection field
            if (value instanceof AbstractPersistentCollection && !((AbstractPersistentCollection) value).wasInitialized()) {
                value = null;
            }
            // Un-proxy an array
            else if (value instanceof Object[]) {

                Object[] valueArray = (Object[]) value;
                Object[] result = (Object[]) Array.newInstance(value.getClass(), valueArray.length);
                for (int i = 0; i < valueArray.length; i++) {
                    result[i] = unproxyDetachedRecursively(valueArray[i], visited);
                }
                value = result;
            }
            // Un-proxy a set
            else if (value instanceof Set) {

                Set<?> valueSet = (Set<?>) value;
                Set<Object> result = new HashSet<>();
                for (Object o : valueSet) {
                    result.add(unproxyDetachedRecursively(o, visited));
                }
                value = result;
            }
            // Un-proxy a map
            else if (value instanceof Map) {

                Map<?, ?> valueMap = (Map<?, ?>) value;
                Map<Object, Object> result = new HashMap<>();
                for (Object o : valueMap.keySet()) {
                    result.put(unproxyDetachedRecursively(o, visited), unproxyDetachedRecursively(valueMap.get(o), visited));
                }
                value = result;
            }
            // Un-proxy a list
            else if (value instanceof List) {

                List<?> valueList = (List<?>) value;
                List<Object> result = new ArrayList<>(valueList.size());
                for (Object o : valueList) {
                    result.add(unproxyDetachedRecursively(o, visited));
                }
                value = result;
            }
            // Un-proxy a standard field
            else {
                value = unproxyDetachedRecursively(value, visited);
            }

            // Replace the field value by the un-proxied value
            try {
                field.set(ret, value);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return ret;
    }

    /**
     * Replace an Hibernate proxy by a POJO
     *
     * @param maybeProxy Object that may contain an Hibernate proxy
     * @param baseClass  Proxy base class
     * @return Object without Hibernate proxy
     */
    private static <T> T unproxyDetachedSingleBean(T maybeProxy, Class<T> baseClass) {

        if (maybeProxy == null) {
            return null;
        }

        if (maybeProxy instanceof HibernateProxy) {
            return baseClass.cast(((HibernateProxy) maybeProxy).getHibernateLazyInitializer().getImplementation());
        } else {
            return baseClass.cast(maybeProxy);
        }
    }

    public static <T> T createLazyProxy(Class<T> clazz) {

        final T proxy;

        final MethodHandler mi = new MethodHandler() {
            public Object invoke(Object self, Method m, Method proceed,
                                 Object[] args) throws Throwable {

                throw new LazyInitializationException("This object is not initialized.");
            }
        };

        ProxyFactory proxyFactory = JavassistProxyFactory.buildJavassistProxyFactory(clazz, new Class[0]);
        proxyFactory.setSuperclass(clazz);
        try {
            proxy = (T) proxyFactory.create(new Class[0], new Object[0], mi);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return proxy;
    }
}
