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

import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;

/**
 * 一个Service由一组：多个Connectors和一个Container组成的。举个例子，这种做法支持同时使用non-SSL
 * 和SSL connector访问同一个web app。
 *
 * 一个给定的jvm能包含多个Service实例，然而，他们相互独立，仅仅依赖在同一个JVM上。
 */
public interface Service extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * 返回Engine。
     */
    public Engine getContainer();

    /**
     * 设置Engine，专门处理Connectors请求的，并关联在该Service上的。
     */
    public void setContainer(Engine engine);

    /**
     * 返回Service的名字
     */
    public String getName();

    /**
     * 设置Service的名字
     */
    public void setName(String name);

    /**
     * 返回关联的Server
     */
    public Server getServer();

    /**
     * 设置关联的Server
     */
    public void setServer(Server server);

    /**
     * 返回这个Service的父类加载器，如果没有设置，返回关联的Server的父类加载器，
     * 如果Server也没有设置，返回System Class Loader。
     */
    public ClassLoader getParentClassLoader();

    /**
     * 设置这个Service的父来加载器
     */
    public void setParentClassLoader(ClassLoader parent);

    /**
     * @return the domain under which this container will be / has been
     * registered.
     */
    public String getDomain();


    // --------------------------------------------------------- Public Methods

    /**
     * 增加Connector
     */
    public void addConnector(Connector connector);

    /**
     * 返回所有的Connectors
     */
    public Connector[] findConnectors();

    /**
     * 移除指定的Connector
     */
    public void removeConnector(Connector connector);

    /**
     * 为该Service添加一个线程池
     */
    public void addExecutor(Executor ex);

    /**
     * 返回所有的线程池
     */
    public Executor[] findExecutors();

    /**
     * 返回指定的线程池
     */
    public Executor getExecutor(String name);

    /**
     * 移除指定的线程池
     */
    public void removeExecutor(Executor ex);

    /**
     * 返回该Service关联的Mapper
     */
    Mapper getMapper();
}
