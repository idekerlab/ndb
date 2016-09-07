
/*
 Copyright (c) 2006, 2007, The Cytoscape Consortium (www.cytoscape.org)

 The Cytoscape Consortium is:
 - Institute for Systems Biology
 - University of California San Diego
 - Memorial Sloan-Kettering Cancer Center
 - Institut Pasteur
 - Agilent Technologies

 This library is free software; you can redistribute it and/or modify it
 under the terms of the GNU Lesser General Public License as published
 by the Free Software Foundation; either version 2.1 of the License, or
 any later version.

 This library is distributed in the hope that it will be useful, but
 WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 documentation provided hereunder is on an "as is" basis, and the
 Institute for Systems Biology and the Whitehead Institute
 have no obligations to provide maintenance, support,
 updates, enhancements or modifications.  In no event shall the
 Institute for Systems Biology and the Whitehead Institute
 be liable to any party for direct, indirect, special,
 incidental or consequential damages, including lost profits, arising
 out of the use of this software and its documentation, even if the
 Institute for Systems Biology and the Whitehead Institute
 have been advised of the possibility of such damage.  See
 the GNU Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
*/

package org.cytoscape.ndb.internal;


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.io.read.AbstractCyNetworkReader;
import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CyRootNetworkManager;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;


/**
 * @author  Mike Smoot
 */
public class NDBNetworkReader extends AbstractCyNetworkReader implements CyNetworkReader {

	private List<Long> nodeIds;
	private List<Long> edgeIds;
	private Map<Object, CyNode> nodeMap;
	private TaskMonitor parentTaskMonitor;
	
	private CyNetwork newNetwork;
	private CyServiceRegistrar serviceRegistrar;

	public NDBNetworkReader(final InputStream is, final CyServiceRegistrar serviceRegistrar) {
		super(
				is, 
				serviceRegistrar.getService(CyApplicationManager.class), 
				serviceRegistrar.getService(CyNetworkFactory.class), 
				serviceRegistrar.getService(CyNetworkManager.class),
				serviceRegistrar.getService(CyRootNetworkManager.class)
		);
		this.serviceRegistrar = serviceRegistrar;
	}
	
	@Override
	public void run(TaskMonitor tm) throws IOException {
		this.parentTaskMonitor = tm;
		try {
			read();
		} finally {
			if (inputStream != null) {
				inputStream.close();
				inputStream = null;
			}
		}
	}

	/**
	 *  DOCUMENT ME!
	 *
	 * @throws IOException DOCUMENT ME!
	 */
	public void read() throws IOException {
		CyRootNetwork root = getRootNetwork();
		
		if (root != null)
			newNetwork = root.addSubNetwork();
		else // Need to create new network with new root.
			newNetwork = (CySubNetwork) cyNetworkFactory.createNetwork();
		
		CyTable nodeTable = newNetwork.getDefaultNodeTable();
		if(nodeTable.getColumn("NDB Module ID") == null)
			nodeTable.createColumn("NDB Module ID", String.class, false);
		
		CyTable edgeTable = newNetwork.getDefaultEdgeTable();
		if(edgeTable.getColumn("NDB Regulator") == null)
			edgeTable.createColumn("NDB Regulator", Boolean.class, false);

		nodeMap = getNodeMap();
		nodeIds = new ArrayList<Long>();
		edgeIds = new ArrayList<Long>();

		try {

			// Actual work
			SAXBuilder builder = new SAXBuilder(false);
			Document doc = builder.build(inputStream);

			createGeneNodes( getTable(doc,"Genes") );
			createRegulatorsRegulatorsEdges( getTable(doc,"Regulators_Regulators") );
			createModuleNodeAttrs( getTable(doc,"Genes_Modules") );

		} catch (JDOMException je) { 
			throw new IOException("JDOM failure parsing file.",je); 
		}
		this.networks = new CyNetwork[] { newNetwork };
	}

