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

import java.io.File;

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.startup.Catalina;

/**
 * 一个Server代表着Catalina Servlet容器的全部，它的属性代表着servlet容器的全部规格参数，
 * 一个Server可能包含一个或者多个的Services，以及顶级的命名资源。
 *
 * 一般来说，实现该接口的实现类也需要实现Lifecycle接口，例如当start()或者stop()方法被调用时，
 * 所有包含在server中的services也会被执行start和stop
 *
 * 另外的，该接口的实现类也需要使用port端口属性来打开一个socket连接，当一个连接被接收，
 * 第一行数据将被读取并与特定的shutdown命令进行比较，如果匹配，server将被停止。
 *
 * 注意：实现类需要注册单例的实例，通过ServerFactory类的构造函数。
 */
public interface Server extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * 返回全局naming resource
     */
    public NamingResourcesImpl getGlobalNamingResources();


    /**
     * 设置全局naming resource
     */
    public void setGlobalNamingResources
        (NamingResourcesImpl globalNamingResources);


    /**
     * 返回全局naming resouces的context
     */
    public javax.naming.Context getGlobalNamingContext();


    /**
     * 返回shutdown监听的端口号
     */
    public int getPort();


    /**
     * 设置shutdown监听的端口号
     */
    public void setPort(int port);


    /**
     * 返回shutdown监听的地址
     */
    public String getAddress();


    /**
     * 设置shutdown监听的地址
     */
    public void setAddress(String address);


    /**
     * 返回执行shutdown命令的字符串
     */
    public String getShutdown();


    /**
     * 设置Server等到那一条命令时会执行shutdown
     */
    public void setShutdown(String shutdown);


    /**
     * 返回Server的父类加载器
     */
    public ClassLoader getParentClassLoader();


    /**
     * 为server设置一个父类加载器
     */
    public void setParentClassLoader(ClassLoader parent);


    /**
     * 返回外部的startup/shutdown组件
     */
    public Catalina getCatalina();

    /**
     * Set the outer Catalina startup/shutdown component if present.
     *
     * 如果存在外部的startup/shutdown组件，可以在这里设置
     */
    public void setCatalina(Catalina catalina);


    /**
     * 返回CatalinaBase的值
     */
    public File getCatalinaBase();

    /**
     * 设置CatalinaBase的值
     */
    public void setCatalinaBase(File catalinaBase);


    /**
     * 返回CatalinaHome的值
     */
    public File getCatalinaHome();

    /**
     * 设置CatalinaHome值，CatalinaHome可能会和CatalinaBase的值一样（默认是一样的）
     */
    public void setCatalinaHome(File catalinaHome);


    // --------------------------------------------------------- Public Methods


    /**
     * 增加service
     */
    public void addService(Service service);


    /**
     * 等待直到收到争取的shutdown命令，然后返回
     */
    public void await();


    /**
     * 查找指定的Service
     */
    public Service findService(String name);


    /**
     * 返回所有的Service数组
     */
    public Service[] findServices();


    /**
     * 移除一个Service
     */
    public void removeService(Service service);


    /**
     * 获取操作关联JDNI的naming context的必要的token
     */
    public Object getNamingToken();
}
