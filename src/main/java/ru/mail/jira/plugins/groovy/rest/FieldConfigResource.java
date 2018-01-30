package ru.mail.jira.plugins.groovy.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import ru.mail.jira.plugins.groovy.api.repository.FieldConfigRepository;
import ru.mail.jira.plugins.groovy.api.dto.cf.FieldConfigForm;
import ru.mail.jira.plugins.groovy.impl.PermissionHelper;
import ru.mail.jira.plugins.groovy.util.ExceptionHelper;
import ru.mail.jira.plugins.groovy.util.RestExecutor;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Scanned
@Path("/fieldConfig")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FieldConfigResource {
    private final JiraAuthenticationContext authenticationContext;
    private final PermissionHelper permissionHelper;
    private final FieldConfigRepository fieldConfigRepository;

    public FieldConfigResource(
        JiraAuthenticationContext authenticationContext,
        PermissionHelper permissionHelper,
        FieldConfigRepository fieldConfigRepository
    ) {
        this.authenticationContext = authenticationContext;
        this.permissionHelper = permissionHelper;
        this.fieldConfigRepository = fieldConfigRepository;
    }

    @Path("/all")
    @GET
    @WebSudoRequired
    public Response getAllConfigs() {
        return new RestExecutor<>(() -> {
            permissionHelper.checkIfAdmin();

            return fieldConfigRepository.getAllConfigs();
        }).getResponse();
    }

    @Path("/{id}")
    @GET
    @WebSudoRequired
    public Response getFieldConfig(@PathParam("id") long id) {
        return new RestExecutor<>(() -> {
            permissionHelper.checkIfAdmin();

            return fieldConfigRepository.getConfig(id, true);
        }).getResponse();
    }

    @Path("/{id}")
    @PUT
    @WebSudoRequired
    public Response updateFieldConfig(@PathParam("id") long id, FieldConfigForm form) {
        return new RestExecutor<>(() -> {
            permissionHelper.checkIfAdmin();

            return fieldConfigRepository.updateConfig(authenticationContext.getLoggedInUser(), id, form);
        })
            .withExceptionMapper(MultipleCompilationErrorsException.class, Response.Status.BAD_REQUEST, e -> ExceptionHelper.mapCompilationException("scriptBody", e))
            .getResponse();
    }
}