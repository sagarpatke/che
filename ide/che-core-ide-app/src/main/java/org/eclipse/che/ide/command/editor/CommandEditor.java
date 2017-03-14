/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.command.editor;

import com.google.common.annotations.VisibleForTesting;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.ide.CoreLocalizationConstant;
import org.eclipse.che.ide.api.command.CommandManager;
import org.eclipse.che.ide.api.command.CommandManager.CommandChangedListener;
import org.eclipse.che.ide.api.command.ContextualCommand;
import org.eclipse.che.ide.api.dialogs.DialogFactory;
import org.eclipse.che.ide.api.editor.AbstractEditorPresenter;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorInput;
import org.eclipse.che.ide.api.icon.Icon;
import org.eclipse.che.ide.api.icon.IconRegistry;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.command.editor.page.CommandEditorPage;
import org.eclipse.che.ide.command.editor.page.commandline.CommandLinePage;
import org.eclipse.che.ide.command.editor.page.context.ContextPage;
import org.eclipse.che.ide.command.editor.page.goal.GoalPage;
import org.eclipse.che.ide.command.editor.page.name.NamePage;
import org.eclipse.che.ide.command.editor.page.previewurl.PreviewUrlPage;
import org.eclipse.che.ide.command.editor.page.project.ProjectsPage;
import org.eclipse.che.ide.command.node.CommandFileNode;
import org.eclipse.che.ide.command.node.NodeFactory;
import org.vectomatic.dom.svg.ui.SVGImage;
import org.vectomatic.dom.svg.ui.SVGResource;

import java.util.LinkedList;
import java.util.List;

import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.EMERGE_MODE;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.WARNING;

/**
 * Presenter for command editor.
 *
 * @author Artem Zatsarynnyi
 */
