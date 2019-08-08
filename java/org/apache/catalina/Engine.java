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

/**
 * 在Container中一个Engine代表了Catalina中整个Servlet引擎，它在以下几个场景中使用：
 * - 你需要在所有Servlet在处理请求前都经过一个拦截器。
 * - 你需要使用独立的HTTP连接器运行Catalina，但是还想有多个虚拟主机（HOST）
 *
 * 一般来说，在部署Catalina是不太会用到Engine，因为Connector会决定使用哪个Context或者Wrapper来处理Request。
 *
 */
public interface Engine extends Container {

    /**
     * 返回Engine使用的默认Host名称
     */
    public String getDefaultHost();


    /**
     * 给Engine设置默认Hostname
     */
    public void setDefaultHost(String defaultHost);


    /**
     * 返回Engine的JvmRoute
     */
    public String getJvmRoute();


    /**
     * 给Engine设置JvmRouteId
     */
    public void setJvmRoute(String jvmRouteId);


    /**
     * 获取相关的Service
     */
    public Service getService();


    /**
     * 设置相关的Service
     */
    public void setService(Service service);
}
