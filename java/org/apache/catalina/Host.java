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
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;


/**
 * Host也是一个容器，在Engine中代表了一个虚拟主机，它被使用在以下几个场景中：
 * - 你希望使用拦截器来观察指定虚拟主机上的每一个请求；
 * - 你希望使用单个HTTP连接器执行Catalina，但是还希望支持多个虚拟host
 *
 * 通常，在部署了一个Catalina服务时，不会使用Host直接连接web服务，因为连接器能利用web Server
 * 的一些能力决定由那一个Context或者Wrapper来处理请求。
 * 父容器只有Engine，子容器一般是Context(代表单个servlet上下文)
 */
public interface Host extends Container {


    // ----------------------------------------------------- Manifest Constants


    /**
     * host的一些事件定义，添加别名
     */
    public static final String ADD_ALIAS_EVENT = "addAlias";


    /**
     * host的一些事件定义，移除别名
     */
    public static final String REMOVE_ALIAS_EVENT = "removeAlias";


    // ------------------------------------------------------------- Properties


    /**
     * 当前host的XML配置路径，可以是一个绝对路径，或相对路径，或一个URL
     * 如果为null，则默认地址为${catalina.base}/conf/engine name/host name
     */
    public String getXmlBase();

    /**
     * 设置host的XML配置路径
     */
    public void setXmlBase(String xmlBase);

    /**
     * 当前host的配置xml文件
     * 区别上面的getXmlBase(),一个是针对路径的，一个是转换成File对象了
     */
    public File getConfigBaseFile();

    /**
     * 当前host的app的路径
     */
    public String getAppBase();


    /**
     * 获取app放的目录的文件引用
     */
    public File getAppBaseFile();


    /**
     * 设置App存放路径
     */
    public void setAppBase(String appBase);


    /**
     * 查询是否自动部署，如果值为true，表名webapps会自动被发现然后被自动部署
     */
    public boolean getAutoDeploy();


    /**
     * 设置当前Host是否自动开始自动部署
     */
    public void setAutoDeploy(boolean autoDeploy);


    /**
     * 新app的context 配置类名称
     */
    public String getConfigClass();


    /**
     * 设置新web app的配置类的class name
     */
    public void setConfigClass(String configClass);


    /**
     * 启动的时候自动部署
     */
    public boolean getDeployOnStartup();


    /**
     * 设置启动时是否自动部署
     */
    public void setDeployOnStartup(boolean deployOnStartup);


    /**
     * 获取自动部署时，需要忽略的文件或地址
     */
    public String getDeployIgnore();


    /**
     * 获取自动部署时需要忽略的文件或地址的正则表达式
     */
    public Pattern getDeployIgnorePattern();


    /**
     * 设置自动部署时需要忽略的文件或者地址
     */
    public void setDeployIgnore(String deployIgnore);


    /**
     * 用于启动和停止context的executor
     */
    public ExecutorService getStartStopExecutor();


    /**
     * 如果为true的话，那么会尝试为应用程序和host的配置创建文件夹
     */
    public boolean getCreateDirs();


    /**
     * 设置是否需要为xmlBean和appBase尝试创建文件夹
     */
    public void setCreateDirs(boolean createDirs);


    /**
     * 查询是否自动卸载程序的老版本
     */
    public boolean getUndeployOldVersions();


    /**
     * 设置是否自动卸载程序的老版本
     */
    public void setUndeployOldVersions(boolean undeployOldVersions);


    // --------------------------------------------------------- Public Methods

    /**
     * 为当前host添加别名
     */
    public void addAlias(String alias);


    /**
     * 获取当前host的所有别名
     */
    public String[] findAliases();


    /**
     * 移除一个指定的别名
     */
    public void removeAlias(String alias);
}
