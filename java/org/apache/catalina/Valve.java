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

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

/**
 * 一个Valve是跟指定容器相关的request处理组件。一系列相关的Valve会被放入同一个pipeline。
 */
public interface Valve {


    //-------------------------------------------------------------- Properties

    /**
     * 返回pipeline中下一个Valve
     */
    public Valve getNext();


    /**
     * 设置当前Valve的下一个Valve
     */
    public void setNext(Valve valve);


    //---------------------------------------------------------- Public Methods


    /**
     * 后台执行的方法，该方法应该在对应容器的上下文中被调用
     */
    public void backgroundProcess();


    /**
     * 执行request请求
     * 一个Valve将按照顺序执行一下行为：
     * - 检查或者修改对应request或response属性
     * - 检查request属性，生成response，并返回
     * - 调用下一个Valve
     *
     *
     * <p>A Valve <b>MUST NOT</b> do any of the following things:</p>
     * <ul>
     * <li>Change request properties that have already been used to direct
     *     the flow of processing control for this request (for instance,
     *     trying to change the virtual host to which a Request should be
     *     sent from a pipeline attached to a Host or Context in the
     *     standard implementation).
     * <li>Create a completed Response <strong>AND</strong> pass this
     *     Request and Response on to the next Valve in the pipeline.
     * <li>Consume bytes from the input stream associated with the Request,
     *     unless it is completely generating the response, or wrapping the
     *     request before passing it on.
     * <li>Modify the HTTP headers included with the Response after the
     *     <code>getNext().invoke()</code> method has returned.
     * <li>Perform any actions on the output stream associated with the
     *     specified Response after the <code>getNext().invoke()</code> method has
     *     returned.
     * </ul>
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     * @exception ServletException if a servlet error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException;


    /**
     *是否支持异步
     */
    public boolean isAsyncSupported();
}
