/*
 * Created on Nov 29, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.eclipse.ui.internal.macro;

import java.io.PrintWriter;
import java.util.ArrayList;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.custom.TableTree;
import org.eclipse.swt.widgets.*;
import org.w3c.dom.*;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.*;
import org.eclipse.ui.commands.IWorkbenchCommandSupport;
import org.eclipse.ui.keys.*;
import org.eclipse.ui.keys.SWTKeySupport;

/**
 * @author dejan
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class MacroCommandShell implements IWritable, IPlayable {
	private String path;

	private ArrayList commands;

	private int expectedReturnCode;

	private transient Event lastEvent;

	private transient Display display;

	private transient Shell shell;

	private transient Window window;

	private static class NestedShell implements Listener, Runnable {
		private MacroCommandShell cshell;

		private Display display;

		private Shell nshell;

		private boolean released;

		private CoreException exception;

		private IProgressMonitor monitor;

		public NestedShell(Display display, MacroCommandShell cshell,
				IProgressMonitor monitor) {
			this.display = display;
			this.cshell = cshell;
			this.monitor = monitor;
		}

		public void handleEvent(Event e) {
			if (e.widget instanceof Shell) {
				// shell activated
				Shell shell = (Shell) e.widget;
				IPath path = MacroUtil.getShellId(shell);
				String sid = path.toString();
				if (sid.equals(cshell.getId())) {
					shell.getDisplay().removeFilter(SWT.Activate, this);
					released = true;
					this.nshell = shell;
					shell.getDisplay().asyncExec(this);
				}
			}
		}

		public boolean getResult() {
			return cshell.matchesReturnCode();
		}

		public boolean isReleased() {
			return released;
		}

		public void run() {
			try {
				cshell.playback(display, nshell, monitor);
			} catch (CoreException e) {
				this.exception = e;
				if (nshell != null && !nshell.isDisposed())
					nshell.close();
			}
		}

		public CoreException getException() {
			return exception;
		}
	}

	public MacroCommandShell() {
		this(null, null);
	}

	public String getId() {
		return path;
	}

	public MacroCommandShell(Shell shell, String path) {
		commands = new ArrayList();
		this.shell = shell;
		this.path = path;
		hookWindow(false);
	}

	private void hookWindow(boolean playback) {
		if (shell != null) {
			if (!playback)
				doHookWindow();
			else
				display.syncExec(new Runnable() {
					public void run() {
						doHookWindow();
					}
				});
		}
	}

	private void doHookWindow() {
		Object data = shell.getData();
		if (data != null && data instanceof Window)
			this.window = (Window) data;
	}

	public void load(Node node) {
		this.path = MacroUtil.getAttribute(node, "id");
		String codeId = MacroUtil.getAttribute(node, "return-code");
		if (codeId != null) {
			try {
				expectedReturnCode = new Integer(codeId).intValue();
			} catch (NumberFormatException e) {
			}
		}
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				String name = child.getNodeName();
				if (name.equals("command"))
					processCommand(child);
				else if (name.equals("shell"))
					processShell(child);
			}
		}
	}

	private void processCommand(Node node) {
		String wid = MacroUtil.getAttribute(node, "widgetId");
		String cid = MacroUtil.getAttribute(node, "contextId");
		String type = MacroUtil.getAttribute(node, "type");
		if (type == null)
			return;
		MacroCommand command = null;
		WidgetIdentifier wi = (wid != null && cid != null) ? new WidgetIdentifier(
				new Path(wid), new Path(cid))
				: null;
		if (type.equals(ModifyCommand.TYPE))
			command = new ModifyCommand(wi);
		else if (type.equals(BooleanSelectionCommand.TYPE))
			command = new BooleanSelectionCommand(wi);
		else if (type.equals(StructuredSelectionCommand.TYPE))
			command = new StructuredSelectionCommand(wi);
		else if (type.equals(ExpansionCommand.TYPE))
			command = new ExpansionCommand(wi);
		else if (type.equals(CheckCommand.TYPE))
			command = new CheckCommand(wi);
		else if (type.equals(FocusCommand.TYPE))
			command = new FocusCommand(wi);
		else if (type.equals(DefaultSelectionCommand.TYPE))
			command = new DefaultSelectionCommand(wi);
		else if (type.equals(ChoiceSelectionCommand.TYPE))
			command = new ChoiceSelectionCommand(wi);
		else if (type.equals(WaitCommand.TYPE))
			command = new WaitCommand();
		if (command != null) {
			command.load(node);
			commands.add(command);
		}
	}

	private void processShell(Node node) {
		MacroCommandShell shell = new MacroCommandShell();
		shell.load(node);
		commands.add(shell);
	}

	public void addCommandShell(MacroCommandShell cshell) {
		commands.add(cshell);
	}

	public void write(String indent, PrintWriter writer) {
		writer.print(indent);
		writer.print("<shell id=\"");
		writer.print(path);
		writer.print("\" return-code=\"");
		writer.print(expectedReturnCode + "");
		writer.println("\">");
		String cindent = indent + "   ";
		for (int i = 0; i < commands.size(); i++) {
			IWritable writable = (IWritable) commands.get(i);
			if (i < commands.size() - 1 || !(writable instanceof WaitCommand))
				writable.write(cindent, writer);
		}
		writer.print(indent);
		writer.println("</shell>");
	}

	public void addEvent(Event event) {
		if (event.widget instanceof Control) {
			if (((Control) event.widget).isVisible() == false)
				return;
		}
		MacroCommand command = createCommand(event);
		if (command != null) {
			command.processEvent(event);
			MacroCommand lastCommand = getLastCommand();
			if (lastCommand != null
					&& lastCommand.getWidgetId().equals(command.getWidgetId())
					&& lastCommand.getType().equals(FocusCommand.TYPE)
					&& isFocusCommand(command.getType())) {
				// focus followed by select or modify - focus implied
				commands.remove(lastCommand);
			}
			commands.add(command);
			lastEvent = event;
		}
	}

	public void addPause() {
		WaitCommand command = new WaitCommand();
		MacroCommand lastCommand = getLastCommand();
		if (lastCommand != null && lastCommand.getType() != WaitCommand.TYPE)
			commands.add(command);
	}

	public void extractExpectedReturnCode() {
		if (window != null)
			expectedReturnCode = window.getReturnCode();
	}

	public boolean matchesReturnCode() {
		if (window != null) {
			return window.getReturnCode() == expectedReturnCode;
		}
		return true;
	}

	private boolean isFocusCommand(String type) {
		return type.equals(BooleanSelectionCommand.TYPE)
				|| type.equals(StructuredSelectionCommand.TYPE)
				|| type.equals(ExpansionCommand.TYPE)
				|| type.equals(CheckCommand.TYPE)
				|| type.equals(ModifyCommand.TYPE)
				|| type.equals(DefaultSelectionCommand.TYPE);
	}

	protected MacroCommand createCommand(Event event) {
		MacroCommand lastCommand = getLastCommand();
		if (lastEvent != null && lastEvent.widget.equals(event.widget)
				&& lastEvent.type == event.type) {
			if (lastCommand != null && lastCommand.mergeEvent(event))
				return null;
		}
		MacroCommand command = null;
		WidgetIdentifier wi = MacroUtil.getWidgetIdentifier(event.widget);
		if (wi == null)
			return null;

		switch (event.type) {
		case SWT.Modify:
			if (!isEditable(event.widget))
				return null;
			command = new ModifyCommand(wi);
			break;
		case SWT.Selection:
		case SWT.DefaultSelection:
			command = createSelectionCommand(wi, event);
			break;
		case SWT.FocusIn:
			command = new FocusCommand(wi);
			break;
		case SWT.Expand:
		case SWT.Collapse:
			command = new ExpansionCommand(wi);
			break;
	/*
		case SWT.KeyUp:
			command = findKeyBinding(wi, event);
			break;
		*/
		}
		return command;
	}

	private boolean isEditable(Widget widget) {
		if (widget instanceof Control) {
			Control control = (Control) widget;
			if (!control.isEnabled())
				return false;
			if (control instanceof Text)
				return ((Text) control).getEditable();
			if (control instanceof Combo || control instanceof CCombo)
				return ((control.getStyle() & SWT.READ_ONLY) == 0);
			if (control instanceof StyledText)
				return ((StyledText) control).getEditable();
		}
		return true;
	}

	private MacroCommand createSelectionCommand(WidgetIdentifier wid,
			Event event) {
		if (event.type == SWT.DefaultSelection)
			return new DefaultSelectionCommand(wid);
		if (event.widget instanceof MenuItem
				|| event.widget instanceof ToolItem
				|| event.widget instanceof Button) {
			if (wid.getWidgetId().endsWith(
					"org.eclipse.ui.macro.actions.StopAction"))
				return null;
			return new BooleanSelectionCommand(wid);
		}
		if (event.widget instanceof Tree || event.widget instanceof Table
				|| event.widget instanceof TableTree) {
			if (event.detail == SWT.CHECK)
				return new CheckCommand(wid);
			else
				return new StructuredSelectionCommand(wid);
		}
		if (event.widget instanceof TabFolder
				|| event.widget instanceof CTabFolder)
			return new ChoiceSelectionCommand(wid);
		if (event.widget instanceof Combo || event.widget instanceof CCombo)
			return new ChoiceSelectionCommand(wid);
		return null;
	}

	private MacroCommand findKeyBinding(WidgetIdentifier wid, Event e) {
		System.out.println("mask=" + e.stateMask + ", char=" + e.character);
		java.util.List keyStrokes = MacroUtil.generatePossibleKeyStrokes(e);
		if (keyStrokes.size() == 0)
			return null;
		for (int i = 0; i < keyStrokes.size(); i++) {
			if (!((KeyStroke) keyStrokes.get(i)).isComplete())
				return null;
		}
		System.out.println("keyStrokes=" + keyStrokes);
		IWorkbenchCommandSupport csupport = PlatformUI.getWorkbench()
				.getCommandSupport();
		KeySequence keySequence = KeySequence.getInstance(keyStrokes);
		System.out.println("keySequence=" + keySequence);
		String commandId = csupport.getCommandManager().getPerfectMatch(
				keySequence);
		System.out.println("Command id=" + commandId);
		if (commandId == null)
			return null;
		return new KeyCommand(wid, commandId);
	}

	private MacroCommand getLastCommand() {
		if (commands.size() > 0) {
			Object item = commands.get(commands.size() - 1);
			if (item instanceof MacroCommand)
				return (MacroCommand) item;
		}
		return null;
	}

	public boolean isDisposed() {
		return this.shell != null && this.shell.isDisposed();
	}

	public boolean tracks(Shell shell) {
		if (this.shell != null && this.shell.equals(shell))
			return true;
		return false;
	}

	public boolean playback(final Display display, Composite parent,
			IProgressMonitor monitor) throws CoreException {
		if (parent instanceof Shell) {
			this.shell = (Shell) parent;
			this.display = display;
			hookWindow(true);
		}

		NestedShell nestedShell = null;

		monitor.beginTask("", commands.size());

		for (int i = 0; i < commands.size(); i++) {
			IPlayable playable = (IPlayable) commands.get(i);
			if (i < commands.size() - 1) {
				// check the next playable
				IPlayable next = (IPlayable) commands.get(i + 1);
				if (next instanceof MacroCommandShell) {
					// this command will open a new shell
					// add a listener before it is too late
					MacroCommandShell nestedCommand = (MacroCommandShell) next;
					nestedShell = new NestedShell(display, nestedCommand,
							new SubProgressMonitor(monitor, 1));
					final NestedShell fnestedShell = nestedShell;
					display.syncExec(new Runnable() {
						public void run() {
							display.addFilter(SWT.Activate, fnestedShell);
						}
					});
				}
			}
			if (playable instanceof MacroCommand) {
				boolean last = i == commands.size() - 1;
				playInGUIThread(display, playable, last, monitor);
				monitor.worked(1);
			} else if (nestedShell != null) {
				CoreException e = null;
				if (nestedShell.isReleased() == false) {
					final NestedShell fnestedShell = nestedShell;
					display.syncExec(new Runnable() {
						public void run() {
							display.removeFilter(SWT.Activate, fnestedShell);
						}
					});
				}
				e = nestedShell.getException();
				boolean result = nestedShell.getResult();
				nestedShell = null;
				if (e != null)
					throw e;
				if (!result)
					return false;
			}
		}
		shell = null;
		return true;
	}

	private void playInGUIThread(final Display display,
			final IPlayable playable, boolean last,
			final IProgressMonitor monitor) throws CoreException {
		final CoreException[] ex = new CoreException[1];

		Runnable runnable = new Runnable() {
			public void run() {
				try {
					playable.playback(display, MacroCommandShell.this.shell,
							monitor);
					MacroUtil.processDisplayEvents(display);
				} catch (CoreException e) {
					ex[0] = e;
				}
			}
		};
		// if (last)
		// shell.getDisplay().asyncExec(runnable);
		// else
		// display.syncExec(runnable);
		if (playable instanceof WaitCommand) {
			playable.playback(display, this.shell, monitor);
		} else
			display.syncExec(runnable);

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}

		// for (;;) {
		// if (display.isDisposed() || !display.readAndDispatch())
		// break;
		// }

		if (ex[0] != null)
			throw ex[0];
	}
}