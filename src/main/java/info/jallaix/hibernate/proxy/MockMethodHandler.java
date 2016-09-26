package info.jallaix.hibernate.proxy;

import javassist.util.proxy.MethodHandler;
import org.hibernate.LazyInitializationException;
import org.hibernate.proxy.LazyInitializer;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Method handler for the mocked Hibernate proxy.
 */
public class MockMethodHandler<T> implements MethodHandler, Serializable {

    private String identifierGetter;
    private LazyInitializer lazyInitializer;

    /**
     * Constructor with entity and identifier getter.
     *
     * @param entity           Entity
     * @param identifierGetter name for the identifier getter method
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    public MockMethodHandler(T entity, String identifierGetter) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        this.identifierGetter = identifierGetter;

        Serializable id = null;
        if (identifierGetter != null) {
            id = (Serializable) entity.getClass().getDeclaredMethod(identifierGetter).invoke(entity);
        }
        this.lazyInitializer = new MockLazyInitializer(entity, id);
    }

    @Override
    public Object invoke(Object self, Method m, Method proceed, Object[] args) throws Throwable {

        // The proxy method handler always throws a LazyInitializationException except when:
        // - the identifier is accessed
        // - the LazyInitializer is accessed
        if (identifierGetter != null && m.getName().equals(identifierGetter))
            return lazyInitializer.getIdentifier();
        else if (m.getName().equals("getHibernateLazyInitializer"))
            return lazyInitializer;
        else
            throw new LazyInitializationException("This object is not initialized.");
    }
}
