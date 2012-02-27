package cz.cuni.mff.d3s.deeco.framework

import groovy.lang.Closure;
import groovyx.gpars.actor.DefaultActor;

import java.util.List;
import java.util.Map;

import com.sun.xml.internal.bind.v2.runtime.Coordinator;



class EnsembleActor extends DefaultActor {
	def id = ""
	def Closure member2coordinator
	def Closure coordinator2member
	def Interface coordinatorInterface
	def Interface memberInterface
	def KnowledgeActor coordinatorKnowledge
	def KnowledgeActor memberKnowledge
	
	
	
	
	def boolean unregisteredMember = false
	def boolean unregisteredCoordinator = false
	

	
	
	public void afterStart() {
		coordinatorKnowledge.send new RegisterMsg(
			actor: this, 
			fields: coordinatorInterface.serializeForKnowledgeListener())
		memberKnowledge.send new RegisterMsg(
			actor: this, 
			fields: memberInterface.serializeForKnowledgeListener())
	}
			
	public void stopEnsemble() {
		this.send new StopEnsembleMsg()
	}
	
	private void processStopEnsembleMsg() {			
		coordinatorKnowledge.sendAndWait new UnregisterAllMsg(actor: this)
		memberKnowledge.sendAndWait new UnregisterAllMsg(actor: this)
		terminate()
	}	
	
	void act() {
		loop {
			react {msg ->
				if (msg instanceof StopEnsembleMsg) {
					processStopEnsembleMsg()				
				} else {					
						
					Map component = msg as Map
					
					def fromCoordinator = false
					def Map coordinatorData
					def Map memberData
					
					// comparisons have to be based on interfaces 
					// (for cases where the member and coordinator is the same component)
					if (coordinatorInterface.isReadRefinedBy(component)) {
						coordinatorData = component
						memberData = memberKnowledge.sendAndWait new ReqDataMessage(fields: memberInterface.serializeForKnowledgeListener())
						fromCoordinator = true
					} else if (memberInterface.isReadRefinedBy(component)) {
						memberData = component
						coordinatorData = coordinatorKnowledge.sendAndWait new ReqDataMessage(fields: coordinatorInterface.serializeForKnowledgeListener())
					} else {
						System.err.println("Unknown interface of the component: $component");
						return
					}					
					
					if (fromCoordinator) {						
						memberKnowledge.send coordinator2member(coordinatorData, memberData)
					} else {
						coordinatorKnowledge.send member2coordinator(coordinatorData, memberData)
					}					
				}		
			}
		}
	}
}
	
private class StopEnsembleMsg{}
	