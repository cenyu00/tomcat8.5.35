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
package org.apache.catalina.startup;


import java.io.*;
import java.lang.reflect.Constructor;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.security.SecurityConfig;
import org.apache.juli.ClassLoaderLogManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


/**
 *
 * shell程序操作的java对象，支持命令行下面的参数。
 * -config {pathname} 设置配置文件server.xml的路径，
 * -help  展示使用方法
 * -nonaming 禁用naming
 * configtest  尝试测试配置文件
 * start 启动一个Catalina的实例
 * stop 停止当前正在运行的catalina实例
 *
 */
public class Catalina {


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------- Instance Variables

    /**
     * 用于await的flag
     */
    protected boolean await = false;

    /**
     * Server配置的文件路径
     */
    protected String configFile = "conf/server.xml";

    /**
     * The shared extensions class loader for this server.
     * 此server的shared 类加载器
     */
    protected ClassLoader parentClassLoader =
        Catalina.class.getClassLoader();


    /**
     * Server组件
     */
    protected Server server = null;


    /**
     * 使用shutdown钩子的flag
     */
    protected boolean useShutdownHook = true;


    /**
     * Shutdown钩子实例
     */
    protected Thread shutdownHook = null;


    /**
     * 默认需要开启Naming
     */
    protected boolean useNaming = true;


    /**
     * 预防重复加载的标记字段
     */
    protected boolean loaded = false;


    // ----------------------------------------------------------- Constructors

    public Catalina() {
        //设置包的安全保护
        setSecurityProtection();
        ExceptionUtils.preload();
    }


    // ------------------------------------------------------------- Properties

    public void setConfigFile(String file) {
        configFile = file;
    }


    public String getConfigFile() {
        return configFile;
    }


    public void setUseShutdownHook(boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }


    public boolean getUseShutdownHook() {
        return useShutdownHook;
    }


    /**
     * Set the shared extensions class loader.
     *
     * @param parentClassLoader The shared extensions class loader.
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }

    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return (parentClassLoader);
        }
        return ClassLoader.getSystemClassLoader();
    }

    public void setServer(Server server) {
        this.server = server;
    }


    public Server getServer() {
        return server;
    }


    /**
     * @return <code>true</code> if naming is enabled.
     */
    public boolean isUseNaming() {
        return (this.useNaming);
    }


    /**
     * Enables or disables naming support.
     *
     * @param useNaming The new use naming value
     */
    public void setUseNaming(boolean useNaming) {
        this.useNaming = useNaming;
    }

    public void setAwait(boolean b) {
        await = b;
    }

