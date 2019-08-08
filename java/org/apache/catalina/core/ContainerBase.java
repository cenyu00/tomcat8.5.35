/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.core;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ObjectName;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.MultiThrowable;
import org.apache.tomcat.util.res.StringManager;


/**
 * Container接口的抽象实现。提供了通用的函数方法。该类的实现类需要实现invoke()方法。
 *
 * 该抽象类的所有子类都将支持Pipeline，Pipeline对象定义了处理每个请求的方法invoke()，
 * 利用的是责任链设计模式。子类应该封装自己的功能对应的Valve中，并通过调用setBasic()方法
 * 将此Valve配置到Pipeline中。
 *
 * 有两种类型的监听器，一种是监听属性变化的，当一个属性变化时，会通知这种监听器。另一种是事件
 * 监听器，所有实现类将触发ContainerEvent事件，监听器可以通过addContainerListener方法添加。
 *
 */
public abstract class ContainerBase extends LifecycleMBeanBase
        implements Container {

    private static final Log log = LogFactory.getLog(ContainerBase.class);

    /**
     * Perform addChild with the permissions of this class.
     * addChild can be called with the XML parser on the stack,
     * this allows the XML parser to have fewer privileges than
     * Tomcat.
     */
    protected class PrivilegedAddChild implements PrivilegedAction<Void> {

        private final Container child;

        PrivilegedAddChild(Container child) {
            this.child = child;
        }

        @Override
        public Void run() {
            addChildInternal(child);
            return null;
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 保存当前容器的子容器的Map，name作为key
     */
    protected final HashMap<String, Container> children = new HashMap<>();


    /**
     * 后台进程的标记
     */
    protected int backgroundProcessorDelay = -1;


    /**
     * 当前容器的事件监听器列表，实现为CopyOnWriteArrayList，
     */
    protected final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 当前容器的Logger实现
     */
    protected Log logger = null;


    /**
     * 关联的logger名称
     */
    protected String logName = null;


    /**
     * 当前Container关联的集群
     */
    protected Cluster cluster = null;
    private final ReadWriteLock clusterLock = new ReentrantReadWriteLock();


    /**
     * 当然容器的名称
     */
    protected String name = null;


    /**
     * 当前容器的父容器
     */
    protected Container parent = null;


    /**
     * 父类加载器
     */
    protected ClassLoader parentClassLoader = null;


    /**
     * 当前容器相关的Pipeline对象。
     */
    protected final Pipeline pipeline = new StandardPipeline(this);


    /**
     * 当前容器相关的Realm
     */
    private volatile Realm realm = null;


    /**
     * 访问Realm的锁
     */
    private final ReadWriteLock realmLock = new ReentrantReadWriteLock();


    /**
     * 当前包的字符串管理
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * 当添加一个子容器时，自容器自动启动的flag
     */
    protected boolean startChildren = true;

    /**
     * 监听变量修改。
     */
    protected final PropertyChangeSupport support =
            new PropertyChangeSupport(this);


    /**
     * background 线程
     */
    private Thread thread = null;


    /**
     * background线程完成的信号量
     */
    private volatile boolean threadDone = false;


    /**
     * request的访问日志
     */
    protected volatile AccessLog accessLog = null;
    private volatile boolean accessLogScanComplete = false;


/**
 * 可用的线程数量，该线程是用于处理子容器的start和stop事件的。
 */
private int startStopThreads = 1;

    //线程池
    protected ThreadPoolExecutor startStopExecutor;


    // ------------------------------------------------------------- Properties

    @Override
    public int getStartStopThreads() {
        return startStopThreads;
    }

    /**
     * Handles the special values.
     */
    private int getStartStopThreadsInternal() {
        int result = getStartStopThreads();

        // 如果为正数，将不会被改变
        if (result > 0) {
            return result;
        }

        // Zero == Runtime.getRuntime().availableProcessors()
        // -ve  == Runtime.getRuntime().availableProcessors() + value
        // These two are the same
        result = Runtime.getRuntime().availableProcessors() + result;
        if (result < 1) {
            result = 1;
        }
        return result;
    }

    /**
     * 设置可用线程数的时候确保线程池不能为空
     */
    @Override
    public void setStartStopThreads(int startStopThreads) {
        this.startStopThreads = startStopThreads;

        // Use local copies to ensure thread safety
        ThreadPoolExecutor executor = startStopExecutor;
        if (executor != null) {
            int newThreads = getStartStopThreadsInternal();
            executor.setMaximumPoolSize(newThreads);
            executor.setCorePoolSize(newThreads);
        }
    }


    /**
     * 容器后台线程，只有设置backgroundProcessorDelay大于0的容器才会启动ContainerbackgroundProcessor线程，
     * 该线程会调用当前容器的backgroundProcess方法，并且递归调用backgroundProcessorDelay值小于或等于0的子容器方法。
     *
     * 从源码能看到StandardEngine设置了这个backgroundProcessorDelay值为10，所有只有StandardEngine容器启动ContainerbackgroundProcessor
     * 线程，而其他的StandardHost，StandardContext设置的值都是-1
     */
    @Override
    public int getBackgroundProcessorDelay() {
        return backgroundProcessorDelay;
    }


    /**
     * 设置backgroundProcessorDelay的值
     */
    @Override
    public void setBackgroundProcessorDelay(int delay) {
        backgroundProcessorDelay = delay;
    }


    /**
     * 返回当前Container的Logger
     */
    @Override
    public Log getLogger() {

        if (logger != null) {
            return (logger);
        }
        logger = LogFactory.getLog(getLogName());
        return (logger);

    }


    /**
     * Log组件的名称
     */
    @Override
    public String getLogName() {

        if (logName != null) {
            return logName;
        }
        String loggerName = null;
        Container current = this;
        while (current != null) {
            String name = current.getName();
            if ((name == null) || (name.equals(""))) {
                name = "/";
            } else if (name.startsWith("##")) {
                name = "/" + name;
            }
            loggerName = "[" + name + "]"
                + ((loggerName != null) ? ("." + loggerName) : "");
            current = current.getParent();
        }
        logName = ContainerBase.class.getName() + "." + loggerName;
        return logName;

    }


    /**
     * 返回当前容器所在的集群
     */
    @Override
    public Cluster getCluster() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            if (cluster != null) {
                return cluster;
            }

            if (parent != null) {
                return parent.getCluster();
            }

            return null;
        } finally {
            readLock.unlock();
        }
    }


    /*
     * Provide access to just the cluster component attached to this container.
     */

    /**
     * 通过获取锁的方式，来控制对附加在当前容器上的集群组件的访问权限。
     * @return
     */
    protected Cluster getClusterInternal() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            return cluster;
        } finally {
            readLock.unlock();
        }
    }


    /**
     * Set the Cluster with which this Container is associated.
     *
     * @param cluster The newly associated Cluster
     */
    @Override
    public void setCluster(Cluster cluster) {

        Cluster oldCluster = null;
        Lock writeLock = clusterLock.writeLock();
        writeLock.lock();
        try {
            // Change components if necessary
            oldCluster = this.cluster;
            if (oldCluster == cluster) {
                return;
            }
            this.cluster = cluster;

            // Stop the old component if necessary
            if (getState().isAvailable() && (oldCluster != null) &&
                (oldCluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldCluster).stop();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setCluster: stop: ", e);
                }
            }

            // Start the new component if necessary
            if (cluster != null) {
                cluster.setContainer(this);
            }

            if (getState().isAvailable() && (cluster != null) &&
                (cluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) cluster).start();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setCluster: start: ", e);
                }
            }
        } finally {
            writeLock.unlock();
        }

        // Report this property change to interested listeners
        support.firePropertyChange("cluster", oldCluster, cluster);
    }


    /**
     * Return a name string (suitable for use by humans) that describes this
     * Container.  Within the set of child containers belonging to a particular
     * parent, Container names must be unique.
     */
    @Override
    public String getName() {

        return (name);

    }


    /**
     * 设置当前容器的name，并通知监听器开始干活
     */
    @Override
    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException(sm.getString("containerBase.nullName"));
        }
        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }


    /**
     * Return if children of this container will be started automatically when
     * they are added to this container.
     *
     * @return <code>true</code> if the children will be started
     */
    public boolean getStartChildren() {

        return (startChildren);

    }


    /**
     * Set if children of this container will be started automatically when
     * they are added to this container.
     *
     * @param startChildren New value of the startChildren flag
     */
    public void setStartChildren(boolean startChildren) {

        boolean oldStartChildren = this.startChildren;
        this.startChildren = startChildren;
        support.firePropertyChange("startChildren", oldStartChildren, this.startChildren);
    }


    /**
     * Return the Container for which this Container is a child, if there is
     * one.  If there is no defined parent, return <code>null</code>.
     */
    @Override
    public Container getParent() {

        return (parent);

    }


    /**
     * Set the parent Container to which this Container is being added as a
     * child.  This Container may refuse to become attached to the specified
     * Container by throwing an exception.
     *
     * @param container Container to which this Container is being added
     *  as a child
     *
     * @exception IllegalArgumentException if this Container refuses to become
     *  attached to the specified Container
     */
    @Override
    public void setParent(Container container) {

        Container oldParent = this.parent;
        this.parent = container;
        support.firePropertyChange("parent", oldParent, this.parent);

    }


    /**
     * Return the parent class loader (if any) for this web application.
     * This call is meaningful only <strong>after</strong> a Loader has
     * been configured.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return (parentClassLoader);
        }
        if (parent != null) {
            return (parent.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());

    }


    /**
     * Set the parent class loader (if any) for this web application.
     * This call is meaningful only <strong>before</strong> a Loader has
     * been configured, and the specified value (if non-null) should be
     * passed as an argument to the class loader constructor.
     *
     *
     * @param parent The new parent class loader
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);

    }


    /**
     * Return the Pipeline object that manages the Valves associated with
     * this Container.
     */
    @Override
    public Pipeline getPipeline() {

        return (this.pipeline);

    }


    /**
     * Return the Realm with which this Container is associated.  If there is
     * no associated Realm, return the Realm associated with our parent
     * Container (if any); otherwise return <code>null</code>.
     */
    @Override
    public Realm getRealm() {

        Lock l = realmLock.readLock();
        l.lock();
        try {
            if (realm != null) {
                return (realm);
            }
            if (parent != null) {
                return (parent.getRealm());
            }
            return null;
        } finally {
            l.unlock();
        }
    }


    protected Realm getRealmInternal() {
        Lock l = realmLock.readLock();
        l.lock();
        try {
            return realm;
        } finally {
            l.unlock();
        }
    }

    /**
     * Set the Realm with which this Container is associated.
     *
     * @param realm The newly associated Realm
     */
    @Override
    public void setRealm(Realm realm) {

        Lock l = realmLock.writeLock();
        l.lock();
        try {
            // Change components if necessary
            Realm oldRealm = this.realm;
            if (oldRealm == realm) {
                return;
            }
            this.realm = realm;

            // Stop the old component if necessary
            if (getState().isAvailable() && (oldRealm != null) &&
                (oldRealm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldRealm).stop();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setRealm: stop: ", e);
                }
            }

            // Start the new component if necessary
            if (realm != null) {
                realm.setContainer(this);
            }
            if (getState().isAvailable() && (realm != null) &&
                (realm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) realm).start();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setRealm: start: ", e);
                }
            }

            // Report this property change to interested listeners
            support.firePropertyChange("realm", oldRealm, this.realm);
        } finally {
            l.unlock();
        }

    }


    // ------------------------------------------------------ Container Methods


    /**
     * Add a new child Container to those associated with this Container,
     * if supported.  Prior to adding this Container to the set of children,
     * the child's <code>setParent()</code> method must be called, with this
     * Container as an argument.  This method may thrown an
     * <code>IllegalArgumentException</code> if this Container chooses not
     * to be attached to the specified Container, in which case it is not added
     *
     * @param child New child Container to be added
     *
     * @exception IllegalArgumentException if this exception is thrown by
     *  the <code>setParent()</code> method of the child Container
     * @exception IllegalArgumentException if the new child does not have
     *  a name unique from that of existing children of this Container
     * @exception IllegalStateException if this Container does not support
     *  child Containers
     */
    @Override
    public void addChild(Container child) {
        if (Globals.IS_SECURITY_ENABLED) {
            PrivilegedAction<Void> dp =
                new PrivilegedAddChild(child);
            AccessController.doPrivileged(dp);
        } else {
            addChildInternal(child);
        }
    }

