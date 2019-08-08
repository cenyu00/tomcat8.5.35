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
package org.apache.catalina;

import java.beans.PropertyChangeListener;
import java.io.File;

import javax.management.ObjectName;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;


/**
 * Container用于封装和管理Servlet，以及具体处理Request请求。
 * 一个Container是处理接收到的一个request请求，并返回response的组件。
 * Container可选的支持Valves的pipeline来按照顺序处理请求。
 *
 * Container也存在以及子组件：
 *  - Engine：表示Catalina的Servlet 引擎，可以包含多个Host和Context组件，或者其他自定义的组件。
 *  - Host：表示虚拟主机，可以包含多个Context组件
 *  - Context：表示一个Servlet的上下文，可以包含多个Wrapper组件。代表一个应用程序，对应着平时开发的一套程序，一个war包解压后的文件
 *  - Wrapper: 表示一个独立的Servlet定义。每一个Wrapper封装着一个Servlet
 *
 * 一个部署的Catalina并不是必须好包含上面所有的组件。例如，一个内嵌在网络设备职工的应用可能仅仅包含一个Context和几个Wrapper，
 * 或者当这个应用很小的时候可能仅有一个Wrapper。
 *
 * Container还支持下面这些组件：
 * Loader：类加载器
 * Logger：日志
 * Manager:管理Session池
 * Realm:安全的只读接口，用于验证用户身份和角色的。
 * Resources：JNDI
 */
public interface Container extends Lifecycle {


    // ----------------------------------------------------- Manifest Constants


/**
 * addChild事件
 */
public static final String ADD_CHILD_EVENT = "addChild";


/**
 * addValve事件
 */
public static final String ADD_VALVE_EVENT = "addValve";


/**
 * removeChild事件
 */
public static final String REMOVE_CHILD_EVENT = "removeChild";


/**
 * removeValve事件
 */
public static final String REMOVE_VALVE_EVENT = "removeValve";


    // ------------------------------------------------------------- Properties

    /**
     * 获取当前Container的日志对象
     */
    public Log getLogger();


    /**
     * 返回Container使用的Logger名称
     */
    public String getLogName();


    /**
     * 获取当前Container的JMX名称
     */
    public ObjectName getObjectName();


    /**
     * 获取JMX的domain名称（其实不是特别懂这个是干嘛的）
     */
    public String getDomain();


    /**
     * 计算key属性，用于添加到Javax上。（具体放在JMX里进行分析）
     */
    public String getMBeanKeyProperties();


    /**
     * 返回管理Valve的Pipeline对象
     */
    public Pipeline getPipeline();


    /**
     * 返回当前Container所在的集群
     */
    public Cluster getCluster();


    /**
     * 设置Container的集群
     */
    public void setCluster(Cluster cluster);


    /**
     * 获取容器后台进程执行的延迟时间。用于控制每个容器可以跑一些后台进程
     */
    public int getBackgroundProcessorDelay();


    /**
     * 设置容器后台进程的延迟时间
     */
    public void setBackgroundProcessorDelay(int delay);


    /**
     * 返回当前Container的名称，Container必须唯一
     */
    public String getName();


    /**
     *  设置Container的名称
     */
    public void setName(String name);


    /**
     * 获取父容器，如果没有就返回null
     */
    public Container getParent();


    /**
     *  给当前Container设置父容器
     */
    public void setParent(Container container);


    /**
     * 返回父类加载器
     */
    public ClassLoader getParentClassLoader();


    /**
     * 设置父类加载器
     */
    public void setParentClassLoader(ClassLoader parent);


    /**
     * 获取当前Container的Realm域
     */
    public Realm getRealm();


    /**
     * 给当前Container设置Realm容器
     */
    public void setRealm(Realm realm);


    // --------------------------------------------------------- Public Methods


    /**
     * 执行一个周期任务，例如重新加载。此方法在容器的类加载上下问中被调用。
     */
    public void backgroundProcess();


    /**
     * 添加新的子容器。
     */
    public void addChild(Container child);


    /**
     * 添加Container的事件监听器
     */
    public void addContainerListener(ContainerListener listener);


    /**
     * 添加Container的属性变化的监听器
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * 通过Container的名称查找Container
     */
    public Container findChild(String name);


    /**
     * 获取当前Container的所有子容器
     */
    public Container[] findChildren();


    /**
     * 获取当前容器的所有事件监听器
     */
    public ContainerListener[] findContainerListeners();


    /**
     * 从当前容器中移除一个指定的子容器
     */
    public void removeChild(Container child);


    /**
     * 移除Container指定的事件监听器
     */
    public void removeContainerListener(ContainerListener listener);


    /**
     * 移除Container的属性监听器
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * 通知容器的所有事件监听器
     */
    public void fireContainerEvent(String type, Object data);


    /**
     * Log a request/response that was destined for this container but has been
     * handled earlier in the processing chain so that the request/response
     * still appears in the correct access logs.
     * @param request       Request (associated with the response) to log
     * @param response      Response (associated with the request) to log
     * @param time          Time taken to process the request/response in
     *                      milliseconds (use 0 if not known)
     * @param   useDefault  Flag that indicates that the request/response should
     *                      be logged in the engine's default access log
     */
    public void logAccess(Request request, Response response, long time,
            boolean useDefault);


    /**
     * Obtain the AccessLog to use to log a request/response that is destined
     * for this container. This is typically used when the request/response was
     * handled (and rejected) earlier in the processing chain so that the
     * request/response still appears in the correct access logs.
     *
     * @return The AccessLog to use for a request/response destined for this
     *         container
     */
    public AccessLog getAccessLog();


    /**
     * 获取用于start和stop子容器的可用线程数量。允许并行的调用自容器的start/stop方法
     *
     */
    public int getStartStopThreads();


    /**
     *
     * 设置用于start和stop自容器的可用线程数量。
     */
    public void setStartStopThreads(int startStopThreads);


    /**
     * 获取CatalinBase的值
     */
    public File getCatalinaBase();


    /**
     * 获取CatalinaHome的值
     */
    public File getCatalinaHome();
}