    public boolean isAwait() {
        return await;
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * Process the specified command line arguments.
     *
     * @param args Command line arguments to process
     * @return <code>true</code> if we should continue processing
     */
    protected boolean arguments(String args[]) {

        boolean isConfig = false;

        if (args.length < 1) {
            usage();
            return false;
        }

        for (int i = 0; i < args.length; i++) {
            if (isConfig) {
                configFile = args[i];
                isConfig = false;
            } else if (args[i].equals("-config")) {
                isConfig = true;
            } else if (args[i].equals("-nonaming")) {
                setUseNaming(false);
            } else if (args[i].equals("-help")) {
                usage();
                return false;
            } else if (args[i].equals("start")) {
                // NOOP
            } else if (args[i].equals("configtest")) {
                // NOOP
            } else if (args[i].equals("stop")) {
                // NOOP
            } else {
                usage();
                return false;
            }
        }

        return true;
    }


    /**
     * 返回server.xml文件的路径
     */
    protected File configFile() {

        File file = new File(configFile);
        if (!file.isAbsolute()) {
            file = new File(Bootstrap.getCatalinaBase(), configFile);
        }
        return (file);

    }


    /**
     * 创建和配置Digester，返回用于解析server.xml的digester实例
     */
    protected Digester createStartDigester() {
        long t1=System.currentTimeMillis();
        // 创建一个digester实例
        Digester digester = new Digester();
        //是否对xml文档进行类似XSD等类型的校验，默认为fasle。
        digester.setValidating(false);
        //是否进行节点设置规则校验，如果xml中相应节点没有设置解析规则会在控制台显示提示信息。
        digester.setRulesValidation(true);

        //将XML节点中的className作为假属性，不必调用默认的setter方法
        //(一般的节点属性在解析时会以属性作为入参调用该节点相应对象的setter方法，而className属性的作用
        // 提示解析器使用该属性的值作来实例化对象)
        Map<Class<?>, List<String>> fakeAttributes = new HashMap<>();
        List<String> objectAttrs = new ArrayList<>();
        objectAttrs.add("className");
        fakeAttributes.put(Object.class, objectAttrs);
        // Ignore attribute added by Eclipse for its internal tracking
        List<String> contextAttrs = new ArrayList<>();
        contextAttrs.add("source");
        fakeAttributes.put(StandardContext.class, contextAttrs);
        digester.setFakeAttributes(fakeAttributes);

        //使用当前线程的上下文类加载器，主要加载FactoryCreateRule和ObjectCreateRule
        digester.setUseContextClassLoader(true);

        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");

        digester.addObjectCreate("Server/GlobalNamingResources",
                                 "org.apache.catalina.deploy.NamingResourcesImpl");
        digester.addSetProperties("Server/GlobalNamingResources");
        digester.addSetNext("Server/GlobalNamingResources",
                            "setGlobalNamingResources",
                            "org.apache.catalina.deploy.NamingResourcesImpl");

        digester.addObjectCreate("Server/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Listener");
        digester.addSetNext("Server/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service",
                                 "org.apache.catalina.core.StandardService",
                                 "className");
        digester.addSetProperties("Server/Service");
        digester.addSetNext("Server/Service",
                            "addService",
                            "org.apache.catalina.Service");

        digester.addObjectCreate("Server/Service/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Listener");
        digester.addSetNext("Server/Service/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service/Executor",
                         "org.apache.catalina.core.StandardThreadExecutor",
                         "className");
        digester.addSetProperties("Server/Service/Executor");
        digester.addSetNext("Server/Service/Executor",
                            "addExecutor",
                            "org.apache.catalina.Executor");

        digester.addRule("Server/Service/Connector",
                         new ConnectorCreateRule());

        digester.addRule("Server/Service/Connector",
                         new SetAllPropertiesRule(new String[]{"executor", "sslImplementationName"}));
        digester.addSetNext("Server/Service/Connector",
                            "addConnector",
                            "org.apache.catalina.connector.Connector");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig",
                                 "org.apache.tomcat.util.net.SSLHostConfig");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig",
                "addSslHostConfig",
                "org.apache.tomcat.util.net.SSLHostConfig");

        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate",
                         new CertificateCreateRule());
        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate",
                         new SetAllPropertiesRule(new String[]{"type"}));
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/Certificate",
                            "addCertificate",
                            "org.apache.tomcat.util.net.SSLHostConfigCertificate");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf",
                                 "org.apache.tomcat.util.net.openssl.OpenSSLConf");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf",
                            "setOpenSslConf",
                            "org.apache.tomcat.util.net.openssl.OpenSSLConf");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd",
                                 "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd",
                            "addCmd",
                            "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");

        digester.addObjectCreate("Server/Service/Connector/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Connector/Listener");
        digester.addSetNext("Server/Service/Connector/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service/Connector/UpgradeProtocol",
                                  null, // MUST be specified in the element
                                  "className");
        digester.addSetProperties("Server/Service/Connector/UpgradeProtocol");
        digester.addSetNext("Server/Service/Connector/UpgradeProtocol",
                            "addUpgradeProtocol",
                            "org.apache.coyote.UpgradeProtocol");

        // Add RuleSets for nested elements
        digester.addRuleSet(new NamingRuleSet("Server/GlobalNamingResources/"));
        digester.addRuleSet(new EngineRuleSet("Server/Service/"));
        digester.addRuleSet(new HostRuleSet("Server/Service/Engine/"));
        digester.addRuleSet(new ContextRuleSet("Server/Service/Engine/Host/"));
        addClusterRuleSet(digester, "Server/Service/Engine/Host/Cluster/");
        digester.addRuleSet(new NamingRuleSet("Server/Service/Engine/Host/Context/"));

        // When the 'engine' is found, set the parentClassLoader.
        digester.addRule("Server/Service/Engine",
                         new SetParentClassLoaderRule(parentClassLoader));
        addClusterRuleSet(digester, "Server/Service/Engine/Cluster/");

        long t2=System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("Digester for server.xml created " + ( t2-t1 ));
        }
        return (digester);

    }

    /**
     * Cluster support is optional. The JARs may have been removed.
     */
    private void addClusterRuleSet(Digester digester, String prefix) {
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        try {
            clazz = Class.forName("org.apache.catalina.ha.ClusterRuleSet");
            constructor = clazz.getConstructor(String.class);
            RuleSet ruleSet = (RuleSet) constructor.newInstance(prefix);
            digester.addRuleSet(ruleSet);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("catalina.noCluster",
                        e.getClass().getName() + ": " +  e.getMessage()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("catalina.noCluster",
                        e.getClass().getName() + ": " +  e.getMessage()));
            }
        }
    }

    /**
     * Create and configure the Digester we will be using for shutdown.
     * @return the digester to process the stop operation
     */
    protected Digester createStopDigester() {

        // Initialize the digester
        Digester digester = new Digester();
        digester.setUseContextClassLoader(true);

        // Configure the rules we need for shutting down
        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");

        return (digester);

    }


    public void stopServer() {
        stopServer(null);
    }

