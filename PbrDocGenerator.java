package cz.embedit.ea.pbrreport;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import org.sparx.Collection;
import org.sparx.Connector;
import org.sparx.Diagram;
import org.sparx.DiagramObject;
import org.sparx.Element;
import org.sparx.Package;
import org.sparx.Repository;

/**
 * Creates PBR solution architecture documentation in HTML file
 * Document generation is based on HoSel architecture methodology. If diagram is not compliance with that methodology, final documentation will be not complete.
 * 
 * Note: for running this java class from IDE must be places SSJavaCOM.dll into windows path, eg windows/system32 (based on EA documentation). In reality it works only if I put that dll directly into JRE
 * @author miroslav.bubenik@gmail.com
 *
 */
public class PbrDocGenerator {

	/**
	 * Helper inner class for gap basic info 
	 */
	protected class GapDefinition{
		/**
		 * Gap text
		 */
		String gapText;
		
		/**
		 * Gap type bases on architecture methodology (NEW, CHANGE, REUSE)
		 */
		String gapType;
		
		/**
		 * Parent/associated element to gap (function, service, component, interface) 
		 */
		Element gapParent;
		public String getGapText() {
			return gapText;
		}
		public void setGapText(String gapText) {
			this.gapText = gapText;
		}
		public String getGapType() {
			return gapType;
		}
		public void setGapType(String gapType) {
			this.gapType = gapType;
		}
		public Element getGapParent() {
			return gapParent;
		}
		public void setGapParent(Element gapParent) {
			this.gapParent = gapParent;
		}
		
	}
	
	private Repository repository;

	/**
	 * just open repository file
	 * @param filename file to open - enterprise architect repository
	 */
	protected void openRepository(String filename) {
        if(this.repository != null) return;

        this.repository = new Repository();

        //check if the specified file exists
        File f = new File(filename);
        if(f.exists() && !f.isDirectory()) {
            repository.OpenFile(filename);
        } else {
            System.out.println(String.format("EAP file %s was not found, exiting.", filename));
        }
    }
	
	
	/**
	 * In defined package find diagram with defined name: Application layer/Application Layer/SA
	 * @param appLayer package object, in which is diagram searched 
	 * @return diagram with defined name from given package or null, if diagram does not exist
	 */
	protected Diagram findApplicationLayer(Package appLayer){
		Diagram result = null;
		if (appLayer != null) {
			org.sparx.Collection<Diagram> diagramColl = appLayer.GetDiagrams();
			for (Iterator<Diagram> iterator = diagramColl.iterator(); iterator.hasNext();) {
				Diagram diagram = (Diagram) iterator.next();
				if ("Application layer".equals(diagram.GetName()) || "Application Layer".equals(diagram.GetName())  || "SA".equals(diagram.GetName())) {
					result = diagram;
				}
			}
		}
		return result;
	}
	
