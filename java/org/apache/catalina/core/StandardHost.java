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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * Host接口的标准实现。
 * 每个子容器都必须是一个Context的实现，来处理对特定webapp的请求
 */
public class StandardHost extends ContainerBase implements Host {

    private static final Log log = LogFactory.getLog(StandardHost.class);

    // ----------------------------------------------------------- Constructors


    /**e.
     * 构造器，并为Pipeline设置Valve
     */
    public StandardHost() {

        super();
        pipeline.setBasic(new StandardHostValve());

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 当前host的别名列表
     */
    private String[] aliases = new String[0];

    /**
     * 设置别名时的对象锁
     */
    private final Object aliasesLock = new Object();


    /**
     * 当前host的app的根路径，默认为webapps
     */
    private String appBase = "webapps";

    /**
     * 当前host的app的根路径的File对象，其实就是把appBase的路径转成File
     */
    private volatile File appBaseFile = null;

    /**
     * 当前host的XML配置根路径
     */
    private String xmlBase = null;

    /**
     * host的默认配置路径
     */
    private volatile File hostConfigBase = null;

    /**
     * 自动部署的flag
     */
    private boolean autoDeploy = true;


    /**
     * 对部署的app，默认的上下文配置类的类名
     */
    private String configClass =
        "org.apache.catalina.startup.ContextConfig";


    /**
     * 对部署的app，默认的上下文实现类的类名
     */
    private String contextClass =
        "org.apache.catalina.core.StandardContext";


    /**
     * 启动时部署的flag
     */
    private boolean deployOnStartup = true;


    /**
     * 部署Context XML配置文件属性
     * (安全性是否已经开启)
     */
    private boolean deployXML = !Globals.IS_SECURITY_ENABLED;


    /**
     * 当一个web app被部署后，XML文件应该被复制到$CATALINA_BASE/conf/engine/host
     */
    private boolean copyXML = false;


    /**
     * 默认的错误报告器的实现类类名，针对web app部署的
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";


    /**
     * 解压WARS属性
     */
    private boolean unpackWARs = true;


    /**
     * app的基础工作路径
     */
    private String workDir = null;


    /**
     * 在启动时，是否为appBase和xmlBase创建路径
     */
    private boolean createDirs = true;


    /**
     * Track the class loaders for the child web applications so memory leaks
     * can be detected.
     */
    private final Map<ClassLoader, String> childClassLoaders =
            new WeakHashMap<>();


    /**
     * Any file or directory in {@link #appBase} that this pattern matches will
     * be ignored by the automatic deployment process (both
     * {@link #deployOnStartup} and {@link #autoDeploy}).
     */
    private Pattern deployIgnore = null;


    /**
     * 是否卸载旧版本，默认是不卸载
     */
    private boolean undeployOldVersions = false;

    private boolean failCtxIfServletStartFails = false;


    // ------------------------------------------------------------- Properties

    @Override
    public boolean getUndeployOldVersions() {
        return undeployOldVersions;
    }


    @Override
    public void setUndeployOldVersions(boolean undeployOldVersions) {
        this.undeployOldVersions = undeployOldVersions;
    }


    @Override
    public ExecutorService getStartStopExecutor() {
        return startStopExecutor;
    }


    /**
     * 返回当前host的app根路径
     */
    @Override
    public String getAppBase() {
        return (this.appBase);
    }


    /**
     * 获取当前host的app根路径的File对象
     */
    @Override
    public File getAppBaseFile() {

        if (appBaseFile != null) {
            return appBaseFile;
        }

        //把当前host的app根路径转为File对象
        File file = new File(getAppBase());

        // If not absolute, make it absolute
        if (!file.isAbsolute()) {
            file = new File(getCatalinaBase(), file.getPath());
        }

        // Make it canonical if possible
        try {
            file = file.getCanonicalFile();
        } catch (IOException ioe) {
            // Ignore
        }

        this.appBaseFile = file;
        return file;
    }


    /**
     * 设置当前host的web app的根路径
     */
    @Override
    public void setAppBase(String appBase) {

        //检查新的app根路径是否为空
        if (appBase.trim().equals("")) {
            log.warn(sm.getString("standardHost.problematicAppBase", getName()));
        }

        //给this.appBase设置新值，并唤醒属性改变的监听事件
        String oldAppBase = this.appBase;
        this.appBase = appBase;
        support.firePropertyChange("appBase", oldAppBase, this.appBase);
        this.appBaseFile = null;
    }


    /**
     * 返回当前host的xml配置文件路径
     */
    @Override
    public String getXmlBase() {

        return (this.xmlBase);

    }


    /**
     * 设置host配置文件的XML根路径
     */
    @Override
    public void setXmlBase(String xmlBase) {

        String oldXmlBase = this.xmlBase;
        this.xmlBase = xmlBase;
        support.firePropertyChange("xmlBase", oldXmlBase, this.xmlBase);

    }


    /**
     * 当前host的配置xml文件
     * 区别上面的getXmlBase(),一个是针对路径的，一个是转换成File对象了
     */
    @Override
    public File getConfigBaseFile() {
        //如果hostConfigBase字段不为null，就直接返回
        if (hostConfigBase != null) {
            return hostConfigBase;
        }
        String path = null;
        if (getXmlBase()!=null) {
            path = getXmlBase();
        } else {
            StringBuilder xmlDir = new StringBuilder("conf");
            Container parent = getParent();
            if (parent instanceof Engine) {
                xmlDir.append('/');
                xmlDir.append(parent.getName());
            }
            xmlDir.append('/');
            xmlDir.append(getName());
            path = xmlDir.toString();
        }
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(getCatalinaBase(), path);
        }
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {// ignore
        }
        this.hostConfigBase = file;
        return file;
    }


