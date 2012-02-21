
import groovyx.gpars.actor.Actor
import groovyx.gpars.actor.BlockingActor;
import groovyx.gpars.actor.DefaultActor

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
		for (f in fields) {
			msg[f] = knowledge[f]
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
		for (key in changeSet.keySet()) {
			knowledge[key] = changeSet[key]
		}		
		 
		def toNotify = listeners.findAll { KnowledgeListener l ->
			!l.fields.disjoint(changeSet.keySet())
		}
		for (l in toNotify) {
			notifyListener(l)
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
							
					def resultList = func(argList)	
						
					if (resultList != null && resultList != [] && outMapping == []) {			
						def result = [outMapping, resultList].transpose().collectEntries { it }
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