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

def waypointsToPath(waypoints) {
	def x
	def y
	if (waypoints.size() < 2)
		return waypoints

	def prev = waypoints.first()
	
	def steps = []
	
	for (w in waypoints.drop(1)) {				
		for (s in (prev.x..<w.x))
			steps.add([x: s, y: prev.y] as IPosition)
		for (s in (prev.y..<w.y))
			steps.add([x: w.x, y: s] as IPosition)
		prev = w				
	}	
	steps.add(prev)	
	return steps
}

def crossingArea(IPosition tl, IPosition br) {
	def area = []	
	(tl.x..br.x).each { x->
		(tl.y..br.y).each  { y->
			area.add(new IPosition(x: x, y: y))
		}
	}	
	return area
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



def robot = [
    id: "R1",
    position: [x: 3, y: 5] as IPosition,
	nextPosition: [x: 3, y: 5] as IPosition,
	nextPositionAlongPath: [x: 4, y: 5] as IPosition,
    path: waypointsToPath([[x: 4, y: 5] as IPosition, [x:10, y:5] as IPosition]), 
	processes: [		
		drive: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&RobotDriveF,
			inMapping: ["path"], 
			outMapping: ["nextPositionAlongPath"]),		
		step: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&RobotStepF,
			inMapping: ["nextPosition", "path"],
			outMapping: ["position", "path"],
			schedData: [sleepTime: 1000]),		
		log: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&LogPosition,
			inMapping: ["id", "position"],
			outMapping: []),
	]
]

def robot2 = [
	id: "R2",
	position: [x: 5, y: 1] as IPosition,
	nextPosition: [x: 5, y: 1] as IPosition,
	nextPositionAlongPath: [x: 5, y: 2] as IPosition,
	path: waypointsToPath([[x: 5, y: 2] as IPosition, [x:5, y:10] as IPosition]),
	processes: [
		drive: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&RobotDriveF,
			inMapping: ["path"],
			outMapping: ["nextPositionAlongPath"]),
		step: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&RobotStepF,
			inMapping: ["nextPosition", "path"],
			outMapping: ["position", "path"],
			schedData: [sleepTime: 500]),
		log: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&LogPosition,
			inMapping: ["id", "position"],
			outMapping: []),
	]
]

def CrossingLogF(id, Map robots, Map nextPositions) {
	System.out.println("Crossing ${id} update: robots=$robots, nextPositions=$nextPositions");
}

def CrossingDriveF(robots) {
	return robots
}

crossing = [
	id: "C1",
	area: crossingArea([x: 3, y: 3] as IPosition, [x: 7, y: 7] as IPosition),
	robots: [:],
	nextPositions: [:],	
	processes: [		
		drive: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&CrossingDriveF,
			inMapping: ["robots"],
			outMapping: ["nextPositions"]),
		log: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&CrossingLogF,
			inMapping: ["id", "robots", "nextPositions"],
			outMapping: []),
	],
]


def IRobot = ["id", "nextPosition"]
def IRobotDrive = ["id", "nextPositionAlongPath"]

def RobotMembershipF(coordinator, member ) {
	coordinator.id.toString().equals(member.id.toString())
}

def robotEnsemble = [
	id: "robotEnsemble",
	coordinator: IRobotDrive,
	member: IRobot,
	membership: this.&RobotMembershipF,
	mapping: {coordinator, member ->
		member.nextPosition = coordinator.nextPositionAlongPath
		return [coordinator, member]
	},
]

def f = new Framework()
f.registerEnsemble(robotEnsemble)
f.runComponents([robot, robot2, crossing])*.join()
