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
				if (msg instanceof Map) {
					processChangeSet(msg as Map)
				} else if (msg instanceof ReqDataMessage) {
					processDataRequest(((ReqDataMessage)msg).reply, ((ReqDataMessage)msg).fields)
				} else if (msg instanceof RegisterMsg) {
					msg = msg as RegisterMsg
					def listener = new KnowledgeListener(actor: msg.actor, fields: msg.fields)
					registerListener(listener)
					notifyListener(listener)
				} else if (msg instanceof UnregisterAllMsg) {
					msg = msg as UnregisterAllMsg
					unregisterListener(msg.actor)
					msg.actor.send new UnregisterAllConfirmation()
				} else {
					System.err.println("Unknown message: " + msg.toString());
				}
			}
		}
	}
}

class ReqDataMessage {
	def Actor reply
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