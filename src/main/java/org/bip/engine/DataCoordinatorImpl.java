package org.bip.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bip.api.BIPComponent;
import org.bip.api.BIPEngine;
import org.bip.api.Behaviour;
import org.bip.behaviour.Data;
import org.bip.behaviour.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.glue.BIPGlue;
import org.bip.glue.DataWire;
import org.bip.glue.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** There is no need for DataCoordinator interface, just DataCoordinatorImpl will implement BIP engine interface.
* DataCoordinatorImpl needs BIP coordinator ( BIP Engine again ) that actual does all the computation of BIP
* engine. DataCoordinatorImpl takes care of only of data querying and passing to BIP executors.
* 
* DataCoordinator intercepts call register and inform from BIPExecutor. 
* @authors: mavridou, zolotukhina
*/

public class DataCoordinatorImpl implements BIPEngine, InteractionExecutor, Runnable, DataCoordinator{

	private Logger logger = LoggerFactory.getLogger(BIPCoordinatorImpl.class);

	private ArrayList<BIPComponent> registeredComponents = new ArrayList<BIPComponent>();

	/**
	 * Helper hashtable with integers representing the local identities of
	 * registered components as the keys and the Behaviours of these components
	 * as the values.
	 */
	private Hashtable<BIPComponent, Behaviour> componentBehaviourMapping = new Hashtable<BIPComponent, Behaviour>();
	/**
	 * Helper hashset of the components that have informed in an execution
	 * cycle.
	 */
	private Hashtable<BIPComponent, ArrayList<Port>> componentUndecidedPorts = new Hashtable<BIPComponent, ArrayList<Port>>();

	/**
	 * Helper hashtable with strings as keys representing the component type of
	 * the registered components and ArrayList of BIPComponent instances that
	 * correspond to the component type specified in the key.
	 */
	private Hashtable<String, ArrayList<BIPComponent>> typeInstancesMapping = new Hashtable<String, ArrayList<BIPComponent>>();

	private ArrayList<DataWire> dataWires;
	
	/** Number of ports of components registered */
	private int nbPorts;

	/** Number of states of components registered */
	private int nbStates;
	
	private int number;

	/**
	 * Create instances of all the the Data Encoder and of the BIPCoordinator
	 */
	private DataEncoder dataEncoder = new DataEncoderImpl();
	private BIPCoordinator BIPCoordinator = new BIPCoordinatorImpl();

	private ArrayList<Requires> requires;
	
	private boolean registrationFinished = false;

	public DataCoordinatorImpl() {
		BIPCoordinator.setInteractionExecutor(this);
		dataEncoder.setDataCoordinator(this);
		dataEncoder.setBehaviourEncoder(BIPCoordinator.getBehaviourEncoderInstance());
		dataEncoder.setEngine(BIPCoordinator.getBDDBIPEngineInstance());
	}
	
