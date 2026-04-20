package com.lucentflow.api.security;

import com.lucentflow.common.entity.Project;

/**
 * Virtual-thread friendly context holder for authenticated project.
 *
 * @author ArchLucent
 * @since 1.0
 */
public final class ProjectContext {

    private static final ThreadLocal<Project> HOLDER = new ThreadLocal<>();

    private ProjectContext() {
    }

    public static void set(Project project) {
        HOLDER.set(project);
    }

    public static Project get() {
        return HOLDER.get();
    }

    public static Long getProjectId() {
        Project project = HOLDER.get();
        return project == null ? null : project.getId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
