package com.github.claudecodegui.notifications;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AppIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IDE-side reminders when user action is required (permission dialog / plan approval / ask-user-question).
 *
 * Goals:
 * - Provide a visible hint even if the user has switched to another app (taskbar/dock attention request).
 * - Provide an IDE notification with an action to open the tool window.
 * - Avoid spamming: one notification per request key.
 */
public final class ManualActionNotifier {
    public static final String NOTIFICATION_GROUP_ID = "ClaudeCodeGui.ManualAction";

    private static final Map<String, Boolean> notifiedKeys = new ConcurrentHashMap<>();

    private ManualActionNotifier() {}

    public static void notifyOnce(@NotNull Project project, @NotNull String requestKey, @NotNull String title, @NotNull String content) {
        if (project.isDisposed()) return;
        if (notifiedKeys.putIfAbsent(requestKey, Boolean.TRUE) != null) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;

            NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
            Notification notification = group.createNotification(title, content, NotificationType.INFORMATION);
            notification.addAction(NotificationAction.createSimple("Open Claude Code GUI", () -> openToolWindow(project)));
            notification.notify(project);

            // Also show a short-lived status bar hint.
            ClaudeNotifier.showWarning(project, title + " - " + content);

            requestAttentionIfBackground(project);
        });
    }

    public static void requestAttentionIfBackground(@NotNull Project project) {
        if (project.isDisposed()) return;
        Window w = getProjectWindow(project);
        if (w == null || !w.isActive()) {
            AppIcon.getInstance().requestAttention(project, true);
        }
    }

    public static void cancelAttention(@NotNull Project project) {
        if (project.isDisposed()) return;
        // AppIcon does not expose a cancel API in this platform version.
        // We keep this method to allow callers to "clear" attention state in the future.
    }

    public static void openToolWindow(@NotNull Project project) {
        if (project.isDisposed()) return;
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CCG");
        if (toolWindow != null) {
            toolWindow.activate(null, true, true);
        }
    }

    @Nullable
    private static Window getProjectWindow(@NotNull Project project) {
        IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
        if (frame == null) return null;
        return SwingUtilities.getWindowAncestor(frame.getComponent());
    }
}