	/**
	 * Get gap definition to the given gap. For each gap connection create one instance of {@link GapDefinition}
	 * @param gapElement gap to explore
	 * @return vector of {@link GapDefinition} for each connection of gap to archimate element
	 */
	protected Vector<GapDefinition> getGapDefinition(Element gapElement){
		if (gapElement == null) {
			return null;
		}
		
		Vector<GapDefinition> result = new Vector<>();
		
		String text = gapElement.GetName();
		if (text != null && !"".equals(text)) {			
			Collection<Connector> connectors = gapElement.GetConnectors();
			//iterate over all connector for case, that one gap has association with more than one element
			for (Connector connector : connectors) {
				if (connector != null && "ArchiMate_Association".equals(connector.GetStereotype())) {
					GapDefinition gapDef = new GapDefinition();
					int index = text.indexOf(':');
					if (index > - 1) {
						gapDef.setGapType(text.substring(0, index));
						gapDef.setGapText(text.substring(index + 1));
					}
					
					//test, if connected element is not gap from gapElement
					if (connector.GetClientID() != gapElement.GetElementID()){
						gapDef.setGapParent(repository.GetElementByID(connector.GetClientID()));
					}else{
						gapDef.setGapParent(repository.GetElementByID(connector.GetSupplierID()));
					}
					
					//test, if gap is associated with archimate element (because there should be gap associated also with BPMN element id analysis part}
					if (gapDef.getGapParent().GetStereotype().contains("ArchiMate")) {
						result.add(gapDef);
					}									
				}
			}						
		}		
		return result;
	}
	
	
	/**
	 * In given diagram find all gaps and create them gap definitions. Gap definitions are only for those gaps, which are associated with archimate elements (not eg to BPMN elements)
	 * @param diagram diagram to find gaps
	 * @return vector with gap definitions from given diagram
	 */
	protected Vector<GapDefinition> getDiagramGaps(Diagram diagram){
		Vector<GapDefinition> result = null;
		if (diagram != null) {
			org.sparx.Collection<DiagramObject> diagramColl = diagram.GetDiagramObjects();
			if (diagramColl != null) {
				result = new Vector<>();
				for (Iterator<DiagramObject> iterator = diagramColl.iterator(); iterator.hasNext();) {
					DiagramObject diagrObj = iterator.next();
					Element element = repository.GetElementByID(diagrObj.GetElementID());
					if (element != null && "ArchiMate_Gap".equals(element.GetStereotype())) {
						Vector<GapDefinition> gapDefinitionVector = getGapDefinition(element); 
						//podmienka na contains archimate potrebna pre pripad, ze GAPy sa pouzivaju v BPMN diagrame, ktory je prilinkovany do application layer
						if (gapDefinitionVector != null && gapDefinitionVector.size() > 0) {
							result.addAll(gapDefinitionVector);
						}
					}
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Find root component for given component. If given component is root component, returns given elements. 
	 * @param element of ArchiMate_ApplicationComponent stereotype
	 * @return returns root component of component
	 */
	protected Element getParentOfComponent(Element element){
		if (element == null) {
			return null;
		}
		
		for (Iterator<Connector> iterator = element.GetConnectors().iterator(); iterator.hasNext();) {
			Connector connector = iterator.next();
			//pre istotu aj agregaciu, ak sa niekto pomylil a namiesto kompozicie dal agregaciu 
			if ("ArchiMate_Composition".equals(connector.GetStereotype()) || "ArchiMate_Aggregation".equals(connector.GetStereotype())) {
				Element parentElement = repository.GetElementByID(connector.GetSupplierID());				
				//component ma vzdy parenta. Ak je korenova komponenta, ktora na diagrame v skutocnosti nema nadradenu komponentu, tak parent component je ten isty komponent
				if (parentElement.GetElementGUID().equals(element.GetElementGUID())){
					return element;
				} else if (parentElement != null && "ArchiMate_ApplicationComponent".equals(parentElement.GetStereotype())) {
					return getParentOfComponent(parentElement);
				}
			}
		}
		return element;
	}
	
	/**
	 * Find root component for given function. 
	 * @param element element of ArchiMate_ApplicationFunction stereotype
	 * @param alreadyUsedFunctions Hashtable of already processed archimate function (is used to prevent to cycle function search)
	 * @return returns root component of function
	 */
	protected Element getParentOfFunction(Element element, Hashtable <String, Element> alreadyUsedFunctions){
		if (element == null) {
			return null;
		}
		if (alreadyUsedFunctions == null) {
			alreadyUsedFunctions = new Hashtable<>();			
		}
		alreadyUsedFunctions.put(element.GetName(), element);
		//System.out.println("Search parent for function " + element.GetName() + ", parentID:" + element.GetParentID());
		for (Iterator<Connector> iterator = element.GetConnectors().iterator(); iterator.hasNext();) {
			Connector connector = iterator.next();
			if ("ArchiMate_Composition".equals(connector.GetStereotype()) || "ArchiMate_Aggregation".equals(connector.GetStereotype())) {
				Element parentElement = repository.GetElementByID(connector.GetSupplierID());
				if (parentElement != null && "ArchiMate_ApplicationComponent".equals(parentElement.GetStereotype())) {
					//System.out.println("For function " + element.GetName() + " search parent component " + parentElement.GetName());
					return getParentOfComponent(parentElement);
				}else if (parentElement != null && "ArchiMate_ApplicationFunction".equals(parentElement.GetStereotype())) {
					if (alreadyUsedFunctions.get(parentElement.GetName()) != null) {
						//System.out.println("For function " + element.GetName() + " NOT search parent function "  + parentElement.GetName() + ". That function was already proceeded");
					}else{
						//System.out.println("For function " + element.GetName() + " search parent function "  + parentElement.GetName());
						return getParentOfFunction(parentElement, alreadyUsedFunctions);
					}
					
				}
			}else if ("ArchiMate_Assignment".equals(connector.GetStereotype())) {
				int nextElID;
				if (connector.GetClientID() != element.GetElementID()){
					nextElID = connector.GetClientID(); 
				}else{
					nextElID = connector.GetSupplierID();
				}
				Element parentElement = repository.GetElementByID(nextElID);
				if ("ArchiMate_ApplicationComponent".equals(parentElement.GetStereotype())) {
					return getParentOfComponent(parentElement);
				}else{
					//System.out.println("Unexpected use od function assignment from " + element.GetName() + " to " + parentElement.GetName());
				}
			}
			
		}
		return element;
	}
	
	/**
	 * Find root component for given service. 
	 * @param element element of ArchiMate_ApplicationService stereotype
	 * @return returns root component of service
	 */
	protected Element getParentOfService(Element element){
		if (element == null) {
			return null;
		}
		//System.out.println("Search parent for service " + element.GetName() + ", parentID:" + element.GetParentID());
		for (Iterator<Connector> iterator = element.GetConnectors().iterator(); iterator.hasNext();) {
			Connector connector = iterator.next(); 
			if ("ArchiMate_Realization".equals(connector.GetStereotype())) {				
				Element parentElement = repository.GetElementByID(connector.GetClientID());
				if (parentElement.GetParentID() == 0){
					return parentElement;
				} else if (parentElement != null && "ArchiMate_ApplicationFunction".equals(parentElement.GetStereotype())) {
					return getParentOfFunction(parentElement, null);
				}
			}
		}
		return element;
	}
	
	/**
	 * Find root component for given interface. 
	 * @param element element of ArchiMate_ApplicationInterface stereotype
	 * @return returns root component of interface
	 */
	protected Element getParentOfInterface(Element element){
		if (element == null) {
			return null;
		}
		//System.out.println("Search parent for interface " + element.GetName() + ", parentID:" + element.GetParentID());
		for (Iterator<Connector> iterator = element.GetConnectors().iterator(); iterator.hasNext();) {
			Connector connector = iterator.next();
			//pre istotu aj agregaciu, ak sa niekto pomylil a namiesto kompozicie dal agregaciu 
			if ("ArchiMate_Composition".equals(connector.GetStereotype()) || "ArchiMate_Aggregation".equals(connector.GetStereotype())) {				
				Element parentElement = repository.GetElementByID(connector.GetClientID());				
				//System.out.println("Search parent for interface " + element.GetName() + ", supplier:" + parentElement2.GetName() + ", Client:" + parentElement.GetName() + ", connector stereotype:" + connector.GetStereotype());
				if (parentElement.GetElementGUID().equals(element.GetElementGUID())){
					return element;
				} 
				else if (parentElement != null && "ArchiMate_ApplicationComponent".equals(parentElement.GetStereotype())) {
					return getParentOfComponent(parentElement);
				}
			}
		}
		return element;
	}
	
	/**
	 * Creates diagram document tree. Result is Hashtable, where key is name of application and value is vector of gap definitions associated with that applications (from key)
	 * @param gapDefinitions gap definitions of all gaps in diagrams
	 * @return diagram document tree
	 */
	protected Hashtable<String, Vector<GapDefinition>> getDiagramDocTree(Vector<GapDefinition> gapDefinitions){
		if (gapDefinitions == null || gapDefinitions.size() == 0) {
			return null;
		}
		Hashtable<String, Vector<GapDefinition>> result = new Hashtable<String, Vector<GapDefinition>>();
		for (GapDefinition gapDefinition : gapDefinitions) {			
			if (gapDefinition.getGapParent() != null) {
				//System.out.println("Search root for " + gapDefinition.getGapParent().GetName());
				if ("ArchiMate_ApplicationComponent".equals(gapDefinition.getGapParent().GetStereotype())) {
					Element parent = getParentOfComponent(gapDefinition.getGapParent());
					if (result.get(parent.GetName()) == null) {
						Vector<GapDefinition> vector = new Vector<>();
						vector.add(gapDefinition);
						result.put(parent.GetName(), vector);
					}else{
						Vector<GapDefinition> vector = result.get(parent.GetName());
						vector.add(gapDefinition);
						result.put(parent.GetName(), vector);
					}
				
				} else if ("ArchiMate_ApplicationFunction".equals(gapDefinition.getGapParent().GetStereotype())) {
					Element parent = getParentOfFunction(gapDefinition.getGapParent(), null);
					if (result.get(parent.GetName()) == null) {
						Vector<GapDefinition> vector = new Vector<>();
						vector.add(gapDefinition);
						result.put(parent.GetName(), vector);
					}else{
						Vector<GapDefinition> vector = result.get(parent.GetName());
						vector.add(gapDefinition);
						result.put(parent.GetName(), vector);
					}
				
				} else if ("ArchiMate_ApplicationService".equals(gapDefinition.getGapParent().GetStereotype())) {
					Element parent = getParentOfService(gapDefinition.getGapParent());
					if (result.get(parent.GetName()) == null) {
						Vector<GapDefinition> vector = new Vector<>();
						vector.add(gapDefinition);
						result.put(parent.GetName(), vector);
					}else{
						Vector<GapDefinition> vector = result.get(parent.GetName());
						vector.add(gapDefinition);
						result.put(parent.GetName(), vector);
					}
				
				} else if ("ArchiMate_ApplicationInterface".equals(gapDefinition.getGapParent().GetStereotype())) {
					Element parent = getParentOfInterface(gapDefinition.getGapParent());
					if (result.get(parent.GetName()) == null) {
						Vector<GapDefinition> vector = new Vector<>();
						vector.add(gapDefinition);
						result.put(parent.GetName(), vector);
					}else{
						Vector<GapDefinition> vector = result.get(parent.GetName());
						vector.add(gapDefinition);
						result.put(parent.GetName(), vector);
					}
				
				}
			}
		}
		/*
		System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		Enumeration<String> kyes = result.keys();
		while (kyes.hasMoreElements()) {
			String key = (String) kyes.nextElement();
			System.out.println("Key:" + key + ", length:" + result.get(key).size());			
		}
		System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		*/
		return result;
	}
	
	protected String saveDiagramImageToFile(Diagram diagram, String fileNameWithPath){
		String finalFileName = fileNameWithPath + ".bmp";
		repository.GetProjectInterface().PutDiagramImageToFile(diagram.GetDiagramGUID(), finalFileName, 1);
		return finalFileName;
	}
	
	protected void savePbrDocToFile(String outputPath, String fileName, Diagram appDiagram, Hashtable<String, Vector<GapDefinition>> elementTree){
		if (appDiagram == null || elementTree == null || elementTree.size() == 0){
			System.out.println("Cannot create output file, input variables are empty.");
			return;
		}
		
		String diagramImageFileName = saveDiagramImageToFile(appDiagram, outputPath + fileName);
		StringBuffer sb = new StringBuffer();
		sb.append("<HTML><BODY>");
		if (diagramImageFileName != null && !"".equals(diagramImageFileName)) {
			sb.append("<IMG SRC=\"" + diagramImageFileName + "\" />");
		}
		Enumeration<String> keys = elementTree.keys();
		while (keys.hasMoreElements()) {
			String elementName = (String) keys.nextElement();
			sb.append("<H1>");
			sb.append(elementName);
			sb.append("</H1><br>");
			Vector <GapDefinition> vector= elementTree.get(elementName);
			if (vector != null && vector.size() > 0) {
				sb.append("<TABLE>");
				sb.append("<TR><TH>Concept</TH><TH>Name</TH><TH>Impact</TH><TH>Note</TH></TR>");
				for (Iterator<GapDefinition> iterator = vector.iterator(); iterator.hasNext();) {
					GapDefinition gapDefinition = iterator.next();
					sb.append("<TR><TD>");
					String concept = gapDefinition.getGapParent().GetStereotype();
					sb.append(concept.substring("ArchiMate_Application".length(), concept.length()));
					sb.append("</TD><TD>");
					sb.append(gapDefinition.getGapParent().GetName());
					sb.append("</TD><TD>");
					sb.append(gapDefinition.getGapType());
					sb.append("</TD><TD>");
					sb.append(gapDefinition.getGapText());
					sb.append("</TD></TR>");
					System.out.println("   " + gapDefinition.getGapParent().GetStereotype() + ":" + gapDefinition.getGapParent().GetName() + "(" + gapDefinition.getGapType() + "):" + gapDefinition.getGapText());
					//description = description + "Name:" + gapDefinition.getGapParent().GetName() + ",";
					sb.append("</TR>");
				}
				sb.append("</TABLE>");
			}
			sb.append("<BR></BODY></HTML>");
			
			//System.out.println(description);
		}
		
		try {
			System.out.println("Create output into " + outputPath + fileName);
		    BufferedWriter out = new BufferedWriter(new FileWriter(outputPath + fileName + ".html"));
		    out.write(sb.toString()); //Here you pass your output
		    out.close();
		} catch (IOException e) {
			System.out.println("Exception by creating output file " + fileName + " in path " + outputPath);
		}
	}
	
	public static void main(String[] args) {
		//System.loadLibrary("SSJavaCOM");
		//
		
		String outputPath = "C:\\Temp\\pbrdoc\\";
		
		PbrDocGenerator pbrDocGenerator = new PbrDocGenerator();
		pbrDocGenerator.openRepository("C:\\_SVN\\HS_PRODUCT.eap");
		  
	    String packageGUID;
	    //packageGUID = "{98D6B207-35C6-460f-BF3F-5D614E977143}"; //PBR 1084
	    packageGUID = "{9457517C-8C1E-4847-99E0-A103A7F3F07E}"; //PBR 1092
	    //packageGUID = "{02B12C98-069A-45f3-8226-2421D97599E3}"; //PBR 1030, u tohoto PBR je divne, v natiahnutom modeli neexistuje connector medzi user management console a identity management, v EA ho pritom vidno
	    //packageGUID = "{C535542E-33B6-4a56-8740-D80D2700EEB9}"; //PBR-1225 
	    //packageGUID = "{A8142FD8-C560-4147-BE18-80827F72E05F}"; //PBR-1235
	    //packageGUID = "{902F1C3E-5827-4731-BE19-D03752570DDF}"; //PBR 428 - Value client
	    
	    Package appPackage = pbrDocGenerator.repository.GetPackageByGuid(packageGUID);
	    
	    Diagram appDiagram = pbrDocGenerator.findApplicationLayer(appPackage);
	    
	    Vector<GapDefinition> gaps = pbrDocGenerator.getDiagramGaps(appDiagram);
	    
	    /*System.out.println("xxxxxxxxxx Found gaps - START");
	    if (gaps != null && gaps.size()>0 ) {
			for (GapDefinition gap : gaps) {
				System.out.println("GAP type:" + gap.getGapType() + ", text:" + gap.getGapText() + ", parent:" + (gap.getGapParent() == null?"null":gap.getGapParent().GetName()));
			}
		}
	    System.out.println("xxxxxxxxxx Found gaps - ENDl");
	    */
	    //System.out.println(appDiagram.GetDiagramGUID());
	    
	    Hashtable<String, Vector<GapDefinition>> elementTree = new Hashtable<String, Vector<GapDefinition>>();
	    elementTree = pbrDocGenerator.getDiagramDocTree(gaps);
	    
	    pbrDocGenerator.savePbrDocToFile(outputPath, "myTestPBR", appDiagram, elementTree);
	    if (elementTree != null) {
	    	System.out.println("oooooooooooooooooooooooooooooooooooooooooooo");
	    	System.out.println("oooooo    FINAL DOC      ooooooooooooooooooo");
	    	System.out.println("oooooooooooooooooooooooooooooooooooooooooooo");
			Enumeration<String> keys = elementTree.keys();
			while (keys.hasMoreElements()) {
				String elementName = (String) keys.nextElement();
				System.out.println("Application:" + elementName);
				//String description;
				//description = "Root>" + elementName;
				Vector <GapDefinition> vector= elementTree.get(elementName);
				for (Iterator<GapDefinition> iterator = vector.iterator(); iterator.hasNext();) {
					GapDefinition gapDefinition = iterator.next();
					System.out.println("   " + gapDefinition.getGapParent().GetStereotype() + ":" + gapDefinition.getGapParent().GetName() + "(" + gapDefinition.getGapType() + "):" + gapDefinition.getGapText());
					//description = description + "Name:" + gapDefinition.getGapParent().GetName() + ",";
				}
				//System.out.println(description);
			}
		}
	    
	    pbrDocGenerator.repository.Exit();
	    
	}
	
}