private void addChildInternal(Container child) {


    //1.判断子容器名称是否重复，并添加到子容器列表中
    //对保存子容器的HashMap加锁
    synchronized(children) {
        //判断子容器的列表里是不是已经有相同名称的容器，名称不能重复
        if (children.get(child.getName()) != null) {
            throw new IllegalArgumentException("addChild:  Child name '" +
                                               child.getName() +
                                               "' is not unique");
        }
        //把当前容器设置为子容器的父容器，并把子容器存入HashMap列表中
        child.setParent(this);
        children.put(child.getName(), child);
    }

    //2.执行子容器的start()方法，启动子容器
    //启动没有放在同步代码块中是因为start()操作会很慢，容易引发其他问题
    try {
        if ((getState().isAvailable() ||
                LifecycleState.STARTING_PREP.equals(getState())) &&
                startChildren) {
            child.start();
        }
    } catch (LifecycleException e) {
        log.error("ContainerBase.addChild: start: ", e);
        throw new IllegalStateException("ContainerBase.addChild: start: " + e);
    } finally {
        //3.触发add_child的事件，监听器可以开始工作了
        fireContainerEvent(ADD_CHILD_EVENT, child);
    }
}


    /**
     * 新增一个事件监听器
     */
    @Override
    public void addContainerListener(ContainerListener listener) {
        listeners.add(listener);
    }


    /**
     * 新增一个监听属性变化的监听器
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 根据指定的名称查找对应的子容器对象
     */
    @Override
    public Container findChild(String name) {
        if (name == null) {
            return null;
        }
        synchronized (children) {
            return children.get(name);
        }
    }


    /**
     * 返回当前容器的所有子容器
     * 操作方法是先构造一个数组，然后将多个value转成数组
     */
    @Override
    public Container[] findChildren() {
        synchronized (children) {
            Container results[] = new Container[children.size()];
            return children.values().toArray(results);
        }
    }


    /**
     * 获取当前容器所有的事件监听器
     */
    @Override
    public ContainerListener[] findContainerListeners() {
        ContainerListener[] results =
            new ContainerListener[0];
        return listeners.toArray(results);
    }


    /**
     * 删除指定的子容器
     */
    @Override
    public void removeChild(Container child) {

        if (child == null) {
            return;
        }

        //1.先对子容器进行stop()操作和destroy()操作
        try {
            if (child.getState().isAvailable()) {
                child.stop();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.removeChild: stop: ", e);
        }

        try {
            // child.destroy() may have already been called which would have
            // triggered this call. If that is the case, no need to destroy the
            // child again.
            if (!LifecycleState.DESTROYING.equals(child.getState())) {
                child.destroy();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.removeChild: destroy: ", e);
        }

        //2.在移除HashMap列表中的子容器，该操作使用synchronize保护。
        synchronized(children) {
            if (children.get(child.getName()) == null) {
                return;
            }
            children.remove(child.getName());
        }

        //3.告诉监听器可以出来干活了
        fireContainerEvent(REMOVE_CHILD_EVENT, child);
    }


    /**
     * 移除一个指定的事件监听器
     */
    @Override
    public void removeContainerListener(ContainerListener listener) {
        listeners.remove(listener);
    }


    /**
     * 移除一个属性改变监听器
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    /**
     * 容器的init()方法，主要是准备好一个线程池，使用LinkedBlockingQueue队列
     * 然后调用父类的initInternal()方法进行JMX的注册
     */
    @Override
    protected void initInternal() throws LifecycleException {
        //主要是创建一个线程池
        BlockingQueue<Runnable> startStopQueue = new LinkedBlockingQueue<>();
        startStopExecutor = new ThreadPoolExecutor(
                getStartStopThreadsInternal(),
                getStartStopThreadsInternal(), 10, TimeUnit.SECONDS,
                startStopQueue,
                new StartStopThreadFactory(getName() + "-startStop-"));
        startStopExecutor.allowCoreThreadTimeOut(true);
        super.initInternal();
    }


    /**
     *  容器的start()方法。
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Start our subordinate components, if any
        logger = null;
        getLogger();
        //获取cluster并调用cluster的start()方法
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).start();
        }
        //获取Realm，并调用realm的start()方法。
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).start();
        }

        //启动当前组件的所有字组件，循环遍历，使用线程池去启动
        Container children[] = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            results.add(startStopExecutor.submit(new StartChild(children[i])));
        }

        MultiThrowable multiThrowable = null;

        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Throwable e) {
                log.error(sm.getString("containerBase.threadedStartFailed"), e);
                if (multiThrowable == null) {
                    multiThrowable = new MultiThrowable();
                }
                multiThrowable.add(e);
            }

        }
        if (multiThrowable != null) {
            throw new LifecycleException(sm.getString("containerBase.threadedStartFailed"),
                    multiThrowable.getThrowable());
        }

        //启动Pipeline
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).start();
        }

        //设置当前生命周期的转态
        setState(LifecycleState.STARTING);

        // 启动当前容器的后台线程，定期去执行一些操作。
        threadStart();
    }


    /**
     * 当前容器的stop方法
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        //1.停止当前容器跑的后台线程
        threadStop();

        setState(LifecycleState.STOPPING);

        //2.停止当前容器的pipeline
        if (pipeline instanceof Lifecycle &&
                ((Lifecycle) pipeline).getState().isAvailable()) {
            ((Lifecycle) pipeline).stop();
        }

        //3.停止当前容器的子容器。
        Container children[] = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            results.add(startStopExecutor.submit(new StopChild(children[i])));
        }

        boolean fail = false;
        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString("containerBase.threadedStopFailed"), e);
                fail = true;
            }
        }
        if (fail) {
            throw new LifecycleException(
                    sm.getString("containerBase.threadedStopFailed"));
        }

        //4.停止当前容器的Realm组件。
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).stop();
        }
        //5.停止当前容器的cluster组件。
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).stop();
        }
    }

    /**
     * 当前容器的destroy方法
     * @throws LifecycleException
     */
    @Override
    protected void destroyInternal() throws LifecycleException {

        //1.执行当前的容器Realm的destroy()方法
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).destroy();
        }
        //2.执行当前容器cluster的destroy方法
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).destroy();
        }

        //3.执行Pipeline的destroy方法
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).destroy();
        }

        // 4.移除子容器
        for (Container child : findChildren()) {
            removeChild(child);
        }

        // Required if the child is destroyed directly.
        if (parent != null) {
            parent.removeChild(this);
        }

        // 5.停止当前容器的线程池
        if (startStopExecutor != null) {
            startStopExecutor.shutdownNow();
        }

        // 6.调用父类的方法，取消JMX的注册
        super.destroyInternal();
    }


    /**
     * Check this container for an access log and if none is found, look to the
     * parent. If there is no parent and still none is found, use the NoOp
     * access log.
     */
    @Override
    public void logAccess(Request request, Response response, long time,
            boolean useDefault) {

        boolean logged = false;

        if (getAccessLog() != null) {
            getAccessLog().log(request, response, time);
            logged = true;
        }

        if (getParent() != null) {
            // No need to use default logger once request/response has been logged
            // once
            getParent().logAccess(request, response, time, (useDefault && !logged));
        }
    }

    @Override
    public AccessLog getAccessLog() {

        if (accessLogScanComplete) {
            return accessLog;
        }

        AccessLogAdapter adapter = null;
        Valve valves[] = getPipeline().getValves();
        for (Valve valve : valves) {
            if (valve instanceof AccessLog) {
                if (adapter == null) {
                    adapter = new AccessLogAdapter((AccessLog) valve);
                } else {
                    adapter.add((AccessLog) valve);
                }
            }
        }
        if (adapter != null) {
            accessLog = adapter;
        }
        accessLogScanComplete = true;
        return accessLog;
    }

    // ------------------------------------------------------- Pipeline Methods


    /**
     * Convenience method, intended for use by the digester to simplify the
     * process of adding Valves to containers. See
     * {@link Pipeline#addValve(Valve)} for full details. Components other than
     * the digester should use {@link #getPipeline()}.{@link #addValve(Valve)} in case a
     * future implementation provides an alternative method for the digester to
     * use.
     *
     * @param valve Valve to be added
     *
     * @exception IllegalArgumentException if this Container refused to
     *  accept the specified Valve
     * @exception IllegalArgumentException if the specified Valve refuses to be
     *  associated with this Container
     * @exception IllegalStateException if the specified Valve is already
     *  associated with a different Container
     */
    public synchronized void addValve(Valve valve) {

        pipeline.addValve(valve);
    }


    /**
     * 被ContainerBackgroundProcessor调用的方法。
     * 容器放在后台执行的方法。
     */
    @Override
    public void backgroundProcess() {

        if (!getState().isAvailable()) {
            return;
        }

        //获取cluster，执行cluster的后台任务，例如：发送心跳包，监听集群部署的改变（Engine和Host包含）
        Cluster cluster = getClusterInternal();
        if (cluster != null) {
            try {
                cluster.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.cluster",
                        cluster), e);
            }
        }
        //获取Realm，执行Realm的后台任务。
        Realm realm = getRealmInternal();
        if (realm != null) {
            try {
                realm.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.realm", realm), e);
            }
        }
        //执行整个Valve链中每个Valve的后台任务
        Valve current = pipeline.getFirst();
        while (current != null) {
            try {
                current.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.valve", current), e);
            }
            current = current.getNext();
        }
        fireLifecycleEvent(Lifecycle.PERIODIC_EVENT, null);
    }


    @Override
    public File getCatalinaBase() {

        if (parent == null) {
            return null;
        }

        return parent.getCatalinaBase();
    }


    @Override
    public File getCatalinaHome() {

        if (parent == null) {
            return null;
        }

        return parent.getCatalinaHome();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 把事件发送给所有的监听器
     */
    @Override
    public void fireContainerEvent(String type, Object data) {

        if (listeners.size() < 1) {
            return;
        }

        ContainerEvent event = new ContainerEvent(this, type, data);
        // Note for each uses an iterator internally so this is safe
        for (ContainerListener listener : listeners) {
            listener.containerEvent(event);
        }
    }


    // -------------------- JMX and Registration  --------------------

    @Override
    protected String getDomainInternal() {

        Container p = this.getParent();
        if (p == null) {
            return null;
        } else {
            return p.getDomain();
        }
    }


    @Override
    public String getMBeanKeyProperties() {
        Container c = this;
        StringBuilder keyProperties = new StringBuilder();
        int containerCount = 0;

        // Work up container hierarchy, add a component to the name for
        // each container
        while (!(c instanceof Engine)) {
            if (c instanceof Wrapper) {
                keyProperties.insert(0, ",servlet=");
                keyProperties.insert(9, c.getName());
            } else if (c instanceof Context) {
                keyProperties.insert(0, ",context=");
                ContextName cn = new ContextName(c.getName(), false);
                keyProperties.insert(9,cn.getDisplayName());
            } else if (c instanceof Host) {
                keyProperties.insert(0, ",host=");
                keyProperties.insert(6, c.getName());
            } else if (c == null) {
                // May happen in unit testing and/or some embedding scenarios
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append("=null");
                break;
            } else {
                // Should never happen...
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append('=');
                keyProperties.append(c.getName());
            }
            c = c.getParent();
        }
        return keyProperties.toString();
    }

    public ObjectName[] getChildren() {
        List<ObjectName> names = new ArrayList<>(children.size());
        for (Container next : children.values()) {
            if (next instanceof ContainerBase) {
                names.add(((ContainerBase)next).getObjectName());
            }
        }
        return names.toArray(new ObjectName[names.size()]);
    }


    // -------------------- Background Thread --------------------

    /**
     * 启动后台线程，例如用于定期检查session的超时
     */
    protected void threadStart() {

        if (thread != null) {
            return;
        }
        //当backgroundProcessorDelay小于等于0时，不启动后台线程
        if (backgroundProcessorDelay <= 0) {
            return;
        }

        //给一个线程设置有意义的名称，设置为daemon线程，并启动它
        threadDone = false;
        String threadName = "ContainerBackgroundProcessor[" + toString() + "]";
        thread = new Thread(new ContainerBackgroundProcessor(), threadName);
        thread.setDaemon(true);
        thread.start();

    }


    /**
     * 停止当前容器的后台运行线程。停止方法是使用interrupt()方法，并等待(join())
     */
    protected void threadStop() {

        if (thread == null) {
            return;
        }

        threadDone = true;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            // Ignore
        }

        thread = null;

    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Container parent = getParent();
        if (parent != null) {
            sb.append(parent.toString());
            sb.append('.');
        }
        sb.append(this.getClass().getSimpleName());
        sb.append('[');
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }


    // -------------------------------------- ContainerExecuteDelay Inner Class

    /**
     * Private thread class to invoke the backgroundProcess method
     * of this container and its children after a fixed delay.
     * ContainerBase的保护线程类，调用当前容器的backgroundProcess方法，并在一个固定延时后，调用他的子容器的backgroundProcess方法
     *
     *
     * 在容器的start()生命周期方法中，启动了这个后台线程。
     */
    protected class ContainerBackgroundProcessor implements Runnable {

        @Override
        public void run() {
            //该线程默认每间隔一定时间去执行processChildren(ContainerBase.this)方法。
            Throwable t = null;
            String unexpectedDeathMessage = sm.getString(
                    "containerBase.backgroundProcess.unexpectedThreadDeath",
                    Thread.currentThread().getName());
            try {
                while (!threadDone) {
                    try {
                        Thread.sleep(backgroundProcessorDelay * 1000L);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    if (!threadDone) {
                        processChildren(ContainerBase.this);
                    }
                }
            } catch (RuntimeException|Error e) {
                t = e;
                throw e;
            } finally {
                if (!threadDone) {
                    log.error(unexpectedDeathMessage, t);
                }
            }
        }

        /**
         * 容器的后台线程定期执行的方法
         * @param container 接收的参数是当前容器对象
         */
        protected void processChildren(Container container) {
            ClassLoader originalClassLoader = null;

            try {
                //判断当前容器是不是Context类型，如果是需要确保当前容器的classLoader是web的class loader。
                //就是使用它专属的类加载器
                if (container instanceof Context) {
                    Loader loader = ((Context) container).getLoader();
                    // Loader will be null for FailedContext instances
                    if (loader == null) {
                        return;
                    }

                    // Ensure background processing for Contexts and Wrappers
                    // is performed under the web app's class loader
                    originalClassLoader = ((Context) container).bind(false, null);
                }
                //执行容器的backgroundProcess()方法
                container.backgroundProcess();
                //循环所有多层子容器，调用每个子容器的backgroundProcess()方法。
                Container[] children = container.findChildren();
                for (int i = 0; i < children.length; i++) {
                    if (children[i].getBackgroundProcessorDelay() <= 0) {
                        processChildren(children[i]);
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error("Exception invoking periodic operation: ", t);
            } finally {
                if (container instanceof Context) {
                    ((Context) container).unbind(false, originalClassLoader);
               }
            }
        }
    }


    // ----------------------------- Inner classes used with start/stop Executor

    private static class StartChild implements Callable<Void> {

        private Container child;

        public StartChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            child.start();
            return null;
        }
    }

    private static class StopChild implements Callable<Void> {

        private Container child;

        public StopChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            if (child.getState().isAvailable()) {
                child.stop();
            }
            return null;
        }
    }

    private static class StartStopThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public StartStopThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
