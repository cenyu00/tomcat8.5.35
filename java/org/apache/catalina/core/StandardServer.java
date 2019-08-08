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
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.util.Random;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.mbeans.MBeanFactory;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.util.ExtensionValidator;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.StringCache;
import org.apache.tomcat.util.res.StringManager;


/**
 * Standard implementation of the <b>Server</b> interface, available for use
 * (but not required) when deploying and starting Catalina.
 *
 * @author Craig R. McClanahan
 */
public final class StandardServer extends LifecycleMBeanBase implements Server {

    private static final Log log = LogFactory.getLog(StandardServer.class);


    // ------------------------------------------------------------ Constructor


    /**
     * Construct a default instance of this class.
     */
    public StandardServer() {

        super();

        globalNamingResources = new NamingResourcesImpl();
        globalNamingResources.setContainer(this);

        if (isUseNaming()) {
            namingContextListener = new NamingContextListener();
            addLifecycleListener(namingContextListener);
        } else {
            namingContextListener = null;
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * JNDI的context
     */
    private javax.naming.Context globalNamingContext = null;


    /**
     * JNDI的resource
     */
    private NamingResourcesImpl globalNamingResources = null;


    /**
     * 作用与web application的JDNI监听器
     */
    private final NamingContextListener namingContextListener;


    /**
     * 用于等待shutdown命令的端口号
     */
    private int port = 8005;

    /**
     * 用于等待shutdown命令的地址
     */
    private String address = "localhost";


    /**
     * 随机数生成器，当shutdown命令行大于1024个字符时，将随机产生
     */
    private Random random = null;


    /**
     * Server下的service集合
     */
    private Service services[] = new Service[0];


    /**
     * Service用到的锁
     */
    private final Object servicesLock = new Object();


    /**
     * shutdown服务在等待的命令
     */
    private String shutdown = "SHUTDOWN";


    /**
     * 这个包的string管理器
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The property change support for this component.
     */
    final PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * stopAwait的标记位
     */
    private volatile boolean stopAwait = false;

    //Catalina对象
    private Catalina catalina = null;

    //父类加载器
    private ClassLoader parentClassLoader = null;

    /**
     * 执行await()的线程
     */
    private volatile Thread awaitThread = null;

    /**
     * 用于等待shutdown的socket服务器
     */
    private volatile ServerSocket awaitSocket = null;

    //catalinaHome的地址
    private File catalinaHome = null;

    //catalinaBase的地址
    private File catalinaBase = null;

    private final Object namingToken = new Object();


    // ------------------------------------------------------------- Properties

    @Override
    public Object getNamingToken() {
        return namingToken;
    }


    /**
     * Return the global naming resources context.
     */
    @Override
    public javax.naming.Context getGlobalNamingContext() {

        return (this.globalNamingContext);

    }


    /**
     * Set the global naming resources context.
     *
     * @param globalNamingContext The new global naming resource context
     */
    public void setGlobalNamingContext
        (javax.naming.Context globalNamingContext) {

        this.globalNamingContext = globalNamingContext;

    }


    /**
     * Return the global naming resources.
     */
    @Override
    public NamingResourcesImpl getGlobalNamingResources() {

        return (this.globalNamingResources);

    }


    /**
     * Set the global naming resources.
     *
     * @param globalNamingResources The new global naming resources
     */
    @Override
    public void setGlobalNamingResources
        (NamingResourcesImpl globalNamingResources) {

        NamingResourcesImpl oldGlobalNamingResources =
            this.globalNamingResources;
        this.globalNamingResources = globalNamingResources;
        this.globalNamingResources.setContainer(this);
        support.firePropertyChange("globalNamingResources",
                                   oldGlobalNamingResources,
                                   this.globalNamingResources);

    }


    /**
     * Report the current Tomcat Server Release number
     * @return Tomcat release identifier
     */
    public String getServerInfo() {
        return ServerInfo.getServerInfo();
    }


    /**
     * Return the current server built timestamp
     * @return server built timestamp.
     */
    public String getServerBuilt() {
        return ServerInfo.getServerBuilt();
    }


    /**
     * Return the current server's version number.
     * @return server's version number.
     */
    public String getServerNumber() {
        return ServerInfo.getServerNumber();
    }


    /**
     * Return the port number we listen to for shutdown commands.
     */
    @Override
    public int getPort() {

        return (this.port);

    }


    /**
     * Set the port number we listen to for shutdown commands.
     *
     * @param port The new port number
     */
    @Override
    public void setPort(int port) {

        this.port = port;

    }


    /**
     * Return the address on which we listen to for shutdown commands.
     */
    @Override
    public String getAddress() {

        return (this.address);

    }


    /**
     * Set the address on which we listen to for shutdown commands.
     *
     * @param address The new address
     */
    @Override
    public void setAddress(String address) {

        this.address = address;

    }

    /**
     * Return the shutdown command string we are waiting for.
     */
    @Override
    public String getShutdown() {

        return (this.shutdown);

    }


    /**
     * Set the shutdown command we are waiting for.
     *
     * @param shutdown The new shutdown command
     */
    @Override
    public void setShutdown(String shutdown) {

        this.shutdown = shutdown;

    }


    /**
     * Return the outer Catalina startup/shutdown component if present.
     */
    @Override
    public Catalina getCatalina() {
        return catalina;
    }


    /**
     * Set the outer Catalina startup/shutdown component if present.
     */
    @Override
    public void setCatalina(Catalina catalina) {
        this.catalina = catalina;
    }

    // --------------------------------------------------------- Server Methods


    /**
     * Add a new Service to the set of defined Services.
     *
     * @param service The Service to be added
     */
    @Override
    public void addService(Service service) {

        service.setServer(this);

        synchronized (servicesLock) {
            Service results[] = new Service[services.length + 1];
            System.arraycopy(services, 0, results, 0, services.length);
            results[services.length] = service;
            services = results;

            if (getState().isAvailable()) {
                try {
                    service.start();
                } catch (LifecycleException e) {
                    // Ignore
                }
            }

            // Report this property change to interested listeners
            support.firePropertyChange("service", null, service);
        }

    }

    public void stopAwait() {
        stopAwait=true;
        Thread t = awaitThread;
        if (t != null) {
            ServerSocket s = awaitSocket;
            if (s != null) {
                awaitSocket = null;
                try {
                    s.close();
                } catch (IOException e) {
                    // Ignored
                }
            }
            t.interrupt();
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                // Ignored
            }
        }
    }

    /**
     * 等待shutdown命令发送发送过来才会返回，否则一直阻塞。
     * 主线程在等待，其他的监听和http连接等都是daemon线程。
     */
    @Override
    public void await() {
        //1.处理port等于-1或-2的情况。
        // Negative values - don't wait on port - tomcat is embedded or we just don't like ports
        if( port == -2 ) {
            // undocumented yet - for embedding apps that are around, alive.
            return;
        }
        if( port==-1 ) {
            try {
                awaitThread = Thread.currentThread();
                while(!stopAwait) {
                    try {
                        Thread.sleep( 10000 );
                    } catch( InterruptedException ex ) {
                        // continue and check the flag
                    }
                }
            } finally {
                awaitThread = null;
            }
            return;
        }

        //2.设置一个socket的服务端在指定端口上进行等待
        try {
            awaitSocket = new ServerSocket(port, 1,
                    InetAddress.getByName(address));
        } catch (IOException e) {
            log.error("StandardServer.await: create[" + address
                               + ":" + port
                               + "]: ", e);
            return;
        }

        try {
            //当前线程设置为等待线程
            awaitThread = Thread.currentThread();

            //3.轮询等待，直到shutdown命令行进来，如果跟设置的匹配就结束循环
            while (!stopAwait) {
                ServerSocket serverSocket = awaitSocket;
                if (serverSocket == null) {
                    break;
                }

                // Wait for the next connection
                Socket socket = null;
                StringBuilder command = new StringBuilder();
                try {
                    InputStream stream;
                    long acceptStartTime = System.currentTimeMillis();
                    try {
                        socket = serverSocket.accept();
                        socket.setSoTimeout(10 * 1000);  // Ten seconds
                        stream = socket.getInputStream();
                    } catch (SocketTimeoutException ste) {
                        // This should never happen but bug 56684 suggests that
                        // it does.
                        log.warn(sm.getString("standardServer.accept.timeout",
                                Long.valueOf(System.currentTimeMillis() - acceptStartTime)), ste);
                        continue;
                    } catch (AccessControlException ace) {
                        log.warn("StandardServer.accept security exception: "
                                + ace.getMessage(), ace);
                        continue;
                    } catch (IOException e) {
                        if (stopAwait) {
                            // Wait was aborted with socket.close()
                            break;
                        }
                        log.error("StandardServer.await: accept: ", e);
                        break;
                    }

                    // Read a set of characters from the socket
                    int expected = 1024; // Cut off to avoid DoS attack
                    while (expected < shutdown.length()) {
                        if (random == null)
                            random = new Random();
                        expected += (random.nextInt() % 1024);
                    }
                    while (expected > 0) {
                        int ch = -1;
                        try {
                            ch = stream.read();
                        } catch (IOException e) {
                            log.warn("StandardServer.await: read: ", e);
                            ch = -1;
                        }
                        // Control character or EOF (-1) terminates loop
                        if (ch < 32 || ch == 127) {
                            break;
                        }
                        command.append((char) ch);
                        expected--;
                    }
                } finally {
                    // Close the socket now that we are done with it
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                // Match against our command string
                boolean match = command.toString().equals(shutdown);
                if (match) {
                    log.info(sm.getString("standardServer.shutdownViaPort"));
                    break;
                } else
                    log.warn("StandardServer.await: Invalid command '"
                            + command.toString() + "' received");
            }
        } finally {
            ServerSocket serverSocket = awaitSocket;
            awaitThread = null;
            awaitSocket = null;

            // Close the server socket and return
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }


    /**
     * @return the specified Service (if it exists); otherwise return
     * <code>null</code>.
     *
     * @param name Name of the Service to be returned
     */
    @Override
    public Service findService(String name) {

        if (name == null) {
            return (null);
        }
        synchronized (servicesLock) {
            for (int i = 0; i < services.length; i++) {
                if (name.equals(services[i].getName())) {
                    return (services[i]);
                }
            }
        }
        return (null);

    }


    /**
     * @return the set of Services defined within this Server.
     */
    @Override
    public Service[] findServices() {

        return services;

    }

    /**
     * @return the JMX service names.
     */
    public ObjectName[] getServiceNames() {
        ObjectName onames[]=new ObjectName[ services.length ];
        for( int i=0; i<services.length; i++ ) {
            onames[i]=((StandardService)services[i]).getObjectName();
        }
        return onames;
    }


    /**
     * Remove the specified Service from the set associated from this
     * Server.
     *
     * @param service The Service to be removed
     */
    @Override
    public void removeService(Service service) {

        synchronized (servicesLock) {
            int j = -1;
            for (int i = 0; i < services.length; i++) {
                if (service == services[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            try {
                services[j].stop();
            } catch (LifecycleException e) {
                // Ignore
            }
            int k = 0;
            Service results[] = new Service[services.length - 1];
            for (int i = 0; i < services.length; i++) {
                if (i != j)
                    results[k++] = services[i];
            }
            services = results;

            // Report this property change to interested listeners
            support.firePropertyChange("service", service, null);
        }

    }


    @Override
    public File getCatalinaBase() {
        if (catalinaBase != null) {
            return catalinaBase;
        }

        catalinaBase = getCatalinaHome();
        return catalinaBase;
    }


    @Override
    public void setCatalinaBase(File catalinaBase) {
        this.catalinaBase = catalinaBase;
    }


    @Override
    public File getCatalinaHome() {
        return catalinaHome;
    }


    @Override
    public void setCatalinaHome(File catalinaHome) {
        this.catalinaHome = catalinaHome;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("StandardServer[");
        sb.append(getPort());
        sb.append("]");
        return (sb.toString());

    }


    /**
     * Write the configuration information for this entire <code>Server</code>
     * out to the server.xml configuration file.
     *
     * @exception InstanceNotFoundException
     *            if the managed resource object cannot be found
     * @exception MBeanException
     *            if the initializer of the object throws an exception, or
     *            persistence is not supported
     * @exception javax.management.RuntimeOperationsException
     *            if an exception is reported by the persistence mechanism
     */
    public synchronized void storeConfig() throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            if (mserver.isRegistered(sname)) {
                mserver.invoke(sname, "storeConfig", null, null);
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(t);
        }
    }


    /**
     * Write the configuration information for <code>Context</code>
     * out to the specified configuration file.
     *
     * @param context the context which should save its configuration
     * @exception InstanceNotFoundException
     *            if the managed resource object cannot be found
     * @exception MBeanException
     *            if the initializer of the object throws an exception
     *            or persistence is not supported
     * @exception javax.management.RuntimeOperationsException
     *            if an exception is reported by the persistence mechanism
     */
    public synchronized void storeContext(Context context) throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            if (mserver.isRegistered(sname)) {
                mserver.invoke(sname, "store",
                    new Object[] {context},
                    new String [] { "java.lang.String"});
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(t);
        }
    }


    /**
     * @return <code>true</code> if naming should be used.
     */
    private boolean isUseNaming() {
        boolean useNaming = true;
        // Reading the "catalina.useNaming" environment variable
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if ((useNamingProperty != null)
            && (useNamingProperty.equals("false"))) {
            useNaming = false;
        }
        return useNaming;
    }


    /**
     * Start nested components ({@link Service}s) and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        //1.发送生命周期时间
        fireLifecycleEvent(CONFIGURE_START_EVENT, null);
        //更改生命周期的状态为starting
        setState(LifecycleState.STARTING);

        //2.启动JDNI
        globalNamingResources.start();

        //3.调用Service的start()方法，启动Service
        synchronized (servicesLock) {
            for (int i = 0; i < services.length; i++) {
                services[i].start();
            }
        }
    }


    /**
     * Stop nested components ({@link Service}s) and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        //1.设置生命周期的状态，
        setState(LifecycleState.STOPPING);
        fireLifecycleEvent(CONFIGURE_STOP_EVENT, null);

        // 2.调用所有的Service的stop方法
        for (int i = 0; i < services.length; i++) {
            services[i].stop();
        }

        //3.停止JDNI
        globalNamingResources.stop();

        //4.清理等待线程和socket
        stopAwait();
    }

    /**
     * Invoke a pre-startup initialization. This is used to allow connectors
     * to bind to restricted ports under Unix operating environments.
     */
    @Override
    protected void initInternal() throws LifecycleException {

        //1.先调用父类的init方法
        super.initInternal();

        // Register global String cache
        // Note although the cache is global, if there are multiple Servers
        // present in the JVM (may happen when embedding) then the same cache
        // will be registered under multiple names
        //2.注册全局的String cache，作用是什么现在还不清楚
        onameStringCache = register(new StringCache(), "type=StringCache");

        // Register the MBeanFactory
        //3.注册JMX的MBean
        MBeanFactory factory = new MBeanFactory();
        factory.setContainer(this);
        onameMBeanFactory = register(factory, "type=MBeanFactory");

        // Register the naming resources
        //4.调用JNDI的init方法
        globalNamingResources.init();

        // Populate the extension validator with JARs from common and shared class loaders
        //5.对common和shared目录下的jar包进行校验，如果给出的jar文件包含MANIFEST，
        // 将被添加到container的manifest资源中
        if (getCatalina() != null) {
            ClassLoader cl = getCatalina().getParentClassLoader();
            // Walk the class loader hierarchy. Stop at the system class loader.
            // This will add the shared (if present) and common class loaders
            while (cl != null && cl != ClassLoader.getSystemClassLoader()) {
                if (cl instanceof URLClassLoader) {
                    URL[] urls = ((URLClassLoader) cl).getURLs();
                    for (URL url : urls) {
                        if (url.getProtocol().equals("file")) {
                            try {
                                File f = new File (url.toURI());
                                if (f.isFile() &&
                                        f.getName().endsWith(".jar")) {
                                    ExtensionValidator.addSystemResource(f);
                                }
                            } catch (URISyntaxException e) {
                                // Ignore
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }
                }
                cl = cl.getParent();
            }
        }
        //6.执行Service的init方法
        for (int i = 0; i < services.length; i++) {
            services[i].init();
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        // 1.调用所有的service的destroy()方法
        for (int i = 0; i < services.length; i++) {
            services[i].destroy();
        }

        //2.调用JNDI的destroy()
        globalNamingResources.destroy();

        //3.解绑MBean,和String Cache，对应init方法中的注册。
        unregister(onameMBeanFactory);

        unregister(onameStringCache);

        //4.调用父类的方法
        super.destroyInternal();
    }

    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (catalina != null) {
            return (catalina.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }

    /**
     * Set the parent class loader for this server.
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


    private ObjectName onameStringCache;
    private ObjectName onameMBeanFactory;

    /**
     * Obtain the MBean domain for this server. The domain is obtained using
     * the following search order:
     * <ol>
     * <li>Name of first {@link org.apache.catalina.Engine}.</li>
     * <li>Name of first {@link Service}.</li>
     * </ol>
     */
    @Override
    protected String getDomainInternal() {

        String domain = null;

        Service[] services = findServices();
        if (services.length > 0) {
            Service service = services[0];
            if (service != null) {
                domain = service.getDomain();
            }
        }
        return domain;
    }


    @Override
    protected final String getObjectNameKeyProperties() {
        return "type=Server";
    }

}
