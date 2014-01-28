package org.bip.engine.api;

import java.util.List;

import net.sf.javabdd.BDD;

import org.bip.exceptions.BIPEngineException;
import org.bip.api.BIPGlue;

/**
 * Receives information about the glue and computes the glue BDD.
 * @author Anastasia Mavridou
 */

public interface GlueEncoder {

    /**
     * Receives the information about the glue of the system.
     * @param glue
     * @throws BIPEngineException 
     */
    void specifyGlue(BIPGlue glue) throws BIPEngineException;
	/**
	 * Setter for the BehaviourEncoder
	 */
    void setBehaviourEncoder(BehaviourEncoder behaviourEncoder);
	/**
	 * Setter for the BDDBIPEngine
	 */
	void setEngine(BDDBIPEngine engine);
	/**
	 * Setter for the OSGiBIPEngine
	 */
	void setBIPCoordinator(BIPCoordinator wrapper);
	
	/**
	 * @return the total Glue BDD
	 * @throws BIPEngineException 
	 */
	List<BDD> totalGlue() throws BIPEngineException;

}
