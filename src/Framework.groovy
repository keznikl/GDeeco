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

class RegisterMsg {
	Actor actor
	List fields
}
class UnregisterAllMsg {
	Actor actor
}
class UnregisterAllConfirmation{}

class KnowledgeListener {
	Actor actor
	List fields
}

class KnowledgeActor extends DefaultActor {
	def name
	def knowledge
	def List listeners = []
	
	void registerListener(KnowledgeListener l) {
		listeners.add(l)
	}	
	
	void unregisterListener(Actor a) {
		listeners.removeAll {it.actor == a}
	}
	
	protected static deepClone(c) {
		if (c instanceof Map) {
			def m = (c as Map).clone()
			for (k in m.keySet())
				m[k] = deepClone(m[k])
			return m
		} else if (c instanceof List) {
			def r = []
			def l = c as List
			for (v in l)
				r.add(deepClone(v))
			return r	
		} else if (c instanceof Set) {
			def r = [] as Set
			def l = c as Set
			for (v in l)
				r.add(deepClone(v))
			return r
		} else {
			return c
		}
	}
	
	protected static boolean deepEquals(a, b) {
		if ((a instanceof Map) && (a instanceof Map)) {
			a = (Map) a
			b = (Map) b
			return a.keySet().equals(b.keySet()) && a.keySet().every {deepEquals(a[it], b[it])}
		} else if ((a instanceof List) && (b instanceof List)) {
			a = (List) a
			b = (List) b
			return (a.size() == b.size()) && (0..<a.size()).every {deepEquals(a[it], b[it])}
		} else if ((a instanceof Set) && (b instanceof Set)) {
			a = (Set) a
			b = (Set) b
			if (a.size() != b.size())
				return false
			a.each {ai->
				def found = false
				b.each {bi->
					if (deepEquals(ai, bi))
						found = true
				}
				if (!found)
					return false
			}
			return true
		} else {
			if (a==null && b==null)
				return true
			else if (a==null || b==null)
				return false
			return a == b
		}
	}
	
	protected static void mergeMaps(Map to, Map from) {
		from.each {k, v ->
			def orig = to[k]
			if ((orig != null) && (v instanceof Map) && (orig instanceof Map)) {
				mergeMaps(orig, v)					
			} else {
				to[k]=v
			}
		}
	}
	
	private assembleChangeSet(List fields) {
		def msg = [:]
		if (fields == ["root"])
			msg = ["root": deepClone(knowledge)]
		else {
			for (f in fields) {
				msg[f] = deepClone(knowledge[f])
			}
		}
		return msg
	}
	private void notifyListener(KnowledgeListener l) {
		l.actor.send assembleChangeSet(l.fields)
	}
	
	private void processDataRequest(Actor a, List fields) {
		a.send(assembleChangeSet(fields))
	}
	
	void processChangeSet(Map changeSet) {
		def changed = []
		for (key in changeSet.keySet()) {
			
			if (!deepEquals(knowledge[key], changeSet[key])) {
				//System.out.println("Changed '$key' for ${knowledge.id}:\n${knowledge[key]}\n${changeSet[key]}");				
				//if ((knowledge[key] instanceof Map) && (changeSet[key] instanceof Map)) {
				//	mergeMaps(knowledge[key], changeSet[key] )
				//} else {
					knowledge[key] = deepClone(changeSet[key])
				//}
				changed.add(key)
				
			} 
		}
		 
		if (changed != []) {
			def toNotify = listeners.findAll { KnowledgeListener l ->
				l.fields == ["root"] || !l.fields.disjoint(changed)
			}
			for (l in toNotify) {
				notifyListener(l)
			}
		}
	}

	void act() {
		loop {
			react { msg ->
				if (msg instanceof Map)
					processChangeSet(msg as Map)
				else if (msg instanceof ReqDataMessage)
					processDataRequest(((ReqDataMessage)msg).reply, ((ReqDataMessage)msg).fields)
				else if (msg instanceof RegisterMsg) {
					msg = msg as RegisterMsg
					def listener = new KnowledgeListener(actor: msg.actor, fields: msg.fields)
					registerListener(listener)
					notifyListener(listener)
				} else if (msg instanceof UnregisterAllMsg) {
					msg = msg as UnregisterAllMsg
					unregisterListener(msg.actor)
					msg.actor.send new UnregisterAllConfirmation()
				} else
					System.err.println("Unknown message: " + msg.toString());
				
			}
		}
	} 
}


class TriggeredProcessActor extends DefaultActor {
	def func
	def List inMapping
	def List outMapping

	void act() {
		loop {
			react { Map args ->
				if (args.keySet().equals(inMapping.toSet())) {
					def argList = []
					for (key in inMapping)
						argList.add(args[key])
					
					if (argList.size() == 1 && argList.first() instanceof List)
						argList = argList.first()
							
					def resultList = func(argList)
						
					if (resultList != null && resultList != [] && outMapping != []) {
						def result = [outMapping, resultList].transpose().collectEntries { it }						
						reply result
					}
				}
			}
		}
	}
}

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

class ReqDataMessage {
	def Actor reply
	def List fields
}

class PeriodicProcessActor extends BlockingActor{
	def long sleepTime
	def func
	def KnowledgeActor knowledgeActor
	def List inMapping
	def List outMapping

	void act() {
		while (true) {
			knowledgeActor.send new ReqDataMessage(reply: this, fields: inMapping)
			def args = receive()
			def argList = []
			for (key in inMapping)
				argList.add(args[key])
			def resultList = func(argList)
			
			if (resultList != null && resultList != [] && outMapping != []) {
				def result = [outMapping, resultList].transpose().collectEntries { it }
				knowledgeActor.send result
			}
			sleep(sleepTime)
		}
	}
}



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