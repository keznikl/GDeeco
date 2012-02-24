package cz.cuni.mff.d3s.deeco.framework

import KnowledgeListener;
import groovyx.gpars.actor.Actor;
import groovyx.gpars.actor.DefaultActor;

import java.util.List;
import java.util.Map;

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
		
	private Map assembleChangeSet(List fields) {
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
	
	private Map processDataRequest(List fields) {
		return assembleChangeSet(fields)
	}
	
	private procesChange(String key, value) {
		Map knowledgeSubTree = knowledge
		List keyPath = key.split("\\.")
		def topKey = keyPath.first()
		
		while (keyPath.size() > 1) {
			knowledgeSubTree = knowledgeSubTree[keyPath.first()]
			keyPath.remove(0)						 
		}
		key = keyPath.first()
		if (!deepEquals(knowledgeSubTree[key], value)) {
			knowledgeSubTree[key] = deepClone(value)			
			System.out.println("[${knowledge.id}]: $topKey = ${knowledge[topKey]}");
			return topKey
		}
		return null
	}
	
	void processChangeSet(Map changeSet) {
		def changed = []
		changeSet.each {k, v ->			
			def changedKey = procesChange(k, v)
			if (changedKey!=null)
				changed.add(changedKey)
		}
			
//		for (key in changeSet.keySet()) {
//						
//			if (!deepEquals(k[key], changeSet[key])) {
//				knowledge[key] = deepClone(changeSet[key])				
//				changed.add(key)				
//				System.out.println("[${knowledge.id}]: $key = ${changeSet[key]}");
//			}
//		}
		 
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
				if (msg instanceof Map) {
					processChangeSet(msg as Map)
				} else if (msg instanceof ReqDataMessage) {
					def response = processDataRequest((msg as ReqDataMessage).fields)
					reply response
				} else if (msg instanceof RegisterMsg) {
					msg = msg as RegisterMsg
					def listener = new KnowledgeListener(actor: msg.actor, fields: msg.fields)
					registerListener(listener)
					msg.actor.send assembleChangeSet(listener.fields) 
				} else if (msg instanceof UnregisterAllMsg) {					
					unregisterListener((msg as UnregisterAllMsg).actor)
					reply new UnregisterAllConfirmation()
				} else {
					System.err.println("Unknown message: " + msg.toString());
				}
			}
		}
	}
}

class ReqDataMessage {	
	def List fields
}

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