package org.cytoscape.ndb.internal;

import java.io.InputStream;

import org.cytoscape.io.CyFileFilter;
import org.cytoscape.io.read.AbstractInputStreamTaskFactory;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskIterator;

public class NDBNetworkReaderFactory extends AbstractInputStreamTaskFactory{

	private CyServiceRegistrar serviceRegistrar;
	
	public NDBNetworkReaderFactory(CyFileFilter fileFilter, CyServiceRegistrar serviceRegistrar) {
		super(fileFilter);
		this.serviceRegistrar = serviceRegistrar;
	}

	@Override
	public TaskIterator createTaskIterator(InputStream is, String inputName) {
		return new TaskIterator(new NDBNetworkReader(is, serviceRegistrar));
	}

}