public class CommandEditor extends AbstractEditorPresenter implements CommandEditorView.ActionDelegate,
                                                                      CommandChangedListener {

    private final CommandEditorView        view;
    private final WorkspaceAgent           workspaceAgent;
    private final IconRegistry             iconRegistry;
    private final CommandManager           commandManager;
    private final NotificationManager      notificationManager;
    private final DialogFactory            dialogFactory;
    private final EditorAgent              editorAgent;
    private final CoreLocalizationConstant coreMessages;
    private final EditorMessages           messages;
    private final NodeFactory              nodeFactory;

    private final List<CommandEditorPage> pages;

    /** Edited command. */
    @VisibleForTesting
    protected ContextualCommand editedCommand;
    /** Initial (before any modification) name of the edited command. */
    private   String            commandNameInitial;

    @Inject
    public CommandEditor(CommandEditorView view,
                         WorkspaceAgent workspaceAgent,
                         IconRegistry iconRegistry,
                         CommandManager commandManager,
                         NamePage namePage,
                         ProjectsPage projectsPage,
                         CommandLinePage commandLinePage,
                         GoalPage goalPage,
                         ContextPage contextPage,
                         PreviewUrlPage previewUrlPage,
                         NotificationManager notificationManager,
                         DialogFactory dialogFactory,
                         EditorAgent editorAgent,
                         CoreLocalizationConstant coreMessages,
                         EditorMessages messages,
                         NodeFactory nodeFactory) {
        this.view = view;
        this.workspaceAgent = workspaceAgent;
        this.iconRegistry = iconRegistry;
        this.commandManager = commandManager;
        this.notificationManager = notificationManager;
        this.dialogFactory = dialogFactory;
        this.editorAgent = editorAgent;
        this.coreMessages = coreMessages;
        this.messages = messages;
        this.nodeFactory = nodeFactory;

        view.setDelegate(this);

        commandManager.addCommandChangedListener(this);

        pages = new LinkedList<>();
        pages.add(namePage);
        pages.add(commandLinePage);
        pages.add(goalPage);
        pages.add(contextPage);
        pages.add(projectsPage);
        pages.add(previewUrlPage);
    }

    @Override
    public void go(AcceptsOneWidget container) {
        container.setWidget(getView());
    }

    @Override
    protected void initializeEditor(EditorAgent.OpenEditorCallback callback) {
        final VirtualFile file = getEditorInput().getFile();

        if (file instanceof CommandFileNode) {
            // make a copy of the given command to avoid modifying of the provided command
            editedCommand = new ContextualCommand(((CommandFileNode)file).getData());

            initializePages();

            for (CommandEditorPage page : pages) {
                view.addPage(page.getView(), page.getTitle());
            }
        } else {
            callback.onInitializationFailed();
        }
    }

    /** Initialize editor's pages with the edited command. */
    private void initializePages() {
        commandNameInitial = editedCommand.getName();

        for (CommandEditorPage page : pages) {
            page.edit(editedCommand);

            page.setDirtyStateListener(() -> {
                updateDirtyState(isDirtyPage());
                view.setSaveEnabled(isDirtyPage());
            });
        }
    }

    /** Checks whether any page is dirty. */
    private boolean isDirtyPage() {
        for (CommandEditorPage page : pages) {
            if (page.isDirty()) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    @Override
    public SVGResource getTitleImage() {
        final VirtualFile file = getEditorInput().getFile();

        if (file instanceof CommandFileNode) {
            final ContextualCommand command = ((CommandFileNode)file).getData();
            final Icon icon = iconRegistry.getIconIfExist("command.type." + command.getType());

            if (icon != null) {
                final SVGImage svgImage = icon.getSVGImage();

                if (svgImage != null) {
                    return icon.getSVGResource();
                }
            }
        }

        return input.getSVGResource();
    }

    @Override
    public String getTitle() {
        return (isDirty() ? "* " : "") + input.getName();
    }

    @Override
    public IsWidget getView() {
        return view;
    }

    @Nullable
    @Override
    public String getTitleToolTip() {
        return input.getName();
    }

    @Override
    public void doSave() {
        doSave(new AsyncCallback<EditorInput>() {
            @Override
            public void onFailure(Throwable caught) {
            }

            @Override
            public void onSuccess(EditorInput result) {
            }
        });
    }

    @Override
    public void doSave(AsyncCallback<EditorInput> callback) {
        commandManager.updateCommand(commandNameInitial, editedCommand).then(arg -> {
            updateDirtyState(false);

            // according to the CommandManager#updateCommand contract
            // command's name after updating may differ from the proposed name
            // in order to prevent name duplication
            editedCommand.setName(arg.getName());

            if (!commandNameInitial.equals(editedCommand.getName())) {
                input.setFile(nodeFactory.newCommandFileNode(editedCommand));
            }

            initializePages();

            callback.onSuccess(getEditorInput());
        }).catchError((Operation<PromiseError>)arg -> {
            notificationManager.notify(messages.editorMessageUnableToSave(),
                                       arg.getMessage(),
                                       WARNING,
                                       EMERGE_MODE);

            callback.onFailure(arg.getCause());

            throw new OperationException(arg.getMessage());
        });
    }

    @Override
    public void doSaveAs() {
    }

    @Override
    public void activate() {
    }

    @Override
    public void close(boolean save) {
        workspaceAgent.removePart(this);
    }

    @Override
    public void onClosing(AsyncCallback<Void> callback) {
        if (!isDirty()) {
            callback.onSuccess(null);
        } else {
            dialogFactory.createChoiceDialog(
                    coreMessages.askWindowCloseTitle(),
                    coreMessages.messagesSaveChanges(getEditorInput().getName()),
                    coreMessages.yesButtonTitle(),
                    coreMessages.noButtonTitle(),
                    coreMessages.cancelButton(),
                    () -> doSave(new AsyncCallback<EditorInput>() {
                        @Override
                        public void onSuccess(EditorInput result) {
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onFailure(Throwable caught) {
                            callback.onFailure(null);
                        }
                    }),
                    () -> callback.onSuccess(null),
                    () -> callback.onFailure(null)).show();
        }
    }

    @Override
    public void onCommandCancel() {
        close(false);
    }

    @Override
    public void onCommandSave() {
        doSave();
    }

    @Override
    public void onCommandAdded(ContextualCommand command) {
    }

    @Override
    public void onCommandUpdated(ContextualCommand previousCommand, ContextualCommand command) {
    }

    @Override
    public void onCommandRemoved(ContextualCommand command) {
        if (command.getName().equals(editedCommand.getName())) {
            editorAgent.closeEditor(this);
            Scheduler.get().scheduleDeferred(() -> commandManager.removeCommandChangedListener(this));
        }
    }
}
