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


def r1Path = waypointsToPath([new IPosition(x: 2, y: 9), new IPosition(x: 10, y: 9)]) 
def robot = [
    id: "R1",
    position: r1Path.first() ,
	nextPosition: r1Path.first(),
	nextPositionAlongPath: r1Path.drop(1).first(),
    path: r1Path.drop(1), 
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

def r2Path = waypointsToPath([new IPosition(x: 6, y: 2), new IPosition(x: 6, y: 12)])
def robot2 = [
	id: "R2",
	position: r2Path.first(),
	nextPosition: r2Path.first(),
	nextPositionAlongPath: r2Path.drop(1).first(),
	path: r2Path.drop(1),
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



def IRobot = ["id", "nextPosition"]
def IRobotDrive = ["id", "nextPositionAlongPath"]

def robotEnsemble = [
	id: "robotEnsemble",
	coordinator: IRobotDrive,
	member: IRobot,
	membership: {coordinator, member -> 
		coordinator.id.toString().equals(member.id.toString())
	},
	mapping: {coordinator, member ->
		member.nextPosition = coordinator.nextPositionAlongPath
		return [coordinator, member]
	},
	priority: {other -> false}
]

def CrossingLogF(id, Map robots, Map nextPositions) {
	System.out.println("Crossing ${id} update: robots=$robots, nextPositions=$nextPositions");
}

def CrossingDriveF(robots) {
	return robots
}

crossing = [
	id: "C1",
	area: crossingArea(new IPosition(x: 4, y: 6), new IPosition(x: 9, y: 11)),
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

def positionInArea(IPosition position, List area) {
	def ret = false
	area.each {p ->
		if (p.equals(position))
			ret = true		
	}
	return ret
}
def ICrossing = ["robots", "nextPositions", "area"]
def ICrossingRobot = ["id", "position"]

def crossingEnsemble = [
	id: "crossingEnsemble",
	coordinator: ICrossing,
	member: ICrossingRobot,
	membership: {coordinator, member ->
		positionInArea(member.position, coordinator.area)
	},
	mapping: {coordinator, member ->
		coordinator.robots[member.id] = member.position 
		if (coordinator.nextPositions[member.id] != null)
			member.nextPosition = coordinator.nextPositions[member.id]
		
		return [coordinator, member]
	},
	priority: {other -> true}	
]


def f = new Framework()
f.registerEnsemble(robotEnsemble)
f.registerEnsemble(crossingEnsemble)
actors = f.runComponents([robot, robot2, crossing])
actors*.start()
f.start()
actors*.join()
