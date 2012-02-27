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
	def List<Ensemble> ensembles = []
	def List<EnsembleInstance> runningEnsembles = []
	def List<KnowledgeActor> components = []
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
	
	private class EnsembleInstance {
		KnowledgeActor coordinator
		KnowledgeActor member
		Ensemble ensemble
		EnsembleActor ensembleActor
	}
	
	void act() {
		loop {
			react { Map component ->	
				component = component.root			
				//System.out.println("Framework update of ${component.id}")
				
				componentData[sender] = component				
				
				// iterate over all pairs of components with known state data
				components.grep({componentData.containsKey(it)}).each {m->											
					components.grep({componentData.containsKey(it)}).each {c->						
						ensembles.each {e->		
							def cd = componentData[c]
							def md = componentData[m]									
							
							EnsembleInstance eInstance = runningEnsembles.find {it.coordinator == c && it.member == m && it.ensemble == e}
							
							if (e.coordinator.isRefinedBy(cd) && e.member.isRefinedBy(md) && e.membership(cd, md)) {
							
								// already running?
								if (eInstance != null)
									return
									
								// there is another ensemble of the same type with the same member but different coordinator running
								// in that case, they have to be mutually excluded => we choose the old one to be preserved
								if (!runningEnsembles.findAll {it.member == m && it.ensemble == e && it.coordinator!=c}.empty)
									return
																									
								List<EnsembleInstance> toStop = []
								def hasHighestPriority = true
								
								// iterate over all of the conflicting instances 
								// (i.e. those with different ensemble type)
								List<EnsembleInstance> conflictingInstances = runningEnsembles.findAll {it.member == m && it.ensemble != e} 
								for (EnsembleInstance conflictingInstance in conflictingInstances) {
									// if the current ensemble type has higher priority then remove the previous one
									if (e.priority(conflictingInstance.ensemble)) {										
										toStop.add(conflictingInstance)
									} else {
										hasHighestPriority = false
									}
								}
									
								if (hasHighestPriority) {
									System.out.println("Creating ensemble ${e.id}: c=${cd.id}, m=${md.id}");
									def ea = createEnsembleActor(e, c, m, cd, md)									
									runningEnsembles.add(new EnsembleInstance(
										coordinator: c,
										member: m,
										ensemble: e,
										ensembleActor: ea))									
									
									if (!toStop.empty) {										
										// if stopping some of the ensembles, the new one has to wait for all the previous ones to stop
										toStop.each { 										
											def ocd = componentData[it.coordinator]
											System.out.println("Removing ensemble ${it.ensemble.id}: c=${ocd.id}, m=${md.id} because of ${e.id}");
											runningEnsembles.remove(it)
											// instruct the old ensemble to notify the new one
											it.ensembleActor.stopEnsemble()										
										}
										toStop.collect({it.ensembleActor})*.join()
									}									
									ea.start()
								} else {
									assert toStop.empty 
								}
							} else { 
								// the membership predicate does not hold (or the components do not have the required interface)
							
								// 	already running?
								if (eInstance != null) {
									System.out.println("Removing ensemble ${e.id}");
									runningEnsembles.remove(eInstance)
									eInstance.ensembleActor.stopEnsemble()
									eInstance.ensembleActor.join()									
								}								
							}
						}			
					}
				}
			}
		}
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
	
	private def createEnsembleActor(Ensemble e, KnowledgeActor coordinator, KnowledgeActor member, Map initCoordinatorData, Map initMemberData) {
		return new EnsembleActor(
			id: e.id, 
			member2coordinator: e.member2coordinator,
			coordinator2member: e.coordinator2member,
			coordinatorInterface: e.coordinator,
			memberInterface: e.member,
			coordinatorKnowledge: coordinator,
			memberKnowledge: member,			
		)				
	}
	
	
	def registerEnsemble(Ensemble e) {
		ensembles.add(e)
	}
	
	
}