import actors.*
import groovyx.gpars.actor.Actor


class IPosition {
    def x
    def y
}

enum SchedType {PROCESS_PERIODIC, PROCESS_TRIGGERED}
class IProcess {
	SchedType schedType
	Closure func
	List inMapping = []
	List outMapping = []
	Map schedData = [:]	
}

def RobotStepF(position, List path) {
	if (path.size() > 0)
		return [path.first(), path.drop(1)]	
}

def LogPosition(id, position) { System.out.println("Position for $id updated to: [${position.x}, ${position.y}]");}

robot = [
    id: "R1",
    position: [x: 1, y: 1] as IPosition,
    path: [[x:1, y:2] as IPosition, [x:1, y:3] as IPosition, [x:2, y:3] as IPosition], 
	processes: [
		step: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&RobotStepF, 
			inMapping: ["position", "path"], 
			outMapping: ["position", "path"],			
			schedData: [sleepTime: 1000]),
		log: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&LogPosition,
			inMapping: ["id", "position"], 
			outMapping: [])
		
	]
]


def runComponent(Map r) {
	def startedActors = []	
	
	def KnowledgeActor k = new KnowledgeActor(
		name: r.id,
		knowledge: r
	).start()
	
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

runComponent(robot)*.join()

//def k = new KnowledgeActor(
//	name:"R1", 
//	knowledge:robot
//).start()
//
//def logProcess = new TriggeredProcessActor(
//	func: this.&LogPosition, 
//	inMapping: ["id", "position"], 
//	outMapping: []
//).start()
//
//k.registerListener([actor: logProcess as Actor, fields: logProcess.inMapping as List] as KnowledgeListener)
//
//def stepProcess = new PeriodicProcessActor(
//	func: this.&RobotStepF, 
//	inMapping: ["position", "path"], 
//	outMapping: ["position", "path"], 
//	knowledgeActor: k, 
//	sleepTime: 1000
//).start()

//[k, logProcess, stepProcess]*.join()
