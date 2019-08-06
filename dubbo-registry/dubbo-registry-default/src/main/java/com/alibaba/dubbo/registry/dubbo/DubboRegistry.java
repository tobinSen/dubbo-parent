/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.registry.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ExecutorUtil;
import com.alibaba.dubbo.common.utils.NamedThreadFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.RegistryService;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.alibaba.dubbo.rpc.Invoker;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DubboRegistry
 *
 */
public class DubboRegistry extends FailbackRegistry {

    private final static Logger logger = LoggerFactory.getLogger(DubboRegistry.class);

    // Reconnecting detection cycle: 3 seconds (unit:millisecond)
    private static final int RECONNECT_PERIOD_DEFAULT = 3 * 1000;

    // Scheduled executor service
    private final ScheduledExecutorService reconnectTimer = Executors.newScheduledThreadPool(1, new NamedThreadFactory("DubboRegistryReconnectTimer", true));

    // Reconnection timer, regular check connection is available. If unavailable, unlimited reconnection.
    private final ScheduledFuture<?> reconnectFuture;

    // The lock for client acquisition process, lock the creation process of the client instance to prevent repeated clients
    private final ReentrantLock clientLock = new ReentrantLock();

    //Invoker也是一个节点
    //Node-->URL
    //Node-->Invoker
    private final Invoker<RegistryService> registryInvoker;

    private final RegistryService registryService;

    /**
     * The time in milliseconds the reconnectTimer will wait
     */
    private final int reconnectPeriod;

    public DubboRegistry(Invoker<RegistryService> registryInvoker, RegistryService registryService) {
        //registryUrl：注册中心URL（以dubbo为注册中心，需要将自己的URL注册到【抽象层】中，扩展性）
        super(registryInvoker.getUrl());
        this.registryInvoker = registryInvoker;
        this.registryService = registryService; //这里是一个代理对象
        // Start reconnection timer
        this.reconnectPeriod = registryInvoker.getUrl().getParameter(Constants.REGISTRY_RECONNECT_PERIOD_KEY, RECONNECT_PERIOD_DEFAULT);
        //相对固定的时间内进行延时重试
        reconnectFuture = reconnectTimer.scheduleWithFixedDelay(new Runnable() {
            @Override

            public void run() {
                // Check and connect to the registry
                try {
                    //dubbo为注册中心-->注册到AbstractRegistry抽象化
                    connect();
                } catch (Throwable t) { // Defensive fault tolerance
                    logger.error("Unexpected error occur at reconnect, cause: " + t.getMessage(), t);
                }
            }
            //3秒重试链接
        }, reconnectPeriod, reconnectPeriod, TimeUnit.MILLISECONDS);
    }

    protected final void connect() {
        try {
            // Check whether or not it is connected
            // 判断dubbo为注册中心就是一个Invoker-->Node
            if (isAvailable()) {
                return;
            }
            if (logger.isInfoEnabled()) {
                logger.info("Reconnect to registry " + getUrl());
            }
            //加锁
            clientLock.lock();
            try {
                // Double check whether or not it is connected
                //
                if (isAvailable()) {
                    return;
                }
                //加锁的原因是避免重复链接
                //todo 这里的重试：将该添加,当注册中心更换的时候，向上的URL不需要重新注册，只需要改变向下的扩展持久化注册
                recover();
            } finally {
                clientLock.unlock();
            }
        } catch (Throwable t) { // Ignore all the exceptions and wait for the next retry
            if (getUrl().getParameter(Constants.CHECK_KEY, true)) {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                throw new RuntimeException(t.getMessage(), t);
            }
            logger.error("Failed to connect to registry " + getUrl().getAddress() + " from provider/consumer " + NetUtils.getLocalHost() + " use dubbo " + Version.getVersion() + ", cause: " + t.getMessage(), t);
        }
    }

    @Override
    public boolean isAvailable() {
        if (registryInvoker == null)
            return false;
        return registryInvoker.isAvailable();
    }

    @Override
    public void destroy() {
        super.destroy();
        try {
            // Cancel the reconnection timer
            if (!reconnectFuture.isCancelled()) {
                reconnectFuture.cancel(true);
            }
        } catch (Throwable t) {
            logger.warn("Failed to cancel reconnect timer", t);
        }
        registryInvoker.destroy();
        //优雅关机
        ExecutorUtil.gracefulShutdown(reconnectTimer, reconnectPeriod);
    }

    //dubbo的注册中心实现的注册是内存注册形式
    @Override
    protected void doRegister(URL url) {
        registryService.register(url);
    }

    @Override
    protected void doUnregister(URL url) {
        registryService.unregister(url);
    }

    @Override
    protected void doSubscribe(URL url, NotifyListener listener) {
        registryService.subscribe(url, listener);
    }

    @Override
    protected void doUnsubscribe(URL url, NotifyListener listener) {
        registryService.unsubscribe(url, listener);
    }

    @Override
    public List<URL> lookup(URL url) {
        return registryService.lookup(url);
    }

}
