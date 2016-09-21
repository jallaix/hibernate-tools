package info.jallaix.hibernate;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.hibernate.collection.internal.AbstractPersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.pojo.javassist.JavassistProxyFactory;

import javax.persistence.Id;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    public static <T> Pair<T, List<Queue<Field>>> unproxyDetachedRecursively(T maybeProxy) {

        if (maybeProxy == null) {
            return null;
        }

        LinkedList<Field> fieldPath = new LinkedList<>();
        fieldPath.add(null);                             // Null indicates root object

        return unproxyDetachedRecursively(maybeProxy, new ArrayList<>(), new HashSet<>(), fieldPath);
    }

    /**
     * Recursively replace Hibernate proxies by POJOs.
     *
     * @param maybeProxy Object that may contain Hibernate proxies
     * @param visited    List of objects without proxy
     * @return Object without Hibernate proxies
     */
    @SuppressWarnings("unchecked")
    private static <T> Pair<T, List<Queue<Field>>> unproxyDetachedRecursively(T maybeProxy, List<Queue<Field>> uninitializedProxyPaths, HashSet<Object> visited, LinkedList<Field> fieldPath) {

        if (maybeProxy == null) {
            return null;
        }

        // Get the class to un-proxy
        Class<T> clazz = getUnproxyClass(maybeProxy);

        // Un-proxy the bean
        Pair<T, Boolean> unproxyResult = unproxyDetachedSingleBean(maybeProxy, clazz);
        T unproxiedEntity = unproxyResult.getLeft();

        // Add the field path to the list of uninitialized proxy paths if the un-proxied bean is uninitialized
        if (!unproxyResult.getRight()) {
            uninitializedProxyPaths.add(new LinkedList<Field>(fieldPath));
            return new ImmutablePair<>(unproxiedEntity, uninitializedProxyPaths);
        }

        // Return if the bean is already un-proxied
        if (visited.contains(unproxiedEntity)) {
            return new ImmutablePair<>(unproxiedEntity, uninitializedProxyPaths);
        } else {
            visited.add(unproxiedEntity);
        }

        // Un-proxy contained fields
        for (Field field : clazz.getDeclaredFields()) {

            // Final fields aren't touched
            if ((field.getModifiers() & Modifier.FINAL) == Modifier.FINAL) {
                continue;
            }

            // Add the current field to the field path
            fieldPath.addLast(field);

            // Get the field value
            Object fieldValue;
            try {
                field.setAccessible(true);
                fieldValue = field.get(unproxiedEntity);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            // Un-proxy an proxied collection field
            if (fieldValue instanceof AbstractPersistentCollection && !((AbstractPersistentCollection) fieldValue).wasInitialized()) {
                fieldValue = null;
                uninitializedProxyPaths.add(new LinkedList<Field>(fieldPath));
            }
            // Un-proxy an array
            else if (fieldValue instanceof Object[]) {

                Object[] valueArray = (Object[]) fieldValue;
                Object[] result = (Object[]) Array.newInstance(fieldValue.getClass(), valueArray.length);
                for (int i = 0; i < valueArray.length; i++) {

                    Pair<Object, List<Queue<Field>>> itemUnproxyResult = unproxyDetachedRecursively(valueArray[i], uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = itemUnproxyResult.getRight();

                    result[i] = itemUnproxyResult.getLeft();
                }
                fieldValue = result;
            }
            // Un-proxy a set
            else if (fieldValue instanceof Set) {

                Set<?> valueSet = (Set<?>) fieldValue;
                Set<Object> result = new HashSet<>();
                for (Object o : valueSet) {

                    Pair<Object, List<Queue<Field>>> itemUnproxyResult = unproxyDetachedRecursively(o, uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = itemUnproxyResult.getRight();

                    result.add(itemUnproxyResult.getLeft());
                }
                fieldValue = result;
            }
            // Un-proxy a map
            else if (fieldValue instanceof Map) {

                Map<?, ?> valueMap = (Map<?, ?>) fieldValue;
                Map<Object, Object> result = new HashMap<>();
                for (Object o : valueMap.keySet()) {

                    Pair<Object, List<Queue<Field>>> keyUnproxyResult = unproxyDetachedRecursively(o, uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = keyUnproxyResult.getRight();
                    Pair<Object, List<Queue<Field>>> valueUnproxyResult = unproxyDetachedRecursively(valueMap.get(o), uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = valueUnproxyResult.getRight();

                    result.put(keyUnproxyResult.getLeft(), valueUnproxyResult.getLeft());
                }
                fieldValue = result;
            }
            // Un-proxy a list
            else if (fieldValue instanceof List) {

                List<?> valueList = (List<?>) fieldValue;
                List<Object> result = new ArrayList<>(valueList.size());
                for (Object o : valueList) {

                    Pair<Object, List<Queue<Field>>> itemUnproxyResult = unproxyDetachedRecursively(o, uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = itemUnproxyResult.getRight();

                    result.add(itemUnproxyResult.getLeft());
                }
                fieldValue = result;
            }
            // Un-proxy another field type
            else {
                Pair<Object, List<Queue<Field>>> fieldUnproxyResult = unproxyDetachedRecursively(fieldValue, uninitializedProxyPaths, visited, fieldPath);
                uninitializedProxyPaths = fieldUnproxyResult.getRight();

                fieldValue = fieldUnproxyResult.getLeft();
            }

            // Replace the field value by the un-proxied value
            try {
                field.set(unproxiedEntity, fieldValue);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            // Remove the current field from the field path
            fieldPath.removeLast();
        }

        return new ImmutablePair<>(unproxiedEntity, uninitializedProxyPaths);
    }

    private static <T> Class<T> getUnproxyClass(final T maybeProxy) {

        Class<T> clazz;
        if (maybeProxy instanceof HibernateProxy) {

            HibernateProxy proxy = (HibernateProxy) maybeProxy;
            if (Hibernate.isInitialized(proxy))
                clazz = (Class<T>) proxy.getHibernateLazyInitializer().getImplementation().getClass();
            else
                clazz = (Class<T>) proxy.getHibernateLazyInitializer().getPersistentClass();
        } else {
            clazz = (Class<T>) maybeProxy.getClass();
        }

        return clazz;
    }

    /**
     * Replace an Hibernate proxy by a POJO and indicate if the proxy was initialized.
     * If the entity isn't a proxy, the data is considered initialized.
     *
     * @param maybeProxy Object that may contain an Hibernate proxy
     * @param baseClass  Proxy base class
     * @return Object without Hibernate proxy
     */
    private static <T> Pair<T, Boolean> unproxyDetachedSingleBean(final T maybeProxy, final Class<T> baseClass) {

        if (maybeProxy == null) {
            return null;
        }

        if (maybeProxy instanceof HibernateProxy) {

            if (Hibernate.isInitialized(maybeProxy))    // Linked POJO is returned if there is an initialized proxy

                return new ImmutablePair<>(
                        baseClass.cast(((HibernateProxy) maybeProxy).getHibernateLazyInitializer().getImplementation()),
                        true);

            else {  // POJO built with identifier is returned if there is an uninitialized proxy

                // Find the identifier field in the entity class
                Field identifierField = getIdentifierField(baseClass);
                identifierField.setAccessible(true);

                // Create an entity bean initialized with the proxy's identifier
                final T uninitializedEntity;
                try {
                    uninitializedEntity = baseClass.newInstance();
                    identifierField.set(uninitializedEntity, ((HibernateProxy) maybeProxy).getHibernateLazyInitializer().getIdentifier());
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                return new ImmutablePair<>(uninitializedEntity, false);
            }
        }
        // Original POJO is returned if there is no proxy
        else
            return new ImmutablePair<>(baseClass.cast(maybeProxy), true);
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

    /**
     * Find the field used as identifier for the entity.
     *
     * @param <T>           The entity type
     * @param documentClass The entity class
     * @return The identifier field
     */
    private static <T> Field getIdentifierField(Class<T> documentClass) {

        // Find field in entity class with @Id annotation
        for (Field field : documentClass.getDeclaredFields()) {
            if (field.getDeclaredAnnotation(Id.class) != null) {
                field.setAccessible(true);
                return field;
            }
        }

        return null;
    }
}
