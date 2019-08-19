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

import java.util.Set;

/**
 * 描述一个Valve集合的接口，当被调用invoke()方法后，会被按序执行。
 * Pipline需要一个Valve(通常是最后一个)必须处理request，并应答response。而不是传递request。
 *
 * 通常每个容器有一个与之对应的Pipeline。容器处理request请求的逻辑都放在对应的特定的Valve中，
 * 这个Value必须在pipeline的最后被执行。为了方便这一点，提供了setBasic()方法来设置Valve实例
 * 放到pipeline的最后一个Valve。
 * 其他Valve将被按照添加顺序执行，一定是在basic Valve之前被执行的
 */
public interface Pipeline {


    // ------------------------------------------------------------- Properties


    /**
     * 返回pipeline中basic Valve
     */
    public Valve getBasic();


    /**
     * 给pipeline设置最后一个Valve，也就是basic Valve。
     */
    public void setBasic(Valve valve);


    // --------------------------------------------------------- Public Methods


    /**
     *  新增Valve
     */
    public void addValve(Valve valve);


    /**
     * 返回所有的Valve
     */
    public Valve[] getValves();


    /**
     * 移除Valve
     */
    public void removeValve(Valve valve);


    /**
     * @return the Valve instance that has been distinguished as the basic Valve for this Pipeline (if any).
     */
    public Valve getFirst();

    /**
     * 如果该pipeline中所有valve都支持异步，则返回为true
     */
    public boolean isAsyncSupported();


    /**
     * 获取跟该pipeline相关的容器
     */
    public Container getContainer();


    /**
     * 设置该Pipeline实例相关的容器实例

     */
    public void setContainer(Container container);


    /**
     * 表示不支持异步的Valve
     */
    public void findNonAsyncValves(Set<String> result);
}
