/*
 * Created on Nov 29, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.eclipse.ui.internal.macro;

import java.io.PrintWriter;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.widgets.*;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

/**
 * @author dejan
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class ModifyCommand extends MacroCommand {
	public static final String TYPE = "modify";

	private String text;

	public ModifyCommand(WidgetIdentifier wid) {
		super(wid);
	}

	public String getType() {
		return TYPE;
	}

	public boolean mergeEvent(Event e) {
		return doProcessEvent(e);
	}

	public void processEvent(Event e) {
		doProcessEvent(e);
	}

	protected void load(Node node) {
		super.load(node);

		NodeList children = node.getChildNodes();
		for (int i=0; i<children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType()==Node.TEXT_NODE) {
				text = MacroUtil.getNormalizedText(child.getNodeValue());
				break;
			}
		}
	}

	private boolean doProcessEvent(Event e) {
		String text = extractText(e.widget);
		if (text != null) {
			this.text = text;
			return true;
		}
		return false;
	}

	private String extractText(Widget widget) {
		if (widget instanceof Text)
			return ((Text) widget).getText();
		if (widget instanceof Combo)
			return ((Combo) widget).getText();
		if (widget instanceof CCombo)
			return ((CCombo) widget).getText();
		if (widget instanceof StyledText)
			return MacroUtil.getWritableText(((StyledText) widget).getText());
		return null;
	}

	public void write(String indent, PrintWriter writer) {
		writer.print(indent);
		writer.print("<command type=\"");
		writer.print(getType());
		writer.print("\" contextId=\"");
		writer.print(getWidgetId().getContextId());
		writer.print("\" widgetId=\"");
		writer.print(getWidgetId().getWidgetId());
		writer.println("\">");
		if (text != null) {
			writer.print(indent);
			writer.print(text);
			writer.println();
		}
		writer.print(indent);
		writer.println("</command>");
	}

	public boolean playback(Display display, Composite parent, IProgressMonitor monitor) throws CoreException {
		if (parent.isDisposed()) return false;
		CommandTarget target = MacroUtil.locateCommandTarget(parent,
				getWidgetId());
		if (target != null) {
			target.setFocus();
			Widget widget = target.getWidget();
			if (widget instanceof Text)
				((Text) widget).setText(text);
			else if (widget instanceof Combo)
				((Combo) widget).setText(text);
			else if (widget instanceof CCombo)
				((CCombo) widget).setText(text);
			else if (widget instanceof StyledText)
				((StyledText)widget).setText(text);
		}
		return true;
	}
}