    /**
     * 返回是否需要在启动时就创建appBase和xmlBase的路径
     */
    @Override
    public boolean getCreateDirs() {
        return createDirs;
    }

    /**
     * 设置启动时，是否创建路径
     */
    @Override
    public void setCreateDirs(boolean createDirs) {
        this.createDirs = createDirs;
    }

    /**
     * 获取是否自动部署
     */
    @Override
    public boolean getAutoDeploy() {

        return (this.autoDeploy);

    }


    /**
     * 设置自动部署的值，并唤醒属性监听
     */
    @Override
    public void setAutoDeploy(boolean autoDeploy) {

        boolean oldAutoDeploy = this.autoDeploy;
        this.autoDeploy = autoDeploy;
        support.firePropertyChange("autoDeploy", oldAutoDeploy,
                                   this.autoDeploy);

    }


    /**
     * 返回app的上下文的配置类名
     */
    @Override
    public String getConfigClass() {

        return (this.configClass);

    }


    /**
     * 设置app的上下文的配置类的类名
     */
    @Override
    public void setConfigClass(String configClass) {

        String oldConfigClass = this.configClass;
        this.configClass = configClass;
        support.firePropertyChange("configClass",
                                   oldConfigClass, this.configClass);

    }


    /**
     * 返回app的上下文的实现类类名
     */
    public String getContextClass() {

        return (this.contextClass);

    }


    /**
     * 设置app的上下文的实现类的类名，并唤醒属性监听
     */
    public void setContextClass(String contextClass) {

        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        support.firePropertyChange("contextClass",
                                   oldContextClass, this.contextClass);

    }


    /**
     * 返回启动时部署的flag
     */
    @Override
    public boolean getDeployOnStartup() {

        return (this.deployOnStartup);

    }


    /**
     * 设置启动时是否部署
     */
    @Override
    public void setDeployOnStartup(boolean deployOnStartup) {

        boolean oldDeployOnStartup = this.deployOnStartup;
        this.deployOnStartup = deployOnStartup;
        support.firePropertyChange("deployOnStartup", oldDeployOnStartup,
                                   this.deployOnStartup);

    }


    /**
     * 如果XML Context 描述符应该被部署，则返回true
     */
    public boolean isDeployXML() {

        return deployXML;

    }


    /**
     * 设置Context XML配置文件是否为true
     */
    public void setDeployXML(boolean deployXML) {

        this.deployXML = deployXML;

    }


