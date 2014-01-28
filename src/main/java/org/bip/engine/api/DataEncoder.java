package org.bip.engine.api;

import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;

import org.bip.api.BIPComponent;
import org.bip.api.Port;
import org.bip.exceptions.BIPEngineException;
import org.bip.api.DataWire;

/**
 * Deals with the DataGlue.
 * Encodes the informSpecific information.
 * @author Anastasia Mavridou
 */
public interface DataEncoder {
	
	/**
	 * Receives information about the disabled ports due to data transfer information
	 * of a registered component. These ports are of different component instances.
	 * In the current implementation of the Port Object there is no information 
	 * about the holder component of a port. Therefore, the information about the 
	 * component holders has to be explicitly provided in the inform function.
	 * 
	 * It can be called several times through one component during one execution
	 * cycle of the engine. When the inform function implemented in the current state encoder
	 * is called for a particular component, this cannot be called anymore for this particular
	 * component.
	 * 
	 * Returns to the core engine the BDD corresponding to the disabled combination of ports of component instances.
	 *  
	 * @param A map that gives information about a disabled interaction of ports of component instances according to data transfer information
	 * @throws BIPEngineException 
	 */
	BDD encodeDisabledCombinations(BIPComponent decidingComponent, Port decidingPort, Map<BIPComponent, Set<Port>> disabledCombinations) throws BIPEngineException;
	
    /**
     * Receives the information about the data wires of the system.
     * @param data glue
     * @return 
     * @throws BIPEngineException 
     */
    BDD specifyDataGlue(Iterable<DataWire> dataGlue) throws BIPEngineException;
    
	/**
	 * Setter for the BDDBIPEngine
	 */
	void setBDDManager(BDDFactory manager);
	
	/**
	 * Setter for the DataCoordinator
	 */
	void setDataCoordinator(DataCoordinator dataCoordinator);
	
	/**
	 * Setter for the Behaviour Encoder
	 */
	void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);




}