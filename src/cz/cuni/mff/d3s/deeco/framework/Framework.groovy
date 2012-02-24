package cz.cuni.mff.d3s.deeco.framework
import Visualisation;
import groovy.lang.Closure;
import java.util.List;
import java.util.Map;
import groovyx.gpars.actor.Actor
import groovyx.gpars.actor.BlockingActor;
import groovyx.gpars.actor.DefaultActor
import javax.swing.JFrame;
import javax.swing.JLabel
import java.awt.Color;
import java.awt.GridLayout;

enum SchedType {PROCESS_PERIODIC, PROCESS_TRIGGERED}
class IProcess {
	SchedType schedType
	Closure func
	List inMapping = []
	List outMapping = []
	Map schedData = [:]
	
	
}


class Framework extends DefaultActor {
	def visualisation
	def List ensembles = []
	def Map runningEnsembles = [:]
	def List components = []
	def Map componentData = [:]
	def inMapping = ["root"]
	
	public Framework() {
		super()					
	
		visualisation = new Visualisation()
		
	}
	
	public void afterStart() {
		components*.send new RegisterMsg(actor: this, fields: inMapping)
		components*.send new RegisterMsg(actor: visualisation, fields: visualisation.inMapping)		
	}
	
	void act() {
		loop {
			react { Map component ->	
				component = component.root			
				//System.out.println("Framework update of ${component.id}")
				
				componentData[sender] = component				
				
				components.each {c->
					if (componentData.containsKey(c)) {						
						components.each {m->
							if (componentData.containsKey(m)) {
								ensembles.each {e->		
									def cd = componentData[c]
									def md = componentData[m]									
									
									if (hasInterface(cd, e.coordinator) && hasInterface(md, e.member) && e.membership(cd, md)) {
										if (runningEnsembles[m] == null)
											runningEnsembles[m] = [:]
										if (runningEnsembles[m][c] == null)
											runningEnsembles[m][c] = [:]
											
										def toRemove = []
										def hasHighestPriority = true
										
										for (otherC in runningEnsembles[m].keySet()) {
											for (otherE in runningEnsembles[m][otherC]?.keySet().grep({it != e})) {
												if (e.priority(otherE)) {
													def ocd = componentData[otherC]
													System.out.println("Removing ensemble ${otherE.id}: c=${ocd.id}, m=${md.id} because of ${e.id}");
													runningEnsembles.get(m)?.get(otherC)?.get(otherE)?.stopEnsemble()
													runningEnsembles[m][otherC].keySet().remove(otherE)
												} else {
													hasHighestPriority = false
												}																																		
											}
										}									
										
											
										if (hasHighestPriority && runningEnsembles[m][c][e] == null) {
											System.out.println("Creating ensemble ${e.id}: c=${cd.id}, m=${md.id}");
											runningEnsembles[m][c][e] = runEnsemble(e, c, m, cd, md)
										}
									} else {
										if (runningEnsembles.get(m)?.get(c)?.get(e) != null) {
											System.out.println("Removing ensemble ${e.id}");
											runningEnsembles.get(m)?.get(c)?.get(e)?.stopEnsemble()
											runningEnsembles.get(m)?.get(c)?.remove(e)
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	
	def hasInterface(Map c, List i) {
		if (c!= null && i != null) {						
			return c.keySet().containsAll(i.toSet())
		} else
			return false
	}
	
	def runComponents(List c) {
		def startedActors = []
		for (r in c)
			startedActors.addAll(runComponent(r))
		return startedActors
	}
	
	def runComponent(Map r) {
		def startedActors = []
		
		def KnowledgeActor k = new KnowledgeActor(
			name: r.id,
			knowledge: r
		)
		
		startedActors.add(k)
		components.add(k)		
		
		for (p in r.processes) {
			IProcess pr = p.value
			if (pr.schedType == SchedType.PROCESS_TRIGGERED) {
				def pa = new TriggeredProcessActor(
					func: pr.func,
					inMapping: pr.inMapping,
					outMapping: pr.outMapping
				)
				k.registerListener([actor: pa, fields: pr.inMapping] as KnowledgeListener)
				startedActors.add(pa)
									
			} else if (pr.schedType == SchedType.PROCESS_PERIODIC) {
				def pa = new PeriodicProcessActor(
					func: pr.func,
					inMapping: pr.inMapping,
					outMapping: pr.outMapping,
					knowledgeActor: k,
					sleepTime: pr.schedData.sleepTime
				)
				startedActors.add(pa)
			} else {
				System.err.println("Unknown process type ${pr.schedType} for process ${p.key}");
			}
		}		
			
		
		
		return startedActors
	}
	
	private def runEnsemble(Map e, KnowledgeActor coordinator, KnowledgeActor member, Map initCoordinatorData, Map initMemberData) {
		def actor = new EnsembleActor(
			id: e.id, 
			mapping: e.mapping,
			coordinatorInterface: e.coordinator,
			memberInterface: e.member,
			coordinatorKnowledge: coordinator,
			memberKnowledge: member,
			coordinatorData: initCoordinatorData,
			memberData: initMemberData,
		).start()
		return actor		
	}
	
	
	def registerEnsemble(Map e) {
		ensembles.add(e)
	}
	
	
}