	private void createRegulatorsRegulatorsEdges(Element regulatorsRegulators) {
		for ( Object o : regulatorsRegulators.getChildren("Regulator_Gene") ) {
			Element regulatorGene = (Element)o;
			String regulatorId = regulatorGene.getAttributeValue("Regulator_Id");
			String geneId = regulatorGene.getAttributeValue("Gene_Id");
			boolean regulator = parseBoolean(regulatorGene.getAttributeValue("Regulator"));
			if ( regulatorId == null || 
			     geneId == null || 
			     !nodeMap.containsKey(regulatorId) || 
				 !nodeMap.containsKey(geneId) )
				continue;

			CyNode na = nodeMap.get(regulatorId);
			CyNode nb = nodeMap.get(geneId);
			CyEdge e = newNetwork.addEdge(na, nb, true);
			String interaction = "regulates";
			newNetwork.getRow(e).set(CyEdge.INTERACTION, interaction);
			newNetwork.getRow(e).set(CyNetwork.NAME, newNetwork.getRow(na).get(CyNetwork.NAME, String.class) +
					" (" + interaction + ") " 
					+ newNetwork.getRow(nb).get(CyNetwork.NAME, String.class));
			newNetwork.getRow(e).set("NDB Regulator",regulator);
			edgeIds.add( e.getSUID() );
		}
	}

	private void createModuleNodeAttrs(Element genes) {
		for ( Object o : genes.getChildren("Gene_Module") ) {
			Element geneModule = (Element) o;
			String geneId = geneModule.getAttributeValue("Gene_Id");
			String moduleId = geneModule.getAttributeValue("Module_Id");
			CyNode n = nodeMap.get(geneId);
			if ( n != null ) {
				newNetwork.getRow(n).set("NDB Module ID", moduleId);
			}
		}
	}

	private void createGeneNodes(Element genes) {
		for ( Object o : genes.getChildren("Gene") ) {
			Element gene = (Element) o;
			CyNode node = newNetwork.addNode();
			newNetwork.getRow(node).set(CyNetwork.NAME, gene.getAttributeValue("ORF"));
			String id = gene.getAttributeValue("Id");
			nodeMap.put(id,node);
			nodeIds.add(node.getSUID());	
		}
	}

	private Element getTable(Document doc, String tableName) {
		for ( Object e : doc.getRootElement().getChildren("Table") )
			if ( tableName.equals(((Element)e).getAttributeValue("Type")) )
				return (Element)e;

		return null;
	}

	public void doPostProcessing(CyNetwork network) {
	/*
		// Set SBML specific visual style
		VisualMappingManager manager = Cytoscape.getVisualMappingManager();
		CalculatorCatalog catalog = manager.getCalculatorCatalog();

		VisualStyle vs = catalog.getVisualStyle(SBMLVisualStyleFactory.SBMLReader_VS);

		if (vs == null) {
			vs = SBMLVisualStyleFactory.createVisualStyle(network);
			catalog.addVisualStyle(vs);
		}

		manager.setVisualStyle(vs);
		Cytoscape.getCurrentNetworkView().setVisualStyle(vs.getName());
		Cytoscape.getCurrentNetworkView().applyVizmapper(vs);
		*/
	}

	public int[] getNodeIndicesArray() {
		int[] nodes = new int[nodeIds.size()];

		for (int i = 0; i < nodes.length; i++)
			nodes[i] = nodeIds.get(i).intValue();

		return nodes;
	}

	public int[] getEdgeIndicesArray() {
		int[] edges = new int[edgeIds.size()];

		for (int i = 0; i < edges.length; i++)
			edges[i] = edgeIds.get(i).intValue();

		return edges;
	}

	private boolean parseBoolean(String s) {
		if ( s == null )
			return false;
		return s.toLowerCase().equals("true");
	}

	@Override
	public CyNetworkView buildCyNetworkView(CyNetwork network) {
		final CyNetworkView view = getNetworkViewFactory().createNetworkView(network);

		final CyLayoutAlgorithm layout = serviceRegistrar.getService(CyLayoutAlgorithmManager.class).getDefaultLayout();
		TaskIterator itr = layout.createTaskIterator(view, layout.getDefaultLayoutContext(), CyLayoutAlgorithm.ALL_NODE_VIEWS, "");
		Task nextTask = itr.next();
		
		try {
			nextTask.run(parentTaskMonitor);
		} catch (Exception e) {
			throw new RuntimeException("Could not finish layout", e);
		}

		return view;
	}
}
