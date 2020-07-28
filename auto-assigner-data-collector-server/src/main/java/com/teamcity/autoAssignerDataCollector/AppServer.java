package com.teamcity.autoAssignerDataCollector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.issueTracker.errors.NotFoundException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.audit.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.impl.audit.filters.TestId;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class AppServer extends BaseController {
    private final SBuildServer server;
    private final WebControllerManager manager;
    private final SecurityContext securityContext;
    private final ProjectManager projectManager;
    private final AuditLogProvider auditLogProvider;

    private final Gson myGson = new GsonBuilder().setPrettyPrinting().create();

    public AppServer(@NotNull final SBuildServer server,
                     @NotNull final WebControllerManager manager,
                     @NotNull final SecurityContext securityContext,
                     @NotNull final ProjectManager projectManager,
                     @NotNull final AuditLogProvider auditLogProvider) {
        super(server);
        this.server = server;
        this.manager = manager;
        this.securityContext = securityContext;
        this.projectManager = projectManager;
        this.auditLogProvider = auditLogProvider;
        manager.registerController("/demoPlugin.html", this);
    }

    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        if (!isGet(request)) {
            throw new HttpRequestMethodNotSupportedException(request.getMethod());
        }

        @Nullable SProject project = getProjectByExternalId(request.getParameter("projectExternalId"));
        if (project == null) {
            throw new NotFoundException("Project with specified externalProjectId not found");
        }

        List<BuildInfo> builds = new ArrayList<>();
        server.getHistory().getEntries(true).forEach(sFinishedBuild -> {
            if (Objects.requireNonNull(sFinishedBuild.getProjectId()).equals(project.getProjectId())) {
                BuildInfo buildInfo = new BuildInfo(sFinishedBuild);

                List<TestInfo> tests = new ArrayList<>();
                sFinishedBuild.getFullStatistics().getAllTests().forEach(testRun -> {
                    TestInfo testInfo = new TestInfo(testRun);
                    testInfo.setPreviousResponsible(findInAudit(testRun, project));
                    tests.add(testInfo);
                });
                buildInfo.setTests(tests);
                builds.add(buildInfo);
            }
        });

        sendResponse(response, builds);
        return null;
    }

    @NotNull
    public List<String> findInAudit(@NotNull final STestRun testRun, @NotNull SProject project) {
        AuditLogBuilder builder = auditLogProvider.getBuilder();
        builder.setActionTypes(ActionType.TEST_MARK_AS_FIXED, ActionType.TEST_INVESTIGATION_ASSIGN);
        Set<String> objectIds = new HashSet<>();
        objectIds.add(TestId.createOn(testRun.getTest().getTestNameId(), project.getProjectId()).asString());

        builder.setObjectIds(objectIds);
        List<AuditLogAction> lastActions = builder.getLogActions(-1);
        List<String> result = new ArrayList<>();
        for (AuditLogAction action : lastActions) {
            for (ObjectWrapper obj : action.getObjects()) {
                Object user = obj.getObject();
                if (!(user instanceof User)) {
                    continue;
                }

                TestId testId = TestId.fromString(action.getObjectId());
                if (testId != null) {
                    result.add(((User) user).getExtendedName());
                }
            }
        }
        return result;
    }

    @Nullable
    private SProject getProjectByExternalId(@Nullable final String projectExternalId) throws AccessDeniedException {
        if (projectExternalId == null) {
            return null;
        }

        @Nullable SProject project = projectManager.findProjectByExternalId(projectExternalId); // throws AccessDeniedException if no rights
        if (project == null) {
            throw new NotFoundException("Project with specified externalProjectId not found");
        }

        return project;
    }

    private void sendResponse(@NotNull HttpServletResponse servletResponse,
                              @NotNull List<BuildInfo> responsibilities) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(servletResponse.getOutputStream(), StandardCharsets.UTF_8)) {
            servletResponse.setContentType("application/json");
            writer.write(myGson.toJson(responsibilities));
        }
    }
}

