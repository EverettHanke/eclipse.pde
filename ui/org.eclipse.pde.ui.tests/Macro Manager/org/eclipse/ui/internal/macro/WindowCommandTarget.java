/*
 * Created on Dec 2, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.eclipse.ui.internal.macro;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Widget;

/**
 * @author dejan
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class WindowCommandTarget extends CommandTarget {
	/**
	 * @param widget
	 * @param context
	 */
	public WindowCommandTarget(Widget widget, Window window) {
		super(widget, window);
	}
	
	Window getWindow() {
		return (Window)getContext();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.macro.CommandTarget#ensureVisible()
	 */
	public void ensureVisible() {
		Window window = getWindow();
		window.getShell().setActive();
	}
}