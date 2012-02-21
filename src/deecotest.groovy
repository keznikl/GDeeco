import actors.*
import Framework.*
import groovyx.gpars.actor.Actor


class IPosition {
    def x
    def y
	String toString() { return "[$x, $y]" }
	int hashCode() { return 1000*x + y}
	boolean equals(IPosition p) { this.hashCode() == p.hashCode() } 
}



def RobotStepF(nextPosition, path) {
	if (path.size()>0 && path.first().equals(nextPosition))	
		return [nextPosition, path.drop(1)]	
	else
		return [nextPosition, path]
}
def RobotDriveF(List path) {
	if (path.size() > 0)
		return [path.first()]
}
def LogPosition(id, position) { System.out.println("Position for $id updated to: [${position.x}, ${position.y}]");}

robot = [
    id: "R1",
    position: [x: 1, y: 1] as IPosition,
	nextPosition: [x: 1, y: 2] as IPosition,
    path: [[x: 1, y: 2] as IPosition, [x:1, y:3] as IPosition, [x:1, y:4] as IPosition, [x:2, y:4] as IPosition], 
	processes: [		
		drive: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&RobotDriveF,
			inMapping: ["path"], 
			outMapping: ["nextPosition"]),				
		log: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&LogPosition,
			inMapping: ["id", "position"], 
			outMapping: []),
		step: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&RobotStepF,
			inMapping: ["nextPosition", "path"],
			outMapping: ["position", "path"],
			schedData: [sleepTime: 1000]),
		
	]
]






def f = new Framework()
f.runComponent(robot)*.join()

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
