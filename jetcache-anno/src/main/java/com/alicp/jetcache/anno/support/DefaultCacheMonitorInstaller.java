/**
 * Created on 2019/6/7.
 */
package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheMonitor;
import com.alicp.jetcache.MultiLevelCache;
import com.alicp.jetcache.event.CachePutAllEvent;
import com.alicp.jetcache.event.CachePutEvent;
import com.alicp.jetcache.event.CacheRemoveAllEvent;
import com.alicp.jetcache.event.CacheRemoveEvent;
import com.alicp.jetcache.support.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class DefaultCacheMonitorInstaller extends AbstractLifecycle implements CacheMonitorInstaller {

    private DefaultCacheMonitorManager defaultCacheMonitorManager;

    @Resource
    private GlobalCacheConfig globalCacheConfig;

    @Autowired(required = false)
    private Consumer<StatInfo> statCallback;

    @Autowired(required = false)
    private CacheMessagePublisher cacheMessagePublisher;

    @Override
    public void addMonitors(String area, String cacheName, Cache cache) {
        addMetricsMonitor(area, cacheName, cache);
        addCacheUpdateMonitor(area, cacheName, cache);
    }

    protected void addCacheUpdateMonitor(String area, String cacheName, Cache cache) {
        if (cacheMessagePublisher != null) {
            CacheMonitor monitor = event -> {
                CacheMessage m = new CacheMessage();
                if (event instanceof CachePutEvent) {
                    CachePutEvent e = (CachePutEvent) event;
                    m.setType(CacheMessage.TYPE_PUT);
                    m.setKeys(new Object[]{e.getKey()});
                } else if (event instanceof CacheRemoveEvent) {
                    CacheRemoveEvent e = (CacheRemoveEvent) event;
                    m.setType(CacheMessage.TYPE_REMOVE);
                    m.setKeys(new Object[]{e.getKey()});
                } else if (event instanceof CachePutAllEvent) {
                    CachePutAllEvent e = (CachePutAllEvent) event;
                    m.setType(CacheMessage.TYPE_PUT_ALL);
                    if (e.getMap() != null) {
                        m.setKeys(e.getMap().keySet().toArray());
                    }
                } else if (event instanceof CacheRemoveAllEvent) {
                    CacheRemoveAllEvent e = (CacheRemoveAllEvent) event;
                    m.setType(CacheMessage.TYPE_REMOVE_ALL);
                    if (e.getKeys() != null) {
                        m.setKeys(e.getKeys().toArray());
                    }
                }
                cacheMessagePublisher.publish(area, cacheName, m);
            };
            cache.config().getMonitors().add(monitor);
        }
    }

    protected void addMetricsMonitor(String area, String cacheName, Cache cache) {
        if (defaultCacheMonitorManager != null) {
            if (cache instanceof MultiLevelCache) {
                MultiLevelCache mc = (MultiLevelCache) cache;
                if (mc.caches().length == 2) {
                    Cache local = mc.caches()[0];
                    Cache remote = mc.caches()[1];
                    DefaultCacheMonitor localMonitor = new DefaultCacheMonitor(cacheName + "_local");
                    local.config().getMonitors().add(localMonitor);
                    DefaultCacheMonitor remoteMonitor = new DefaultCacheMonitor(cacheName + "_remote");
                    remote.config().getMonitors().add(remoteMonitor);
                    defaultCacheMonitorManager.add(localMonitor, remoteMonitor);
                }
            }

            DefaultCacheMonitor monitor = new DefaultCacheMonitor(cacheName);
            cache.config().getMonitors().add(monitor);
            defaultCacheMonitorManager.add(monitor);
        }
    }

    @Override
    protected void doInit() {
        initMetricsMonitor();
    }

    protected void initMetricsMonitor() {
        if (globalCacheConfig.getStatIntervalMinutes() > 0) {
            defaultCacheMonitorManager = new DefaultCacheMonitorManager(globalCacheConfig.getStatIntervalMinutes(),
                    TimeUnit.MINUTES, statCallback);
            defaultCacheMonitorManager.start();
        }
    }

    @Override
    protected void doShutdown() {
        shutdownMetricsMonitor();
    }

    protected void shutdownMetricsMonitor() {
        if (defaultCacheMonitorManager != null) {
            defaultCacheMonitorManager.stop();
        }
        defaultCacheMonitorManager = null;
    }

    public void setGlobalCacheConfig(GlobalCacheConfig globalCacheConfig) {
        this.globalCacheConfig = globalCacheConfig;
    }

    public void setStatCallback(Consumer<StatInfo> statCallback) {
        this.statCallback = statCallback;
    }

    public void setCacheMessagePublisher(CacheMessagePublisher cacheMessagePublisher) {
        this.cacheMessagePublisher = cacheMessagePublisher;
    }

}

