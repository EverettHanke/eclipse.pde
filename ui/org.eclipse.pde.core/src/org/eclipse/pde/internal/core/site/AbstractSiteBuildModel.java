package org.eclipse.pde.internal.core.site;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.*;

import org.apache.xerces.parsers.DOMParser;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.core.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.core.isite.*;
import org.w3c.dom.*;
import org.xml.sax.*;

public abstract class AbstractSiteBuildModel
	extends AbstractModel
	implements ISiteBuildModel {
	protected transient SiteBuild siteBuild;

	public AbstractSiteBuildModel() {
	}

	public ISiteBuild getSiteBuild() {
		if (siteBuild == null) {
			SiteBuild s = new SiteBuild();
			s.model = this;
			this.siteBuild= s;
		}
		return siteBuild;
	}
	
	public ISiteBuild createSiteBuild() {
		SiteBuild s = new SiteBuild();
		s.model = this;
		s.parent = null;
		return s;
	}
	
	public ISiteBuildFeature createFeature() {
		SiteBuildFeature f = new SiteBuildFeature();
		f.model = this;
		f.parent = getSiteBuild();
		return f;
	}

	public String getInstallLocation() {
		return null;
	}
	public boolean isEditable() {
		return true;
	}
	public boolean isEnabled() {
		return true;
	}
	public void load(InputStream stream, boolean outOfSync) throws CoreException {
		DOMParser parser = new DOMParser();

		try {
			InputSource source = new InputSource(stream);
			parser.parse(source);
			processDocument(parser.getDocument());
			loaded = true;
			if (!outOfSync)
				updateTimeStamp();
		} catch (SAXException e) {
		} catch (IOException e) {
			PDECore.logException(e);
		}
	}

	private void processDocument(Document doc) {
		Node rootNode = doc.getDocumentElement();
		if (siteBuild == null) {
			siteBuild = new SiteBuild();
			siteBuild.model = this;
		} else {
			siteBuild.reset();
		}
		siteBuild.parse(rootNode);
	}
	public void reload(InputStream stream, boolean outOfSync)
		throws CoreException {
		if (siteBuild != null)
			siteBuild.reset();
		load(stream, outOfSync);
		fireModelChanged(
			new ModelChangedEvent(
				IModelChangedEvent.WORLD_CHANGED,
				new Object[] { siteBuild },
				null));
	}
}