public void stopServer(String[] arguments) {

    //1.参数设置
    if (arguments != null) {
        arguments(arguments);
    }

    Server s = getServer();
    //2.如果Server存在，就调用Server的stop和destroy方法进行关闭，
    if (s == null) {
        // Create and execute our Digester
        Digester digester = createStopDigester();
        File file = configFile();
        try (FileInputStream fis = new FileInputStream(file)) {
            InputSource is =
                new InputSource(file.toURI().toURL().toString());
            is.setByteStream(fis);
            digester.push(this);
            digester.parse(is);
        } catch (Exception e) {
            log.error("Catalina.stop: ", e);
            System.exit(1);
        }
    } else {
        // Server object already present. Must be running as a service
        try {
            s.stop();
            s.destroy();
        } catch (LifecycleException e) {
            log.error("Catalina.stop: ", e);
        }
        return;
    }

    // 如果Server不存在，就重新解析server.xml文件构造server，然后通过socket发送shutdown命令关闭
    s = getServer();
    if (s.getPort()>0) {
        try (Socket socket = new Socket(s.getAddress(), s.getPort());
                OutputStream stream = socket.getOutputStream()) {
            String shutdown = s.getShutdown();
            for (int i = 0; i < shutdown.length(); i++) {
                stream.write(shutdown.charAt(i));
            }
            stream.flush();
        } catch (Exception ce) {
            ce.printStackTrace();
        }

    } else {
        log.error(sm.getString("catalina.stopServer"));
        System.exit(1);
    }
}


/**
 * 准备好环境，解析好server.xml文件生成好对象。server对象也准备好，然后调用server的init方法
 */
