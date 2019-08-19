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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Engine接口的标准实现。
 */
public class StandardEngine extends ContainerBase implements Engine {

    private static final Log log = LogFactory.getLog(StandardEngine.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardEngine component with the default basic Valve.
     * StandardEngine的初始化操作，使用默认的StandardEngineValve类放在pipeline中。
     * 并设置jvmRoute（负载均衡的时候使用）
     */
    public StandardEngine() {

        super();
        //1.给pipeline设置默认的Valve
        pipeline.setBasic(new StandardEngineValve());
        /* Set the jmvRoute using the system property jvmRoute */
        //2.设置jvmRoute
        try {
            setJvmRoute(System.getProperty("jvmRoute"));
        } catch(Exception ex) {
            log.warn(sm.getString("standardEngine.jvmRouteFail"));
        }
        //3.Engine的默认值为10，允许后台线程执行
        backgroundProcessorDelay = 10;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 当请求没有server host或者unknown host时的默认hostname
     */
    private String defaultHost = null;


    /**
     * 拥有此引擎的Service
     */
    private Service service = null;

    /**
     * Tomcat实例的JVM Route id。所有的Route id必须唯一
     *
     * jvmRoute：在负载均衡场景下必须定义该参数，来保证session affinity可用，对于集群中所有Tomcat服务器来讲定义的
     * 名称必须是唯一的，该名称将会被添加到生成的会话标识符中。
     */
    private String jvmRouteId;

    /**
     * Default access log to use for request/response pairs where we can't ID
     * the intended host and context.
     */
    private final AtomicReference<AccessLog> defaultAccessLog =
        new AtomicReference<>();

    // ------------------------------------------------------------- Properties

    /**
     * 检查Realm的配置，如果有配置就直接返回，如果没有配置就设置默认的Realm实现，并返回
     */
    @Override
    public Realm getRealm() {
        Realm configured = super.getRealm();
        //如果没有设置Realm，则默认实现为NullRealm
        if (configured == null) {
            configured = new NullRealm();
            this.setRealm(configured);
        }
        return configured;
    }


    /**
     * 返回默认的host
     */
    @Override
    public String getDefaultHost() {
        return (defaultHost);
    }


    /**
     * 设置默认的host，并触发监听器事件
     */
    @Override
    public void setDefaultHost(String host) {

        String oldDefaultHost = this.defaultHost;
        if (host == null) {
            this.defaultHost = null;
        } else {
            this.defaultHost = host.toLowerCase(Locale.ENGLISH);
        }
        support.firePropertyChange("defaultHost", oldDefaultHost,
                                   this.defaultHost);
    }


    /**
     * 为Engine设置集群下全局唯一的标识符。
     * 该值仅用在负载均衡的场景中。
     * 该属性一旦设置不能再被修改
     */
    @Override
    public void setJvmRoute(String routeId) {
        jvmRouteId = routeId;
    }


    /**
     * Retrieve the cluster-wide unique identifier for this Engine.
     * This value is only useful in a load-balancing scenario.
     */
    @Override
    public String getJvmRoute() {
        return jvmRouteId;
    }


    /**
     * Return the <code>Service</code> with which we are associated (if any).
     */
    @Override
    public Service getService() {

        return (this.service);

    }


    /**
     * Set the <code>Service</code> with which we are associated (if any).
     *
     * @param service The service that owns this Engine
     */
    @Override
    public void setService(Service service) {
        this.service = service;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Host.
     *
     * @param child Child container to be added
     */
    @Override
    public void addChild(Container child) {

        if (!(child instanceof Host)) {
            throw new IllegalArgumentException
                (sm.getString("standardEngine.notHost"));
        }
        super.addChild(child);

    }


    /**
     * Disallow any attempt to set a parent for this Container, since an
     * Engine is supposed to be at the top of the Container hierarchy.
     *
     * @param container Proposed parent Container
     */
    @Override
    public void setParent(Container container) {

        throw new IllegalArgumentException
            (sm.getString("standardEngine.notParent"));

    }


    /**
     * Engine的init()方法。直接调用父类ContainerBase的该方法
     * 父类中该方法的主要功能是，准备好一个线程池，使用LinkedBlockingQueue队列
     * 然后调用父类的initInternal()方法进行JMX的注册
     * @throws LifecycleException
     */
    @Override
    protected void initInternal() throws LifecycleException {
        //确保Realm已经存在，如果没有进行配置，就初始化一个NullRealm
        getRealm();
        //调用父类的方法
        super.initInternal();
    }


/**
 *  启动当前Engine组件。具体实现方法也是调用父类ContainerBase的方法
 */
@Override
protected synchronized void startInternal() throws LifecycleException {

    // Log our server identification information
    if(log.isInfoEnabled()) {
        log.info( "Starting Servlet Engine: " + ServerInfo.getServerInfo());
    }

    // Standard container startup
    super.startInternal();
}


    /**
     * Override the default implementation. If no access log is defined for the
     * Engine, look for one in the Engine's default host and then the default
     * host's ROOT context. If still none is found, return the default NoOp
     * access log.
     */
    @Override
    public void logAccess(Request request, Response response, long time,
            boolean useDefault) {

        boolean logged = false;

        if (getAccessLog() != null) {
            accessLog.log(request, response, time);
            logged = true;
        }

        if (!logged && useDefault) {
            AccessLog newDefaultAccessLog = defaultAccessLog.get();
            if (newDefaultAccessLog == null) {
                // If we reached this point, this Engine can't have an AccessLog
                // Look in the defaultHost
                Host host = (Host) findChild(getDefaultHost());
                Context context = null;
                if (host != null && host.getState().isAvailable()) {
                    newDefaultAccessLog = host.getAccessLog();

                    if (newDefaultAccessLog != null) {
                        if (defaultAccessLog.compareAndSet(null,
                                newDefaultAccessLog)) {
                            AccessLogListener l = new AccessLogListener(this,
                                    host, null);
                            l.install();
                        }
                    } else {
                        // Try the ROOT context of default host
                        context = (Context) host.findChild("");
                        if (context != null &&
                                context.getState().isAvailable()) {
                            newDefaultAccessLog = context.getAccessLog();
                            if (newDefaultAccessLog != null) {
                                if (defaultAccessLog.compareAndSet(null,
                                        newDefaultAccessLog)) {
                                    AccessLogListener l = new AccessLogListener(
                                            this, null, context);
                                    l.install();
                                }
                            }
                        }
                    }
                }

                if (newDefaultAccessLog == null) {
                    newDefaultAccessLog = new NoopAccessLog();
                    if (defaultAccessLog.compareAndSet(null,
                            newDefaultAccessLog)) {
                        AccessLogListener l = new AccessLogListener(this, host,
                                context);
                        l.install();
                    }
                }
            }

            newDefaultAccessLog.log(request, response, time);
        }
    }


    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return (parentClassLoader);
        }
        if (service != null) {
            return (service.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }


    @Override
    public File getCatalinaBase() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getCatalinaBase();
                if (base != null) {
                    return base;
                }
            }
        }
        // Fall-back
        return super.getCatalinaBase();
    }


