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
	
	private assembleChangeSet(List fields) {
		def msg = [:]
		if (fields == ["root"])
			msg = ["root": knowledge]
		else {
			for (f in fields) {
				msg[f] = knowledge[f]
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
			if (knowledge[key] != changeSet[key]) {
				knowledge[key] = changeSet[key]
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
					processDataRequest(sender, ((ReqDataMessage)msg).fields)
				else
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
						System.out.println("result: " + result)
						reply result
					}
				}
			}
		}
	}
}

class ReqDataMessage {
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
			knowledgeActor.send new ReqDataMessage(fields: inMapping)
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


class Framework extends TriggeredProcessActor {
	def visualisation
	
	public Framework() {
		super()
		func = this.&FrameworkF
		inMapping = ["root"]
		outMapping = []				
		
		visualisation = new Visualisation()
		
		start()
	}
	
	
	
	private FrameworkF(component) {
		System.out.println("Framework update of ${component.id}")				
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
		).start()
		
		k.registerListener([actor: this, fields: inMapping] as KnowledgeListener)
		k.registerListener([actor: visualisation, fields: visualisation.inMapping] as KnowledgeListener)
		
		startedActors.add(k)
		
		for (p in r.processes) {
			IProcess pr = p.value
			if (pr.schedType == SchedType.PROCESS_TRIGGERED) {
				def pa = new TriggeredProcessActor(
					func: pr.func,
					inMapping: pr.inMapping,
					outMapping: pr.outMapping
				).start()
				k.registerListener([actor: pa, fields: pr.inMapping] as KnowledgeListener)
				startedActors.add(pa)
									
			} else if (pr.schedType == SchedType.PROCESS_PERIODIC) {
				def pa = new PeriodicProcessActor(
					func: pr.func,
					inMapping: pr.inMapping,
					outMapping: pr.outMapping,
					knowledgeActor: k,
					sleepTime: pr.schedData.sleepTime
				).start()
				startedActors.add(pa)
			} else {
				System.err.println("Unknown process type ${pr.schedType} for process ${p.key}");
			}
		}
		return startedActors
	}
	
	
}