public void load() {
    try {

        //1.检查java.io.tmpdir是有有效
        initDirs();

        //2.设置catalina.useNaming的系统参数
        initNaming();

        //3.用digester解析server.xml文件，把配置文件中的配置解析成java对象
        //3.1.准备好用来解析server.xml文件需要用的digester。
        Digester digester = createStartDigester();
        //3.2.server.xml文件作为一个输入流传入
        File file = configFile();
        InputStream inputStream = new FileInputStream(file);
        //3.3.使用inputStream构造一个sax的inputSource
        InputSource inputSource = new InputSource(file.toURI().toURL().toString());
        inputSource.setByteStream(inputStream);
        //3.4.把当前类压入到digester的栈顶，用来作为digester解析出来的对象的一种引用
        digester.push(this);
        //3.5.调用digester的parse()方法进行解析。
        digester.parse(inputSource);

        //4.为子组件Server设置一些值
        getServer().setCatalina(this);
        getServer().setCatalinaHome(Bootstrap.getCatalinaHomeFile());
        getServer().setCatalinaBase(Bootstrap.getCatalinaBaseFile());

        //5.执行server的init方法，左右start方法的准备方法
        getServer().init();

    }catch (Exception e){
        e.printStackTrace();
    }

}


    /*
     * 使用参数进行load
     */
    public void load(String args[]) {

        try {
            //根据传入的参数设置Catalina的一些属性的值
            if (arguments(args)) {
                load();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }


    /**
     * Start a new server instance.
     */
public void start() {

    //1.执行Server的start()方法，如果执行失败，就调用Server的destroy()方法
    try {
        //执行server的start方法
        getServer().start();
    } catch (LifecycleException e) {
        try {
            //如果start失败，就调用server的destroy方法
            getServer().destroy();
        } catch (LifecycleException e1) {
            log.debug("destroy() failed for failed Server ", e1);
        }
        return;
    }

    //2.注册一个shutdown的钩子
    if (useShutdownHook) {
        if (shutdownHook == null) {
            shutdownHook = new CatalinaShutdownHook();
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook);

    }

    //3.等待处理如何停止的问题
    if (await) {
        await();
        stop();
    }
}


    /**
     * 停止一个Server实例
     */
    public void stop() {

        try {
            // Remove the ShutdownHook first so that server.stop()
            // doesn't get invoked twice
            if (useShutdownHook) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);

                // If JULI is being used, re-enable JULI's shutdown to ensure
                // log messages are not lost
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                            true);
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This will fail on JDK 1.2. Ignoring, as Tomcat can run
            // fine without the shutdown hook.
        }

        // Shut down the server
        try {
            Server s = getServer();
            LifecycleState state = s.getState();
            if (LifecycleState.STOPPING_PREP.compareTo(state) <= 0
                    && LifecycleState.DESTROYED.compareTo(state) >= 0) {
                // Nothing to do. stop() was already called
            } else {
                s.stop();
                s.destroy();
            }
        } catch (LifecycleException e) {
            log.error("Catalina.stop", e);
        }

    }


    /**
     * Await and shutdown.
     */
    public void await() {

        getServer().await();

    }


    /**
     * Print usage information for this application.
     */
    protected void usage() {

        System.out.println
            ("usage: java org.apache.catalina.startup.Catalina"
             + " [ -config {pathname} ]"
             + " [ -nonaming ] "
             + " { -help | start | stop }");

    }


    protected void initDirs() {
        String temp = System.getProperty("java.io.tmpdir");
        if (temp == null || (!(new File(temp)).isDirectory())) {
            log.error(sm.getString("embedded.notmp", temp));
        }
    }


    protected void initStreams() {
        // Replace System.out and System.err with a custom PrintStream
        System.setOut(new SystemLogHandler(System.out));
        System.setErr(new SystemLogHandler(System.err));
    }


    protected void initNaming() {
        // Setting additional variables
        if (!useNaming) {
            log.info( "Catalina naming disabled");
            System.setProperty("catalina.useNaming", "false");
        } else {
            System.setProperty("catalina.useNaming", "true");
            String value = "org.apache.naming";
            String oldValue =
                System.getProperty(javax.naming.Context.URL_PKG_PREFIXES);
            if (oldValue != null) {
                value = value + ":" + oldValue;
            }
            System.setProperty(javax.naming.Context.URL_PKG_PREFIXES, value);
            if( log.isDebugEnabled() ) {
                log.debug("Setting naming prefix=" + value);
            }
            value = System.getProperty
                (javax.naming.Context.INITIAL_CONTEXT_FACTORY);
            if (value == null) {
                System.setProperty
                    (javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                     "org.apache.naming.java.javaURLContextFactory");
            } else {
                log.debug( "INITIAL_CONTEXT_FACTORY already set " + value );
            }
        }
    }


    /**
     * Set the security package access/protection.
     */
    protected void setSecurityProtection(){
        SecurityConfig securityConfig = SecurityConfig.newInstance();
        securityConfig.setPackageDefinition();
        securityConfig.setPackageAccess();
    }


    // --------------------------------------- CatalinaShutdownHook Inner Class

    // XXX Should be moved to embedded !
    /**
     * Shutdown hook which will perform a clean shutdown of Catalina if needed.
     */
protected class CatalinaShutdownHook extends Thread {

    @Override
    public void run() {
        try {
            if (getServer() != null) {
                //1.调用Catalina的stop()方法
                Catalina.this.stop();
            }
        } catch (Throwable ex) {
            ExceptionUtils.handleThrowable(ex);
            log.error(sm.getString("catalina.shutdownHookFail"), ex);
        } finally {
            // If JULI is used, shut JULI down *after* the server shuts down
            // so log messages aren't lost
            LogManager logManager = LogManager.getLogManager();
            if (logManager instanceof ClassLoaderLogManager) {
                ((ClassLoaderLogManager) logManager).shutdown();
            }
        }
    }
}


    private static final Log log = LogFactory.getLog(Catalina.class);

}


// ------------------------------------------------------------ Private Classes


/**
 * Rule that sets the parent class loader for the top object on the stack,
 * which must be a <code>Container</code>.
 */

final class SetParentClassLoaderRule extends Rule {

    public SetParentClassLoaderRule(ClassLoader parentClassLoader) {

        this.parentClassLoader = parentClassLoader;

    }

    ClassLoader parentClassLoader = null;

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug("Setting parent class loader");
        }

        Container top = (Container) digester.peek();
        top.setParentClassLoader(parentClassLoader);

    }


}
