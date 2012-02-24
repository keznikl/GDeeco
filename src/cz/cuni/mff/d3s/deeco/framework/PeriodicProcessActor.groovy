package cz.cuni.mff.d3s.deeco.framework

import groovyx.gpars.actor.BlockingActor;

import java.util.List;

class PeriodicProcessActor extends BlockingActor{
	def long sleepTime
	def func
	def KnowledgeActor knowledgeActor
	def List inMapping
	def List outMapping

	void act() {
		while (true) {
			def args = knowledgeActor.sendAndWait new ReqDataMessage(fields: inMapping)
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