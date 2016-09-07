package org.cytoscape.ndb.internal;

import java.util.Properties;

import org.cytoscape.io.BasicCyFileFilter;
import org.cytoscape.io.DataCategory;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.io.util.StreamUtil;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.osgi.framework.BundleContext;



public class CyActivator extends AbstractCyActivator {

	@Override
	public void start(BundleContext bc) throws Exception {
		StreamUtil streamUtil = getService(bc, StreamUtil.class);
		CyServiceRegistrar serviceRegistrar = getService(bc, CyServiceRegistrar.class);
		BasicCyFileFilter ndbFilter = new BasicCyFileFilter(new String[]{"ndb"}, new String[]{"text/ndb"}, "NDB files", DataCategory.NETWORK, streamUtil);
		NDBNetworkReaderFactory ndbNetworkReaderFactory = new NDBNetworkReaderFactory(ndbFilter, serviceRegistrar);
		registerService(bc, ndbNetworkReaderFactory, InputStreamTaskFactory.class, new Properties()); 
	}
}	


