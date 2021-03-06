package ru.mail.jira.plugins.groovy.impl.jql.function.builtin.query;

import com.atlassian.jira.issue.index.DocumentConstants;
import com.atlassian.jira.jql.query.QueryCreationContext;
import com.atlassian.jira.jql.query.QueryProjectRoleAndGroupPermissionsDecorator;
import com.atlassian.jira.jql.util.JqlDateSupport;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.apache.lucene.search.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.groovy.util.compat.JiraCompatibilityHelper;

@Component
public class WorkLogQueryParser extends AbstractEntityQueryParser {
    private final QueryProjectRoleAndGroupPermissionsDecorator queryPermissionDecorator;

    @Autowired
    public WorkLogQueryParser(
        @ComponentImport ProjectRoleManager projectRoleManager,
        @ComponentImport TimeZoneManager timeZoneManager,
        @ComponentImport ProjectManager projectManager,
        @ComponentImport JqlDateSupport jqlDateSupport,
        @ComponentImport GroupManager groupManager,
        @ComponentImport UserManager userManager,
        QueryProjectRoleAndGroupPermissionsDecorator queryPermissionDecorator,
        JiraCompatibilityHelper jiraCompatibilityHelper
    ) {
        super(
            projectRoleManager,
            timeZoneManager,
            projectManager,
            jqlDateSupport,
            groupManager,
            userManager,
            jiraCompatibilityHelper,
            DocumentConstants.WORKLOG_DATE, DocumentConstants.WORKLOG_AUTHOR, DocumentConstants.WORKLOG_COMMENT,
            DocumentConstants.WORKLOG_LEVEL, DocumentConstants.WORKLOG_LEVEL_ROLE
        );
        this.queryPermissionDecorator = queryPermissionDecorator;
    }

    @Override
    protected Query addPermissionsCheck(QueryCreationContext queryCreationContext, Query query) {
        return queryPermissionDecorator.appendPermissionFilterQuery(
            query, queryCreationContext,
            DocumentConstants.WORKLOG_LEVEL, DocumentConstants.WORKLOG_LEVEL_ROLE
        );
    }
}
