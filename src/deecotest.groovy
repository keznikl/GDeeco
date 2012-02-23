import actors.*
import Framework.*
import groovyx.gpars.actor.Actor



class IPosition {
    def x
    def y
	public String toString() { return "IPos[$x, $y]" }
	public int hashCode() { return 1000*x + y}
	public boolean equals(IPosition p) { 
		this.hashCode() == p.hashCode() 
	} 
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


def RobotStepF(nextPosition) {
	return nextPosition
}
def RobotDriveF(List path, IPosition positionForDrive) {
	if ((path.size() > 1) && (path.first() == positionForDrive))
		return [path.drop(1), path[1]]
}
def LogPosition(id, position) { System.out.println("Position for $id updated to: [${position.x}, ${position.y}]");}


def r1Path = waypointsToPath([new IPosition(x: 2, y: 9), new IPosition(x: 10, y: 9)]) 
def robot = [
    id: "R1",
    position: r1Path.first() ,
	nextPosition: r1Path[1],
	positionForDrive: r1Path.first() ,
	nextPositionAlongPath: r1Path[1],
    path: r1Path.drop(1), 
	processes: [		
		drive: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&RobotDriveF,
			inMapping: ["path", "positionForDrive"], 
			outMapping: ["path", "nextPositionAlongPath"]),		
		step: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&RobotStepF,
			inMapping: ["nextPosition"],
			outMapping: ["position"],
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
	nextPosition: r2Path[1],
	positionForDrive: r2Path.first() ,
	nextPositionAlongPath: r2Path[1],
	path: r2Path.drop(1),
	processes: [
		drive: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&RobotDriveF,
			inMapping: ["path", "positionForDrive"], 
			outMapping: ["path", "nextPositionAlongPath"]),		
		step: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&RobotStepF,
			inMapping: ["nextPosition"],
			outMapping: ["position"],
			schedData: [sleepTime: 500]),
		log: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,
			func: this.&LogPosition,
			inMapping: ["id", "position"],
			outMapping: []),
	]
]



def IRobot = ["id", "nextPosition", "position"]
def IRobotDrive = ["id", "nextPositionAlongPath", "positionForDrive"]

def robotEnsemble = [
	id: "robotEnsemble",
	coordinator: IRobotDrive,
	member: IRobot,
	membership: {coordinator, member -> 
		coordinator.id.toString().equals(member.id.toString())
	},
	mapping: {coordinator, member ->
		coordinator.positionForDrive = member.position
		member.nextPosition = coordinator.nextPositionAlongPath
		return [coordinator, member]
	},
	priority: {other -> false}
]



def CrossingLogF(id, Map robots, Map nextPositions) {
	System.out.println("Crossing ${id} update: robots=$robots, nextPositions=$nextPositions");
}


class RobotInfo {
	IPosition position
	List path
	
	public equals(RobotInfo other) {
		position.equals(other.position) && (path.toSet().equals(other.path.toSet()))
	}
	
	public String toString() {
		"RobotInfo[pos=$position, path=$path]"
	} 
}
enum EDirection {UP, DOWN, LEFT, RIGHT, UNKNOWN}
def getDirection(RobotInfo robot) {
	if (robot.path.size() == 0)
		return EDirection.UNKNOWN
	def dx = robot.path.first().x - robot.position.x
	def dy = robot.path.first().y - robot.position.y
	
	if (dx>0) {
		return EDirection.RIGHT
	}
	if (dx<0) {
		return EDirection.LEFT
	}
	if (dy>0) {
		return EDirection.DOWN
	}
	if (dy<0) {
		return EDirection.UP
	}
}
def nobodyAtRightHand(RobotInfo robot, Map robots, List area) {
	switch (getDirection(robot)) {
		case EDirection.RIGHT:
			return robots.each({getDirection(it.value) != EDirection.UP})					
		case EDirection.LEFT:
			return robots.each({getDirection(it.value) != EDirection.DOWN})	
		case EDirection.UP:
			return robots.each({getDirection(it.value) != EDirection.LEFT})
		case EDirection.DOWN:
			return robots.each({getDirection(it.value) != EDirection.RIGHT})
		default:
			return false	
	}
	return false
}
def CrossingDriveF(robots, area) {
	def nextpos = [:]
	robots.each{k, v->
		//v.path = v.path.intersect(area)
		if (nobodyAtRightHand(v, robots, area))
			nextpos[k] = new RobotInfo(position: v.path.first(), path: v.path.drop(0))
		else
			nextpos[k] = new RobotInfo(position: v.position, path: v.path)
	}
	return [nextpos]
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
			inMapping: ["robots", "area"],
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
def ICrossingRobot = ["id", "position", "path"]

def crossingEnsemble = [
	id: "crossingEnsemble",
	coordinator: ICrossing,
	member: ICrossingRobot,
	membership: {coordinator, member ->
		positionInArea(member.position, coordinator.area)
	},
	mapping: {coordinator, member ->
		coordinator.robots[member.id] = new RobotInfo(position: member.position, path: member.path) 
		if (coordinator.nextPositions[member.id] != null) {
			member.nextPosition = coordinator.nextPositions[member.id].position
			member.path = coordinator.nextPositions[member.id].path
		}
		
		return [coordinator, member]
	},
	priority: {other -> true}	
]


def f = new Framework()
f.registerEnsemble(robotEnsemble)
f.registerEnsemble(crossingEnsemble)
def actors = f.runComponents([robot, robot2, crossing])
actors*.start()
f.start()
actors*.join()
