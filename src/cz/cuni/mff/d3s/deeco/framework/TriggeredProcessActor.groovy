package cz.cuni.mff.d3s.deeco.framework

import groovyx.gpars.actor.DefaultActor;

import java.util.List;

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