    @Override
    public File getCatalinaHome() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getCatalinaHome();
                if (base != null) {
                    return base;
                }
            }
        }
        // Fall-back
        return super.getCatalinaHome();
    }


    // -------------------- JMX registration  --------------------

    @Override
    protected String getObjectNameKeyProperties() {
        return "type=Engine";
    }


    @Override
    protected String getDomainInternal() {
        return getName();
    }


    // ----------------------------------------------------------- Inner classes
    protected static final class NoopAccessLog implements AccessLog {

        @Override
        public void log(Request request, Response response, long time) {
            // NOOP
        }

        @Override
        public void setRequestAttributesEnabled(
                boolean requestAttributesEnabled) {
            // NOOP

        }

        @Override
        public boolean getRequestAttributesEnabled() {
            // NOOP
            return false;
        }
    }

    protected static final class AccessLogListener
            implements PropertyChangeListener, LifecycleListener,
            ContainerListener {

        private final StandardEngine engine;
        private final Host host;
        private final Context context;
        private volatile boolean disabled = false;

        public AccessLogListener(StandardEngine engine, Host host,
                Context context) {
            this.engine = engine;
            this.host = host;
            this.context = context;
        }

        public void install() {
            engine.addPropertyChangeListener(this);
            if (host != null) {
                host.addContainerListener(this);
                host.addLifecycleListener(this);
            }
            if (context != null) {
                context.addLifecycleListener(this);
            }
        }

        private void uninstall() {
            disabled = true;
            if (context != null) {
                context.removeLifecycleListener(this);
            }
            if (host != null) {
                host.removeLifecycleListener(this);
                host.removeContainerListener(this);
            }
            engine.removePropertyChangeListener(this);
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (disabled) {
                return;
            }

            String type = event.getType();
            if (Lifecycle.AFTER_START_EVENT.equals(type) ||
                    Lifecycle.BEFORE_STOP_EVENT.equals(type) ||
                    Lifecycle.BEFORE_DESTROY_EVENT.equals(type)) {
                // Container is being started/stopped/removed
                // Force re-calculation and disable listener since it won't
                // be re-used
                engine.defaultAccessLog.set(null);
                uninstall();
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (disabled) {
                return;
            }
            if ("defaultHost".equals(evt.getPropertyName())) {
                // Force re-calculation and disable listener since it won't
                // be re-used
                engine.defaultAccessLog.set(null);
                uninstall();
            }
        }

        @Override
        public void containerEvent(ContainerEvent event) {
            // Only useful for hosts
            if (disabled) {
                return;
            }
            if (Container.ADD_CHILD_EVENT.equals(event.getType())) {
                Context context = (Context) event.getData();
                if ("".equals(context.getPath())) {
                    // Force re-calculation and disable listener since it won't
                    // be re-used
                    engine.defaultAccessLog.set(null);
                    uninstall();
                }
            }
        }
    }
}
