package cz.cuni.mff.d3s.deeco.framework

import groovy.lang.Closure;
import groovyx.gpars.actor.DefaultActor;

import java.util.List;
import java.util.Map;



class EnsembleActor extends DefaultActor {
	def id = ""
	def Closure mapping	
	def List coordinatorInterface
	def List memberInterface
	def KnowledgeActor coordinatorKnowledge
	def KnowledgeActor memberKnowledge	 
	
	def Map coordinatorData
	def Map memberData
	
	def active = true
	def unregisteredMember = false
	def unregisteredCoordinator = false

	public void afterStart() {		
		coordinatorKnowledge.send new RegisterMsg(actor: this, fields: coordinatorInterface)
		memberKnowledge.send new RegisterMsg(actor: this, fields: memberInterface)		
	}
	
	public void afterStop(List undeliveredMessages) {	
		
	}
	
	public void stopEnsemble() {	
		active=false
		coordinatorKnowledge.send new UnregisterAllMsg(actor: this)
		memberKnowledge.send new UnregisterAllMsg(actor: this)
	}	
	
	void act() {
		loop {
			react {msg ->
				if (msg instanceof UnregisterAllConfirmation) {
					if (sender == coordinatorKnowledge)
						unregisteredCoordinator = true
					if (sender == memberKnowledge)
						unregisteredMember = true
					if (unregisteredMember && unregisteredCoordinator)
						terminate();
				} else {
					if (!active)
						return
						
					Map component = msg as Map
					
					def fromCoordinator = false
					
					// comparisons have to be based on interfaces 
					// (for cases where the member and coordinator is the same component)
					if (component.keySet().equals(coordinatorInterface.toSet())) {
						coordinatorData = component
						fromCoordinator = true
					}
					if (component.keySet().equals(memberInterface.toSet())) {
						memberData = component
					}					
					
					
					def coordinatorResult
					def memberResult
					
					(coordinatorResult, memberResult) = mapping(coordinatorData, memberData)
					
					if (fromCoordinator) {
						memberKnowledge.send memberResult
					} else {
						coordinatorKnowledge.send coordinatorResult
					}
//					coordinatorKnowledge.send coordinatorResult
//					memberKnowledge.send memberResult
					
					
					
				}		
			}
		}
	}
}