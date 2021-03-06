package ru.mail.jira.plugins.groovy.impl.jql;

import com.atlassian.jira.plugin.jql.function.JqlFunctionModuleDescriptorImpl;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.module.ModuleFactory;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.collect.ImmutableMap;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.dom4j.tree.DefaultElement;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.groovy.api.dto.jql.JqlFunctionScriptDto;
import ru.mail.jira.plugins.groovy.api.jql.ScriptedJqlFunction;
import ru.mail.jira.plugins.groovy.api.jql.ScriptedJqlQueryFunction;
import ru.mail.jira.plugins.groovy.api.jql.ScriptedJqlValuesFunction;
import ru.mail.jira.plugins.groovy.api.service.ScriptService;
import ru.mail.jira.plugins.groovy.api.jql.CustomFunction;
import ru.mail.jira.plugins.groovy.impl.jql.function.QueryFunctionAdapter;
import ru.mail.jira.plugins.groovy.impl.jql.function.ValuesFunctionAdapter;
import ru.mail.jira.plugins.groovy.util.Const;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class ModuleManager {
    private final Logger logger = LoggerFactory.getLogger(ModuleReplicationService.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Map<String, ServiceRegistration> registeredServices = new ConcurrentHashMap<>();
    private final Map<String, String> moduleKeyToFunction = new ConcurrentHashMap<>();
    private final Map<String, CustomFunction> allFunctions = new ConcurrentHashMap<>();

    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final PluginAccessor pluginAccessor;
    private final BundleContext bundleContext;
    private final ScriptService scriptService;

    @Autowired
    public ModuleManager(
        @ComponentImport JiraAuthenticationContext jiraAuthenticationContext,
        @ComponentImport PluginAccessor pluginAccessor,
        BundleContext bundleContext,
        ScriptService scriptService
    ) {
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.pluginAccessor = pluginAccessor;
        this.bundleContext = bundleContext;
        this.scriptService = scriptService;
    }

    private CustomFunction initializeFunction(JqlFunctionScriptDto script) {
        try {
            Class scriptClass = scriptService.parseClassStatic(script.getScriptBody(), false, ImmutableMap.of());

            if (ScriptedJqlFunction.class.isAssignableFrom(scriptClass)) {
                if (Arrays.stream(scriptClass.getConstructors()).anyMatch(it -> it.getParameterCount() == 0)) {
                    Object functionInstance = scriptClass.getConstructor().newInstance();

                    if (functionInstance instanceof ScriptedJqlValuesFunction) {
                        return new ValuesFunctionAdapter(
                            getScriptModuleKey(script.getId()),
                            script.getName(),
                            (ScriptedJqlValuesFunction) functionInstance
                        );
                    } else if (functionInstance instanceof ScriptedJqlQueryFunction) {
                        return new QueryFunctionAdapter(
                            getScriptModuleKey(script.getId()),
                            script.getName(),
                            (ScriptedJqlQueryFunction) functionInstance
                        );
                    } else {
                        logger.error("Constructed object is not instance of ScriptedJqlFunction {} ({})", script.getName(), script.getId());
                    }
                } else {
                    logger.error("Did not find noargs constructor for {} ({})", script.getName(), script.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Unable to initialize function {} ({})", script.getName(), script.getId(), e);
        }

        return null;
    }

    private JqlFunctionModuleDescriptorImpl getModuleDescriptor(Plugin plugin, CustomFunction function) {
        JqlFunctionModuleDescriptorImpl module = new JqlFunctionModuleDescriptorImpl(
            jiraAuthenticationContext,
            new ModuleFactory() {
                @Override
                public <T> T createModule(String name, ModuleDescriptor<T> moduleDescriptor) throws PluginParseException {
                    return (T) function;
                }
            }
        );

        DefaultElement element = new DefaultElement("jql-function");
        element.addAttribute("key", function.getModuleKey());
        DefaultElement fname = new DefaultElement("fname");
        fname.setText(function.getFunctionName());
        element.add(fname);
        DefaultElement list = new DefaultElement("list");
        list.setText(String.valueOf(function.isList()));
        element.add(list);
        module.init(plugin, element);

        return module;
    }

    private void registerDescriptor(String functionName, ModuleDescriptor descriptor) {
        logger.debug("registering function with name: {}", functionName);
        Lock lock = this.lock.writeLock();

        lock.lock();
        try {
            String moduleKey = descriptor.getCompleteKey();
            unregisterDescriptor(moduleKey);

            registeredServices.put(
                descriptor.getKey(),
                bundleContext.registerService(ModuleDescriptor.class.getName(), descriptor, null)
            );
            allFunctions.put(functionName, (CustomFunction) descriptor.getModule());
            moduleKeyToFunction.put(descriptor.getKey(), functionName);
        } finally {
            lock.unlock();
        }
    }

    private void unregisterDescriptor(String moduleKey) {
        Lock lock = this.lock.writeLock();

        lock.lock();
        try {
            ServiceRegistration existingRegistration = registeredServices.remove(moduleKey);
            if (existingRegistration != null) {
                try {
                    existingRegistration.unregister();

                    String functionName = moduleKeyToFunction.remove(moduleKey);

                    logger.debug("unregistering function with name: {}", functionName);

                    CustomFunction function = allFunctions.remove(functionName);
                    if (function instanceof ValuesFunctionAdapter) {
                        ScriptedJqlFunction delegate = ((ValuesFunctionAdapter) function).getDelegate();
                        if (delegate != null) {
                            InvokerHelper.removeClass(delegate.getClass());
                        }
                    }
                } catch (IllegalStateException e) {
                    logger.debug("already unregistered", e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void registerScript(JqlFunctionScriptDto script) {
        CustomFunction function = initializeFunction(script);
        if (function != null) {
            registerDescriptor(script.getName(), getModuleDescriptor(getCurrentPlugin(), function));
        }
    }

    public void unregisterScript(int id) {
        unregisterDescriptor(getScriptModuleKey(id));
    }

    public void registerBuiltInFunction(CustomFunction function) {
        registerDescriptor(function.getFunctionName(), getModuleDescriptor(getCurrentPlugin(), function));
    }

    public void unregisterAll() {
        Lock lock = this.lock.readLock();

        lock.lock();
        try {
            for (ServiceRegistration serviceRegistration : registeredServices.values()) {
                try {
                    serviceRegistration.unregister();
                    //todo: check if we need to unregister modules when plugin is disabled
                } catch (IllegalStateException e) {
                    logger.debug("already unregistered", e);
                } catch (Exception e) {
                    logger.error("unable to unregister {}", serviceRegistration);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public Map<String, CustomFunction> getAllFunctions() {
        Lock lock = this.lock.readLock();

        lock.lock();
        try {
            return allFunctions;
        } finally {
            lock.unlock();
        }
    }

    private String getScriptModuleKey(int id) {
        return "custom-function-user-" + id;
    }

    private Plugin getCurrentPlugin() {
        return pluginAccessor.getPlugin(Const.PLUGIN_KEY);
    }
}