	/**
	 * Sends interactions-glue to the BIP Coordinator
	 * Sends data-glue to the Data Encoder. 
	 */
	public void specifyGlue(BIPGlue glue) {
		BIPCoordinator.specifyGlue(glue);
		this.dataWires = glue.dataWires;
		this.requires = glue.requiresConstraints;
		if (dataWires.isEmpty() || dataWires == null) {
			logger.error("Data wires information not specified in XML file, although DataCoordinator is set as the wrapper");
			try {
				throw new BIPEngineException("Data wires information not specified in XML file, although DataCoordinator is set as the wrapper");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
		} else {
			try {
				BIPCoordinator.specifyDataGlue(dataEncoder.specifyDataGlue(dataWires));
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}
		}
		registrationFinished = true;
	}

	public void register(BIPComponent component, Behaviour behaviour) {
		/*
		 * The condition below checks whether the component has already been
		 * registered.
		 */
		try {
			if (component == null) {
				throw new BIPEngineException("Registering component must not be null.");
			}
			if (registeredComponents.contains(component)) {
				logger.error("Component " + component.getName() + " has already registered before.");
				throw new BIPEngineException("Component " + component.getName() + " has already registered before.");
			} else {
				registeredComponents.add(component);
				componentBehaviourMapping.put(component, behaviour);
				nbPorts += ((ArrayList<Port>)behaviour.getEnforceablePorts()).size();;
				nbStates += ((ArrayList<String>)behaviour.getStates()).size();
				BIPCoordinator.register(component, behaviour);
			}
			ArrayList<BIPComponent> componentInstances = new ArrayList<BIPComponent>();

			/*
			 * If this component type already exists in the hashtable, update
			 * the ArrayList of BIPComponents that corresponds to this component
			 * type.
			 */
			if (typeInstancesMapping.containsKey(component.getName())) {
				componentInstances.addAll(typeInstancesMapping.get(component.getName()));
			}

			componentInstances.add(component);
			typeInstancesMapping.put(component.getName(), componentInstances);
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}
	}

	public void inform(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		// for each component store its undecided ports
		componentUndecidedPorts.put(component, getUndecidedPorts(component, currentState, disabledPorts));
		// easy implementation: when all the components have informed
		// TODO the data wiring process does not need all the components having
		// informed
		while (!registrationFinished){;}
		try {
			doInformSpecific(component);
		} catch (BIPEngineException e) {
			e.printStackTrace();
		}
		//TODO: Inform the BIPCoordinator only after all the informSpecifics for the particular component have finished
		BIPCoordinator.inform(component, currentState, disabledPorts);
	}
	
	//TODO: Replace the current inform specific with this one when alinas part is ready
//	public void informSpecific(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Iterable<Port>> disabledCombinations) throws BIPEngineException {
//		if (disabledCombinations.isEmpty()){
//			try {
//				logger.error("No disabled combination specified in informSpecific. Map of disabledCombinations is empty.");
//				throw new BIPEngineException("No disabled combination specified in informSpecific. Map of disabledCombinations is empty.");
//			} catch (BIPEngineException e) {
//				e.printStackTrace();
//			}	
//		}
//		else if(!registeredComponents.contains(decidingComponent)){
//			try {
//				logger.error("Deciding component specified in informSpecific is not in the list of registered components");
//				throw new BIPEngineException("Deciding component specified in informSpecific is not in the list of registered components");
//			} catch (BIPEngineException e) {
//				e.printStackTrace();
//			}	
//		}
//		else if(!componentBehaviourMapping.get(decidingComponent).getEnforceablePorts().iterator().equals(decidingPort)){
//			try {
//				logger.error("Deciding port in informSpecific is not specified in the behaviour of the deciding component.");
//				throw new BIPEngineException("Deciding port in informSpecific is not specified in the behaviour of the deciding component.");
//			} catch (BIPEngineException e) {
//				e.printStackTrace();
//			}	
//		}
//		else{
//			Iterator <BIPComponent> disabledComponents = disabledCombinations.keySet().iterator();
//			while (disabledComponents.hasNext()){
//				BIPComponent component = disabledComponents.next();
//				if (!registeredComponents.contains(component)) {
//					logger.error("Component " + component.getName() + " specified in the disabledCombinations of informSpecific was not registered.");
//					throw new BIPEngineException("Component " + component.getName() + " specified in the disabledCombinations of informSpecific was not registered.");
//				}
//			}
//			BIPCoordinator.informSpecific(dataEncoder.informSpecific(decidingComponent, decidingPort, disabledCombinations));
//
//		}
//	}
	
	public void informSpecific(BIPComponent decidingComponent, Port decidingPort, Iterable<BIPComponent> disabledComponents) throws BIPEngineException {
		this.number++;
		if (disabledComponents==null)
		{
			return;
		}
		if (!disabledComponents.iterator().hasNext()){
//			try {
			logger.warn("No disabled components specified in informSpecific. Iterable of disabledComponents is empty.");
//				throw new BIPEngineException("No disabled components specified in informSpecific. Iterable of disabledComponents is empty.");
//			} catch (BIPEngineException e) {
//				e.printStackTrace();
//			}	
		}
		else if(!registeredComponents.contains(decidingComponent)){
			try {
				logger.error("Deciding component specified in informSpecific is not in the list of registered components");
				throw new BIPEngineException("Deciding component specified in informSpecific is not in the list of registered components");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		}
		else{
			ArrayList<Port> componentPorts = (ArrayList<Port>) componentBehaviourMapping.get(decidingComponent).getEnforceablePorts();
			if(!componentPorts.contains(decidingPort)){
			try {
				logger.error("Deciding port {} in informSpecific is not specified in the behaviour of the deciding component {}.",decidingPort, decidingComponent.getName());
				throw new BIPEngineException("Deciding port in informSpecific is not specified in the behaviour of the deciding component.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
			}	
		}
		
		else{
			for  (BIPComponent component : disabledComponents){
				if (!registeredComponents.contains(component)) {
					logger.error("Component " + component.getName() + " specified in the disabledComponents of informSpecific was not registered.");
					throw new BIPEngineException("Component " + component.getName() + " specified in the disabledComponents of informSpecific was not registered.");
				}
			}
			if(number%3==0)
			BIPCoordinator.informSpecific(dataEncoder.informSpecific(decidingComponent, decidingPort, disabledComponents));
		}
		}
	}
	
	/**
	 * BDDBIPEngine informs the BIPCoordinator for the components (and their
	 * associated ports) that are part of the same chosen interaction.
	 * 
	 * Through this function all the components need to be notified. If they are
	 * participating in an interaction then their port to be fired is sent to
	 * them through the execute function of the BIPExecutor. If they are not
	 * participating in an interaction then null is sent to them.
	 * 
	 * @throws BIPEngineException
	 */
	//TODO: test this
	public void executeInteractions(Iterable<Map<BIPComponent, Iterable<Port>>> portsToFire) throws BIPEngineException {
		Iterator<Map<BIPComponent, Iterable<Port>>> enabledCombinations = portsToFire.iterator();
		/**
		 * This is a list of components participating in the
		 * chosen-by-the-engine interactions. This keeps track of the chosen
		 * components in order to differentiate them from the non chosen ones.
		 * Through this function all the components need to be notified. Either
		 * by sending null to them or the port to be fired.
		 */
		ArrayList<BIPComponent> enabledComponents = new ArrayList<BIPComponent>();
		while (enabledCombinations.hasNext()) {
			Map<BIPComponent, Iterable<Port>> oneInteraction = enabledCombinations.next();
			Iterator<BIPComponent> interactionComponents = oneInteraction.keySet().iterator();
			while (interactionComponents.hasNext()) {
				BIPComponent component = interactionComponents.next();
				enabledComponents.add(component);
				Iterator<Port> compPortsToFire = oneInteraction.get(component).iterator();

				logger.debug("For component {} the ports are {}. ", component.getName(), oneInteraction.get(component));
				/*
				 * If the Iterator<Port> is null or empty for a chosen
				 * component, throw an exception. This should not happen.
				 */
				if (compPortsToFire == null || !compPortsToFire.hasNext()) {
					try {
						logger.error("In a chosen by the engine interaction, associated to component " + component.getName() + " is a null or empty list of ports to be fired.");
						throw new BIPEngineException("Exception in thread: " + Thread.currentThread().getName() + " In a chosen by the engine interaction, associated to component "
								+ component.getName() + " is a null or empty list of ports to be fired.");
					} catch (BIPEngineException e) {
						e.printStackTrace();
						throw e;
					}
				} else {
					while (compPortsToFire.hasNext()) {
						Port port = compPortsToFire.next();
						/*
						 * If the port is null or empty for a chosen component,
						 * throw an exception. This should not happen.
						 */
						if (port == null || port.id.isEmpty()) {
							try {
								logger.error("In a chosen by the engine interaction, associated to component " + component.getName() + " the port to be fired is null or empty.");
								throw new BIPEngineException("Exception in thread: " + Thread.currentThread().getName() + " In a chosen by the engine interaction, associated to component "
										+ component.getName() + " the port to be fired is null or empty.");
							} catch (BIPEngineException e) {
								e.printStackTrace();
								throw e;
							}
						}
						// Find out which components are sending data to
						// this component
						Iterable<Data> portToDataInForTransition = componentBehaviourMapping.get(component).portToDataInForTransition(port);
						Hashtable<String, Object> nameToValue  = new Hashtable<String, Object>();
						if (portToDataInForTransition==null || !portToDataInForTransition.iterator().hasNext())
						{
							component.execute(port.id);
							continue;
						}
						for (Data dataItem : portToDataInForTransition) {
							logger.info("Component {} execute port with inData {}", component.getName(), dataItem.name());
							for (BIPComponent aComponent : oneInteraction.keySet()) {
								String dataOutName =dataIsProvided(aComponent, component, dataItem.name()); 
								if (dataOutName!=null && !dataOutName.isEmpty()) {
									Object dataValue = aComponent.getData(dataOutName, dataItem.type());
									nameToValue.put(dataItem.name(), dataValue);
									break;
								}
							}
						}
						logger.debug("Data<->value table: {}", nameToValue);
						component.execute(port.id, nameToValue);
					}
				}
			}
		}
		
		/*
		 * send null to the components that are not part of the overall
		 * interaction
		 */
		for (BIPComponent component : registeredComponents) {
			if (!enabledComponents.contains(component)) {
				component.execute(null);
			}
		}
	}

	private String dataIsProvided(BIPComponent providingComponent, BIPComponent requiringComponent, String dataName) {
		for (DataWire wire : this.dataWires) {
			if (wire.isIncoming(dataName, componentBehaviourMapping.get(requiringComponent).getComponentType())
					&& wire.from.specType.equals(componentBehaviourMapping.get(providingComponent).getComponentType())) {
				return wire.from.id;
			}
		}
		return "";
	}

	public void run() {
		// TODO: unregister components and notify the component that the engine
		// is not working
		// for (BIPComponent component : identityMapping.values()) {
		// component.deregister();
		// }
		componentBehaviourMapping.clear();
		return;
	}
	
	public void start() {
		BIPCoordinator.start();
	}

	public void stop() {
		BIPCoordinator.stop();
	}

	public void execute() {
		BIPCoordinator.execute();
	}
	
//	private void doInformSpecific(BIPComponent component) throws BIPEngineException {
//		// mapping port <-> data it needs for computing guards
//		Map<Port, Set<Data>> portToDataInForGuard = componentBehaviourMapping.get(component).portToDataInForGuard();
//		// for each undecided port of each component :
//		for (Port port : componentUndecidedPorts.get(component)) {
//			// get list of DataIn needed for its guards
//			Iterable<Data> dataIn = portToDataInForGuard.get(port);
//			
//			// for each data its different evaluations
//			Hashtable<String, ArrayList<Object>> dataEvaluation = new Hashtable<String, ArrayList<Object>>();
//			//map dataName <-> mapping dataValue - components giving this value 
//			Hashtable<String, Hashtable<Object, ArrayList<BIPComponent>>> dataHelper = new Hashtable<String, Hashtable<Object, ArrayList<BIPComponent>>>();
//
//			// for each DataIn variable get info which components provide it
//			// as their outData
//			// mapping inData <-> outData, where
//			// in outData we have a name and a list of components providing it.
//			// for one inData there can be several outData variables
//			for (Data inDataItem : dataIn) {
//				// mapping dataValue - components giving this value 
//				Hashtable<Object, ArrayList<BIPComponent>> map = new Hashtable<Object, ArrayList<BIPComponent>>();
//				for (DataWire wire : this.dataWires) {
//					// for this dataVariable: all the values that it can take
//					ArrayList<Object> dataValues = new ArrayList<Object>();
//					if (wire.isIncoming(inDataItem.name(), componentBehaviourMapping.get(component).getComponentType())) {
//						//for each component of this type, call getData
//						for (BIPComponent aComponent : getBIPComponentInstances(wire.from.specType)) {
//							Object inValue = aComponent.getData(wire.from.id, inDataItem.type());
//							dataValues.add(inValue);
//							
//							ArrayList<BIPComponent> componentList = new ArrayList<BIPComponent>();
//							if (map.containsKey(inValue)) {
//								componentList = map.get(inValue);
//							} else {
//								map.put(inValue, componentList);
//							}
//							componentList.add(aComponent);
//						}
//						dataHelper.put(inDataItem.name(),map);
//					}
//					dataEvaluation.put(inDataItem.name(), dataValues);
//				}
//			}
//			
//			ArrayList<Map<String, Object>> dataTable = (ArrayList<Map<String, Object>>) getDataValueTable(dataEvaluation);
//			//the result provided must have the same order - put comment
//			ArrayList<Boolean> portActive = (ArrayList<Boolean>) component.checkEnabledness(port, dataTable);
//			
//			HashMap<BIPComponent, Iterable<Port>> disabledCombinations = new HashMap<BIPComponent, Iterable<Port>>();
//			for (int i = 0; i < portActive.size(); i++) {
////				ArrayList<BIPComponent> disabledComponents = new ArrayList<BIPComponent>();
//				if (!(portActive.get(i))) {
//					Map<String, Object> theseDatas = dataTable.get(i);
//					
//					for (Entry<String, Object> entry : theseDatas.entrySet()) {
//						
//						Hashtable<Object, ArrayList<BIPComponent>> valueToComponents = dataHelper.get(entry.getKey());
//					
//						Iterable<BIPComponent> components = valueToComponents.get(entry.getValue());
//						for (BIPComponent aComponent: components)
//						{
//							ArrayList<Port> ports = getDataOutPorts(aComponent, port);
//							disabledCombinations.put(aComponent, ports);
//						}
//						//HelperFunctions.addAll(disabledComponents, components);
//					}
//				}
//				this.informSpecific(component, port, disabledCombinations);
////				this.informSpecific(component, port, disabledComponents);
//			}
//
//		}
//	}

	private void doInformSpecific(BIPComponent component) throws BIPEngineException {
		// mapping port <-> data it needs for computing guards
		Map<Port, Set<Data>> portToDataInForGuard = componentBehaviourMapping.get(component).portToDataInForGuard();
		// for each undecided port of each component :
		for (Port port : componentUndecidedPorts.get(component)) {
			System.out.println("SPECIFIC For component "+ component.getName()+" and port "+ port.id);
			// get list of DataIn needed for its guards
			Iterable<Data> dataIn = portToDataInForGuard.get(port);
			
			if (!dataIn.iterator().hasNext())
			{
				//if the data is empty, then the port is enabled. just send it.
				this.informSpecific(component, port, null);
				continue;
			}
			// for each data its different evaluations
			Hashtable<String, ArrayList<Object>> dataEvaluation = new Hashtable<String, ArrayList<Object>>();
			//map dataName <-> mapping dataValue - components giving this value 
			Hashtable<String, Hashtable<Object, ArrayList<BIPComponent>>> dataHelper = new Hashtable<String, Hashtable<Object, ArrayList<BIPComponent>>>();

			// for each DataIn variable get info which components provide it
			// as their outData
			// mapping inData <-> outData, where
			// in outData we have a name and a list of components providing it.
			// for one inData there can be several outData variables
			for (Data inDataItem : dataIn) {
				// mapping dataValue - components giving this value 
				Hashtable<Object, ArrayList<BIPComponent>> map = new Hashtable<Object, ArrayList<BIPComponent>>();
				for (DataWire wire : this.dataWires) {
					// for this dataVariable: all the values that it can take
					ArrayList<Object> dataValues = new ArrayList<Object>();
					if (wire.isIncoming(inDataItem.name(), componentBehaviourMapping.get(component).getComponentType())) {
						//for each component of this type, call getData
						for (BIPComponent aComponent : getBIPComponentInstances(wire.from.specType)) {
							Object inValue = aComponent.getData(wire.from.id, inDataItem.type());
							dataValues.add(inValue);
							
							ArrayList<BIPComponent> componentList = new ArrayList<BIPComponent>();
							if (map.containsKey(inValue)) {
								componentList = map.get(inValue);
							} else {
								map.put(inValue, componentList);
							}
							componentList.add(aComponent);
						}
						dataHelper.put(inDataItem.name(),map);
					}
					dataEvaluation.put(inDataItem.name(), dataValues);
				}
			}
			
			ArrayList<Map<String, Object>> dataTable = (ArrayList<Map<String, Object>>) getDataValueTable(dataEvaluation);
			//the result provided must have the same order - put comment
			ArrayList<Boolean> portActive = (ArrayList<Boolean>) component.checkEnabledness(port, dataTable);
			System.out.println(portActive);
//			HashMap<BIPComponent, Iterable<Port>> disabledCombinations = new HashMap<BIPComponent, Iterable<Port>>();
			for (int i = 0; i < portActive.size(); i++) {
				ArrayList<BIPComponent> disabledComponents = new ArrayList<BIPComponent>();
				if (!(portActive.get(i))) {
					Map<String, Object> theseDatas = dataTable.get(i);
					
					for (Entry<String, Object> entry : theseDatas.entrySet()) {
						
						Hashtable<Object, ArrayList<BIPComponent>> valueToComponents = dataHelper.get(entry.getKey());
					
						Iterable<BIPComponent> components = valueToComponents.get(entry.getValue());
//						for (BIPComponent aComponent: components)
//						{
//							ArrayList<Port> ports = getDataOutPorts(aComponent, port);
//							disabledCombinations.put(aComponent, ports);
//						}
						HelperFunctions.addAll(disabledComponents, components);
					}
				}
//				this.informSpecific(component, port, disabledCombinations);
				System.out.println(disabledComponents);

				this.informSpecific(component, port, disabledComponents);
			}

		}
	}

	/**
	 * This function takes the structure build from data received from the
	 * executors and changes it to the structure with which the executor can be
	 * questioned vie checkEnabledness method
	 * 
	 * @param dataEvaluation
	 *            Table bipData <-> possible evaluations
	 * @return List of all possible entries, where each entry consists of the
	 *         same number of pairs bipData <-> value
	 */
	private Iterable<Map<String, Object>> getDataValueTable(Hashtable<String, ArrayList<Object>> dataEvaluation) {
		ArrayList<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

		if (dataEvaluation == null || dataEvaluation.entrySet().isEmpty()) {
			// throw exception
			return null;
		}

		// for one bipData get iterator over its values
		Entry<String, ArrayList<Object>> entry = dataEvaluation.entrySet().iterator().next();
		Iterator<Object> iterator = entry.getValue().iterator();

		// for each value of this first bipData
		while (iterator.hasNext()) {
			// create one map, where
			// all the different pairs name<->value will be stored
			// put there the current value of the first bipData
			Hashtable<String, Object> dataRow = new Hashtable<String, Object>();
			String keyCopy = entry.getKey();
			dataRow.put(keyCopy, iterator.next());

			// remove the current data from the initial data table
			// so that it is not treated again further
			ArrayList<Object> valuesCopy = dataEvaluation.remove(keyCopy);
			// treat the other bipData variables
			result.addAll(getNextTableRow(dataEvaluation, dataRow));
			// restore the current data
			dataEvaluation.put(keyCopy, valuesCopy);
		}
		return result;
	}

	/**
	 * This function helps the function getDataValueTable to transform the
	 * structure for the checkEnabledness method
	 * 
	 * @param dataEvaluation
	 *            Table bipData <-> possible evaluations
	 * @param dataRow
	 *            The current row of bipData <-> value pairs already treated
	 *            that needs to be augmented
	 * @return List of possible entries, where each entry consists of the a
	 *         number of pairs bipData <-> value
	 */
	private Collection<Map<String, Object>> getNextTableRow(Hashtable<String, ArrayList<Object>> dataEvaluation, Map<String, Object> dataRow) {
		ArrayList<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		// if there is no more data left, it means we have constructed one map
		// of all the bipData variables
		if (dataEvaluation == null || dataEvaluation.entrySet().isEmpty()) {
			result.add(dataRow);
			return result;
		}

		// for one bipData get iterator over its values
		Entry<String, ArrayList<Object>> entry = dataEvaluation.entrySet().iterator().next();
		Iterator<Object> iterator = entry.getValue().iterator();

		// for each value of this bipData
		while (iterator.hasNext()) {
			// create a new map, where
			// all the different pairs name<->value will be stored
			// copy there all the previous values
			// (this must be done to escape
			// change of one variable that leads to change of all its copies
			Map<String, Object> thisRow = new Hashtable<String, Object>();
			thisRow.putAll(dataRow);
			// put there the current value of the bipData
			thisRow.put(entry.getKey(), iterator.next());

			// remove the current data from the initial data table
			// so that it is not treated again further
			String keyCopy = entry.getKey();
			ArrayList<Object> valuesCopy = dataEvaluation.remove(keyCopy);
			// treat the other bipData variables
			result.addAll(getNextTableRow(dataEvaluation, thisRow));
			// restore the current data
			dataEvaluation.put(keyCopy, valuesCopy);
		}
		return result;
	}

	private ArrayList<Port> getUndecidedPorts(BIPComponent component, String currentState, ArrayList<Port> disabledPorts) {
		ArrayList<Port> undecidedPorts = new ArrayList<Port>();
		Behaviour behaviour = componentBehaviourMapping.get(component);
		boolean portIsDisabled = false;
		// for each port that we have
		ArrayList<Port> currentPorts = (ArrayList<Port>) behaviour.getStateToPorts().get(currentState);
		for (Port port : currentPorts) {
			for (Port disabledPort : disabledPorts) {
				// if it is equal to one of the disabled ports, we mark it as
				// disabled and do not add to the collection of undecided
				if (port.id.equals(disabledPort.id)) {
					portIsDisabled = true;
					break;
				}
			}
			if (!portIsDisabled) {
				undecidedPorts.add(port);
			}
			portIsDisabled = false;
		}
		logger.debug("For component {} the undecided ports are {}. "+ component.getName(), undecidedPorts);
		return undecidedPorts;
	}

	/**
	 * For this component, find out which ports of it provide this dataOut
	 * Independent of the instance of component
	 * @param disabledComponent
	 * @param dataOut
	 * @return
	 */
	public Iterable<Port> getDataOutPorts(BIPComponent disabledComponent, Port decidingPort) {
		ArrayList<Port> dataOutPorts = new ArrayList<Port>();
		ArrayList<Requires> requires = this.requires;
//		boolean found =false;
		logger.info("Requires size: "+requires.size());
		for (Requires oneRequireRule: requires){
//			found = true;
			if (oneRequireRule.effect.id.equals(decidingPort.id) && oneRequireRule.effect.specType.equals(decidingPort.specType)){
				logger.info("One Require rule found");
				List<List<Port>> requireRuleCauses= oneRequireRule.causes;
				for (List<Port> orPorts : requireRuleCauses){
					for (Port port : orPorts){
						if (port.specType.equals(disabledComponent.getName())){
							dataOutPorts.add(port);
						}
					}
				}
			}
		}			
		return dataOutPorts;
	}

	/**
	 * Helper function that returns the registered component instances that correspond to a component type.
	 * @throws BIPEngineException 
	 */
	public Iterable<BIPComponent> getBIPComponentInstances(String type) throws BIPEngineException {
		ArrayList<BIPComponent> instances = typeInstancesMapping.get(type);
		if (instances == null) {
			try {
				logger.error("No registered component instances for the: {} ", type
						+ " component type. Possible reasons: The name of the component instances was specified in another way at registration.");
				throw new BIPEngineException("Exception in thread " + Thread.currentThread().getName() + " No registered component instances for the component type: " + type
						+ "Possible reasons: The name of the component instances was specified in another way at registration.");
			} catch (BIPEngineException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return instances;
	}
	
	/**
	 * Helper function that given a component returns the corresponding behaviour as a Behaviour Object.
	 */
	public Behaviour getBehaviourByComponent(BIPComponent component) {
		return componentBehaviourMapping.get(component);
	}
	
	/**
	 * Helper function that returns the total number of ports of the registered components.
	 */
	public int getNoPorts() {
		return nbPorts;
	}
	
	/**
	 * Helper function that returns the total number of states of the registered components.
	 */
	public int getNoStates() {
		return nbStates;
	}

}
