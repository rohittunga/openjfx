/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.tk.quantum;

import com.sun.glass.events.WindowEvent;
import com.sun.glass.ui.Application;
import com.sun.glass.ui.Screen;
import com.sun.glass.ui.Window;
import com.sun.glass.ui.Window.Level;

import com.sun.javafx.tk.FocusCause;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

class GlassWindowEventHandler extends Window.EventHandler implements PrivilegedAction<Void> {

    private final WindowStage stage;

    private Window window;
    private int type;

    public GlassWindowEventHandler(WindowStage stage) {
        this.stage = stage;
    }

    @Override
    public Void run() {
        if (stage == null || stage.stageListener == null) {
            return null;
        }
        switch (type) {
            case WindowEvent.MINIMIZE:
                stage.stageListener.changedIconified(true);
                break;
            case WindowEvent.MAXIMIZE:
                stage.stageListener.changedIconified(false);
                stage.stageListener.changedMaximized(true);
                break;
            case WindowEvent.RESTORE:
                stage.stageListener.changedIconified(false);
                stage.stageListener.changedMaximized(false);
                break;
            case WindowEvent.MOVE: {
                float pScale = window.getPlatformScale();
                Screen screen = window.getScreen();
                float sx = screen == null ? 0 : screen.getX();
                float sy = screen == null ? 0 : screen.getY();
                float wx = window.getX();
                float wy = window.getY();
                float newx = sx + (wx - sx) / pScale;
                float newy = sy + (wy - sy) / pScale;
                stage.stageListener.changedLocation(newx, newy);
                //We need to sync the new x,y for painting
                if (!Application.GetApplication().hasWindowManager()) {
                    QuantumToolkit.runWithRenderLock(() -> {
                        GlassScene scene = stage.getScene();
                        if (scene != null) {
                            scene.updateSceneState();
                        }
                        return null;
                    });
                }
                break;
            }
            case WindowEvent.RESIZE: {
                float pScale = window.getPlatformScale();
                stage.stageListener.changedSize(window.getWidth()  / pScale,
                                                window.getHeight() / pScale);
                 break;
            }
            case WindowEvent.FOCUS_GAINED:
                WindowStage.addActiveWindow(stage);
                stage.stageListener.changedFocused(true, FocusCause.ACTIVATED);
                break;
            case WindowEvent.FOCUS_LOST:
                stage.stageListener.changedFocused(false, FocusCause.DEACTIVATED);
                break;
            case WindowEvent.FOCUS_UNGRAB:
                stage.stageListener.focusUngrab();
                break;
            case WindowEvent.FOCUS_GAINED_FORWARD:
                WindowStage.addActiveWindow(stage);
                stage.stageListener.changedFocused(true, FocusCause.TRAVERSED_FORWARD);
                break;
            case WindowEvent.FOCUS_GAINED_BACKWARD:
                WindowStage.addActiveWindow(stage);
                stage.stageListener.changedFocused(true, FocusCause.TRAVERSED_BACKWARD);
                break;
            case WindowEvent.FOCUS_DISABLED:
                stage.handleFocusDisabled();
                break;
            case WindowEvent.DESTROY:
                stage.setPlatformWindowClosed();
                stage.stageListener.closed();
                break;
            case WindowEvent.CLOSE:
                stage.stageListener.closing();
                break;
            default:
                if (QuantumToolkit.verbose) {
                    System.err.println("GlassWindowEventHandler: unknown type: " + type);
                }
                break;
        }
        return null;
    }

    @Override
    public void handleLevelEvent(int level) {
        QuantumToolkit.runWithoutRenderLock(() -> {
            AccessControlContext acc = stage.getAccessControlContext();
            return AccessController.doPrivileged((PrivilegedAction<Void>)() -> {
                stage.stageListener.changedAlwaysOnTop(level != Level.NORMAL);
                return (Void)null;
            } , acc);
        });
    }

    @Override
    public void handleWindowEvent(final Window window, final long time, final int type) {
        this.window = window;
        this.type = type;

        QuantumToolkit.runWithoutRenderLock(() -> {
            AccessControlContext acc = stage.getAccessControlContext();
            return AccessController.doPrivileged(this, acc);
        });
    }

    @Override
    public void handleScreenChangedEvent(Window window, long time, Screen oldScreen, Screen newScreen) {
        GlassScene scene = stage.getScene();
        if (scene != null) {
            QuantumToolkit.runWithRenderLock(() -> {
                scene.entireSceneNeedsRepaint();
                scene.updateSceneState();
                return null;
            });
        }

        QuantumToolkit.runWithoutRenderLock(() -> {
            AccessControlContext acc = stage.getAccessControlContext();
            return AccessController.doPrivileged((PrivilegedAction<Void>)() -> {
                stage.stageListener.changedScreen(oldScreen, newScreen);
                return (Void)null;
            } , acc);
        });
    }
}
