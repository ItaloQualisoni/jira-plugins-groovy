package ru.mail.jira.plugins.groovy.util;

import com.atlassian.jira.util.JiraUtils;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DelegatingClassLoader extends ClassLoader {
    private final Logger logger = LoggerFactory.getLogger(DelegatingClassLoader.class);
    private final Map<String, WeakReference<ClassLoader>> classLoaders;

    public DelegatingClassLoader() {
        super(null);
        this.classLoaders = new LinkedHashMap<>();
        this.classLoaders.put("__local", new WeakReference<>(Thread.currentThread().getContextClassLoader()));
        //loader for jira core classes
        this.classLoaders.put("__jira", new WeakReference<>(JiraUtils.class.getClassLoader()));
    }

    public void ensureAvailability(Set<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            if (plugin.getPluginState() != PluginState.ENABLED) {
                throw new RuntimeException("Plugin " + plugin.getKey() + " is not enabled");
            }
            classLoaders.put(plugin.getKey(), new WeakReference<>(plugin.getClassLoader()));
        }
    }

    public void unloadPlugin(String key) {
        classLoaders.remove(key);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (Map.Entry<String, WeakReference<ClassLoader>> entry : classLoaders.entrySet()) {
            try {
                ClassLoader classLoader = entry.getValue().get();

                if (classLoader == null) {
                    logger.warn("classloader for {} is not present", entry.getKey());
                    continue;
                }

                return classLoader.loadClass(name);
            } catch (ClassNotFoundException ignore) {}
        }
        throw new ClassNotFoundException(name);
    }
}
