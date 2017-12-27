package ru.mail.jira.plugins.groovy.impl.repository;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.cache.Cache;
import com.atlassian.cache.CacheLoader;
import com.atlassian.cache.CacheManager;
import com.atlassian.cache.CacheSettingsBuilder;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.collect.ImmutableList;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.groovy.api.EventListenerRepository;
import ru.mail.jira.plugins.groovy.api.dto.EventListenerDto;
import ru.mail.jira.plugins.groovy.api.dto.EventListenerForm;
import ru.mail.jira.plugins.groovy.api.entity.EventListener;
import ru.mail.jira.plugins.groovy.impl.listener.ScriptedEventListener;
import ru.mail.jira.plugins.groovy.impl.listener.condition.ConditionDescriptor;
import ru.mail.jira.plugins.groovy.impl.listener.condition.ConditionFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class EventListenerRepositoryImpl implements EventListenerRepository {
    private static final String VALUE_KEY = "value";

    private final Logger logger = LoggerFactory.getLogger(EventListenerRepositoryImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, List<ScriptedEventListener>> cache;
    private final ActiveObjects ao;
    private final ConditionFactory conditionFactory;

    @Autowired
    public EventListenerRepositoryImpl(
        @ComponentImport CacheManager cacheManager,
        @ComponentImport ActiveObjects ao,
        ConditionFactory conditionFactory) {
        cache = cacheManager.getCache(EventListenerRepositoryImpl.class.getName() + ".cache",
            new EventListenerCacheLoader(),
            new CacheSettingsBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .remote()
                .replicateViaInvalidation()
                .build()
        );
        this.ao = ao;
        this.conditionFactory = conditionFactory;
    }

    @Override
    public List<ScriptedEventListener> getAllListeners() {
        return cache.get(VALUE_KEY);
    }

    @Override
    public List<EventListenerDto> getListeners() {
        return Arrays
            .stream(ao.find(EventListener.class, Query.select().where("DELETED = ?", false)))
            .map(this::buildDto)
            .collect(Collectors.toList());
    }

    @Override
    public EventListenerDto getEventListener(int id) {
        return buildDto(ao.get(EventListener.class, id));
    }

    @Override
    public EventListenerDto createEventListener(ApplicationUser user, EventListenerForm form) {
        EventListener eventListener = ao.create(
            EventListener.class,
            new DBParam("UUID", UUID.randomUUID().toString()),
            new DBParam("NAME", form.getName()),
            new DBParam("SCRIPT", form.getScript()),
            new DBParam("AUTHOR_KEY", user.getKey()),
            new DBParam("DELETED", false),
            new DBParam("CONDITION", writeDescriptor(form.getCondition()))
        );

        cache.remove(VALUE_KEY);
        return buildDto(eventListener);
    }

    @Override
    public EventListenerDto updateEventListener(ApplicationUser user, int id, EventListenerForm form) {
        EventListener eventListener = ao.get(EventListener.class, id);

        if (eventListener == null || eventListener.isDeleted()) {
            throw new RuntimeException("Event listener is deleted");
        }

        eventListener.setName(form.getName());
        eventListener.setUuid(UUID.randomUUID().toString());
        eventListener.setScript(form.getScript());
        eventListener.setCondition(writeDescriptor(form.getCondition()));
        eventListener.save();

        cache.remove(VALUE_KEY);
        return buildDto(eventListener);
    }

    @Override
    public void deleteEventListener(ApplicationUser user, int id) {
        EventListener listener = ao.get(EventListener.class, id);
        listener.setDeleted(true);
        listener.save();

        cache.remove(VALUE_KEY);
    }

    private EventListenerDto buildDto(EventListener listener) {
        EventListenerDto result = new EventListenerDto();
        result.setId(listener.getID());
        result.setName(listener.getName());
        result.setScript(listener.getScript());
        result.setUuid(listener.getUuid());
        result.setCondition(readDescriptor(listener.getCondition()));
        return result;
    }

    private ScriptedEventListener buildEventListener(EventListener eventListener) {
        return new ScriptedEventListener(
            eventListener.getID(),
            eventListener.getScript(),
            eventListener.getUuid(),
            conditionFactory.create(readDescriptor(eventListener.getCondition()))
        );
    }

    private ConditionDescriptor readDescriptor(String json) {
        try {
            return objectMapper.readValue(json, ConditionDescriptor.class);
        } catch (IOException e) {
            logger.error("unable to read condition descriptor {}", json);
        }
        return null;
    }

    private String writeDescriptor(ConditionDescriptor descriptor) {
        try {
            return objectMapper.writeValueAsString(descriptor);
        } catch (IOException e) {
            logger.error("unable to write condition descriptor {}", descriptor);
        }
        return null;
    }

    private class EventListenerCacheLoader implements CacheLoader<String, List<ScriptedEventListener>> {
        @Nonnull
        @Override
        public List<ScriptedEventListener> load(@Nonnull String key) {
            if (Objects.equals(VALUE_KEY, key)) {
                return Arrays
                    .stream(ao.find(EventListener.class, Query.select().where("DELETED = ?", false)))
                    .map(EventListenerRepositoryImpl.this::buildEventListener)
                    .collect(Collectors.toList());
            } else {
                return ImmutableList.of();
            }
        }
    }
}
