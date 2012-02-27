package cz.cuni.mff.d3s.deeco.framework

import java.util.List;
import java.util.Map;

public class Interface {
	/**
	 * The fields to be read from a component
	 * May overlap with read fields.
	 */
	public Map write = [:]
	
	/**
	* The fields to be written to a component. 
	* May overlap with write fields.
	*/
	public Map read = [:]
	
	/**
	 * Returns serialized version of the interface for registering a knowledge listener.
	 * Considers only read values.
	 * @return List of fields changes of which have to be notified to the listener.
	 */
	protected List serializeForKnowledgeListener() {
		return read.keySet() as List
	}
	
	/**
	 * Returns true iff the given component refines this interface (uses duck typing).
	 * @param componentKnowledge the knowledge of the component to be tested
	 * @return true iff the given component refines this interface (uses duck typing).
	 */
	protected boolean isRefinedBy(Map componentKnowledge) {
		if (componentKnowledge!= null) {
			return componentKnowledge.keySet().containsAll(read.keySet() + write.keySet())
		} else
			return false
	}
	
	/**
	* Returns true iff the given component refines the to-read part of this interface (uses duck typing).
	* @param componentKnowledge the knowledge of the component to be tested
	* @return true iff the given component refines the to-read part of this interface (uses duck typing).
	*/
	protected boolean isReadRefinedBy(Map componentKnowledge) {
		if (componentKnowledge!= null) {
			return componentKnowledge.keySet().containsAll(read.keySet())
		} else
			return false
	}

}