    /**
     * 返回是否复制XML文件
     */
    public boolean isCopyXML() {

        return this.copyXML;

    }


    /**
     * 设置是否复制XML文件
     */
    public void setCopyXML(boolean copyXML) {

        this.copyXML = copyXML;

    }


    /**
     * 返回一个新web app的错误报告器类的类名
     */
    public String getErrorReportValveClass() {

        return (this.errorReportValveClass);

    }


    /**
     * 为一个新的web app设置错误报告器的类名
     */
    public void setErrorReportValveClass(String errorReportValveClass) {

        String oldErrorReportValveClassClass = this.errorReportValveClass;
        this.errorReportValveClass = errorReportValveClass;
        //更换之后要唤醒属性监听器
        support.firePropertyChange("errorReportValveClass",
                                   oldErrorReportValveClassClass,
                                   this.errorReportValveClass);

    }


    /**
     * @return the canonical, fully qualified, name of the virtual host
     * this Container represents.
     */
    @Override
    public String getName() {

        return (name);

    }


    /**
     * 设置当前容器的名称，唤醒属性监听事件
     */
    @Override
    public void setName(String name) {

        if (name == null) {
            throw new IllegalArgumentException
                (sm.getString("standardHost.nullName"));
        }

        name = name.toLowerCase(Locale.ENGLISH);      // Internally all names are lower case

        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);

    }


    /**
     * 判断是否解压WARS
     */
    public boolean isUnpackWARs() {

        return (unpackWARs);

    }


    /**
     * 设置是否解压WARS
     */
    public void setUnpackWARs(boolean unpackWARs) {

        this.unpackWARs = unpackWARs;

    }


    /**
     * 返回app的基本工作路径
     */
    public String getWorkDir() {

        return (workDir);
    }


    /**
     * 设置当前虚拟机的基本工作路径
     */
    public void setWorkDir(String workDir) {

        this.workDir = workDir;
    }


    /**
     * 获取自动部署时，需要忽略的文件或地址
     */
    @Override
    public String getDeployIgnore() {
        if (deployIgnore == null) {
            return null;
        }
        return this.deployIgnore.toString();
    }


    /**
     * 获取自动部署时需要忽略的文件或地址的正则表达式
     */
    @Override
    public Pattern getDeployIgnorePattern() {
        return this.deployIgnore;
    }


    /**
     * 设置自动部署时需要忽略的文件或者地址
     */
    @Override
    public void setDeployIgnore(String deployIgnore) {
        String oldDeployIgnore;
        if (this.deployIgnore == null) {
            oldDeployIgnore = null;
        } else {
            oldDeployIgnore = this.deployIgnore.toString();
        }
        if (deployIgnore == null) {
            this.deployIgnore = null;
        } else {
            this.deployIgnore = Pattern.compile(deployIgnore);
        }
        support.firePropertyChange("deployIgnore",
                                   oldDeployIgnore,
                                   deployIgnore);
    }


    /**
     * 查询是否一个Servlet启动失败，这个webapp也启动失败
     */
    public boolean isFailCtxIfServletStartFails() {
        return failCtxIfServletStartFails;
    }


    /**
     * 改变Servlet启动失败对这个web app的影响
     */
    public void setFailCtxIfServletStartFails(
            boolean failCtxIfServletStartFails) {
        boolean oldFailCtxIfServletStartFails = this.failCtxIfServletStartFails;
        this.failCtxIfServletStartFails = failCtxIfServletStartFails;
        //唤醒属性监听事件
        support.firePropertyChange("failCtxIfServletStartFails",
                oldFailCtxIfServletStartFails,
                failCtxIfServletStartFails);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 给当前Host添加别名
     */
    @Override
    public void addAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {
            // 先循环一遍，检查是否重复
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    return;
                }
            }
            // 添加别名到列表中
            String newAliases[] = new String[aliases.length + 1];
            for (int i = 0; i < aliases.length; i++) {
                newAliases[i] = aliases[i];
            }
            newAliases[aliases.length] = alias;
            aliases = newAliases;
        }
        // 提醒监听事件
        fireContainerEvent(ADD_ALIAS_EVENT, alias);

    }


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Context.
     *
     * @param child Child container to be added
     */
    @Override
    public void addChild(Container child) {

        child.addLifecycleListener(new MemoryLeakTrackingListener());

        if (!(child instanceof Context)) {
            throw new IllegalArgumentException
                (sm.getString("standardHost.notContext"));
        }
        super.addChild(child);

    }


    /**
     * Used to ensure the regardless of {@link Context} implementation, a record
     * is kept of the class loader used every time a context starts.
     */
    private class MemoryLeakTrackingListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
                if (event.getSource() instanceof Context) {
                    Context context = ((Context) event.getSource());
                    childClassLoaders.put(context.getLoader().getClassLoader(),
                            context.getServletContext().getContextPath());
                }
            }
        }
    }


    /**
     * Attempt to identify the contexts that have a class loader memory leak.
     * This is usually triggered on context reload. Note: This method attempts
     * to force a full garbage collection. This should be used with extreme
     * caution on a production system.
     *
     * @return a list of possibly leaking contexts
     */
    public String[] findReloadedContextMemoryLeaks() {

        System.gc();

        List<String> result = new ArrayList<>();

        for (Map.Entry<ClassLoader, String> entry :
                childClassLoaders.entrySet()) {
            ClassLoader cl = entry.getKey();
            if (cl instanceof WebappClassLoaderBase) {
                if (!((WebappClassLoaderBase) cl).getState().isAvailable()) {
                    result.add(entry.getValue());
                }
            }
        }

        return result.toArray(new String[result.size()]);
    }

    /**
     * 返回当前host的别名列表，如果没有，则返回0
     */
    @Override
    public String[] findAliases() {

        synchronized (aliasesLock) {
            return (this.aliases);
        }

    }


    /**
     * 移除当前host中的一个指定别名
     */
    @Override
    public void removeAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {

            // 先检查别名是否存在，找到之后n设置为下标
            int n = -1;
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    n = i;
                    break;
                }
            }
            if (n < 0) {
                return;
            }

            // 循环移除
            int j = 0;
            String results[] = new String[aliases.length - 1];
            for (int i = 0; i < aliases.length; i++) {
                if (i != n) {
                    results[j++] = aliases[i];
                }
            }
            aliases = results;

        }

        // 叫醒监听的事件
        fireContainerEvent(REMOVE_ALIAS_EVENT, alias);

    }


    /**
     * StandardHost的start()生命周期方法
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Set error report valve
        String errorValve = getErrorReportValveClass();
        if ((errorValve != null) && (!errorValve.equals(""))) {
            try {
                boolean found = false;
                Valve[] valves = getPipeline().getValves();
                for (Valve valve : valves) {
                    if (errorValve.equals(valve.getClass().getName())) {
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    Valve valve =
                        (Valve) Class.forName(errorValve).getConstructor().newInstance();
                    getPipeline().addValve(valve);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString(
                        "standardHost.invalidErrorReportValveClass",
                        errorValve), t);
            }
        }
        //调用父类的start()方法，即ContainerBase类中的方法
        super.startInternal();
    }


    // -------------------- JMX  --------------------
    /**
     * 该方法给JMX使用，返回当前host相关的所有Valves的MBean名称
      */
     public String[] getValveNames() throws Exception {
         Valve [] valves = this.getPipeline().getValves();
         String [] mbeanNames = new String[valves.length];
         for (int i = 0; i < valves.length; i++) {
             if (valves[i] instanceof JmxEnabled) {
                 ObjectName oname = ((JmxEnabled) valves[i]).getObjectName();
                 if (oname != null) {
                     mbeanNames[i] = oname.toString();
                 }
             }
         }

         return mbeanNames;

     }

    /**
     * 查询当前host的别名列表
     */
    public String[] getAliases() {
        synchronized (aliasesLock) {
            return aliases;
        }
    }

    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties = new StringBuilder("type=Host");
        keyProperties.append(getMBeanKeyProperties());

        return keyProperties.toString();
    }

}
