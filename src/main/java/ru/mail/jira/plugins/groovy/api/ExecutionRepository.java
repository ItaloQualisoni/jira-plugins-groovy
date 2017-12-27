package ru.mail.jira.plugins.groovy.api;

import ru.mail.jira.plugins.groovy.api.dto.ScriptExecutionDto;
import ru.mail.jira.plugins.groovy.api.entity.ScriptExecution;

import java.util.List;
import java.util.Map;

public interface ExecutionRepository {
    void trackFromRegistry(int id, long time, boolean successful, String error, Map<String, String> additionalParams);

    void trackInline(String id, long time, boolean successful, String error, Map<String, String> additionalParams);

    List<ScriptExecutionDto> getRegistryExecutions(int scriptId);

    List<ScriptExecutionDto> getInlineExecutions(String scriptId);

    void deleteOldExecutions();
}
