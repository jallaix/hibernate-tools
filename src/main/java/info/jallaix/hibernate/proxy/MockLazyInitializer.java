package info.jallaix.hibernate.proxy;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.proxy.LazyInitializer;

import java.io.Serializable;

/**
 * Mock for the {@link LazyInitializer} interface used by {@link MockMethodHandler}.
 */
public class MockLazyInitializer implements LazyInitializer, Serializable {

    private Object entity;
    private Serializable id;

    /**
     * Constructor with entity and identifier
     *
     * @param entity Entity
     * @param id     Identifier
     */
    public MockLazyInitializer(Object entity, Serializable id) {
        this.entity = entity;
        this.id = id;
    }

    @Override
    public void initialize() throws HibernateException {
    }

    /**
     * Get the identifier
     *
     * @return The identifier
     */
    @Override
    public Serializable getIdentifier() {
        return id;
    }

    @Override
    public void setIdentifier(Serializable serializable) {
    }

    @Override
    public String getEntityName() {
        return null;
    }

    /**
     * Get the POJO class
     *
     * @return The POJO class
     */
    @Override
    public Class getPersistentClass() {
        return entity.getClass();
    }

    /**
     * All mock proxies are uninitialized
     *
     * @return {@code true}
     */
    @Override
    public boolean isUninitialized() {
        return true;
    }

    /**
     * Get the POJO with identifier set
     *
     * @return The POJO
     */
    @Override
    public Object getImplementation() {
        return entity;
    }

    @Override
    public Object getImplementation(SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
        return null;
    }

    @Override
    public void setImplementation(Object o) {
    }

    @Override
    public boolean isReadOnlySettingAvailable() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void setReadOnly(boolean b) {
    }

    @Override
    public SharedSessionContractImplementor getSession() {
        return null;
    }

    @Override
    public void setSession(SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException {
    }

    @Override
    public void unsetSession() {
    }

    @Override
    public void setUnwrap(boolean b) {
    }

    @Override
    public boolean isUnwrap() {
        return false;
    }
}
