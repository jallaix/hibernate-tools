package info.jallaix.hibernate.proxy;

import javassist.util.proxy.MethodFilter;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * Method filter for the mocked Hibernate proxy.
 */
public class MockMethodFilter implements MethodFilter, Serializable {

    @Override
    public boolean isHandled(Method method) {

        // Handle all getter methods
        return method.getName().startsWith("get");
    }
}
