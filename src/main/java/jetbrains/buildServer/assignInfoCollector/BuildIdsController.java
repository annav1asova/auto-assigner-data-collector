package jetbrains.buildServer.assignInfoCollector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.issueTracker.errors.NotFoundException;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.audit.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.impl.audit.filters.TestId;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class BuildIdsController extends BaseController {
    private final SBuildServer server;
    private final ProjectManager projectManager;

    private final Gson myGson = new GsonBuilder().setPrettyPrinting().create();

    public BuildIdsController(@NotNull final SBuildServer server,
                     @NotNull final WebControllerManager manager,
                     @NotNull final ProjectManager projectManager) {
        super(server);
        this.server = server;
        this.projectManager = projectManager;
        manager.registerController("/assignInfoCollectorIds.html", this);
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

        List<Long> buildIds = new ArrayList<>();

        server.getHistory().getEntries(false).forEach(sFinishedBuild -> {
            if (Objects.requireNonNull(sFinishedBuild.getProjectId()).equals(project.getProjectId())) {
                buildIds.add(sFinishedBuild.getBuildId());
            }
        });

        sendResponse(response, buildIds);
        return null;
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
                              @NotNull List<Long> responsibilities) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(servletResponse.getOutputStream(), StandardCharsets.UTF_8)) {
            servletResponse.setContentType("application/json");
            writer.write(myGson.toJson(responsibilities));
        }
    }
}

