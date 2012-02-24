package cz.cuni.mff.d3s.deeco.casestudy
import cz.cuni.mff.d3s.deeco.framework.Framework;
import cz.cuni.mff.d3s.deeco.framework.IProcess;
import cz.cuni.mff.d3s.deeco.framework.SchedType;
import actors.*
import Framework.*
import groovyx.gpars.actor.Actor



public class IPosition {
    def x
    def y
	public String toString() { return "IPos[$x, $y]" }
	public int hashCode() { return 1000*x + y}
	public boolean equals(IPosition p) { 
		this.hashCode() == p.hashCode() 
	} 
	public clone() {
		return new IPosition(x:x, y:y)
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


def RobotStepF(IPosition nextPosition, List path) {
	def retpath = path
	if ((!path.empty) && nextPosition.equals(path.first()))
		retpath = path.drop(1)
	return [nextPosition.clone(), retpath]
}
def RobotDriveF(List path) {
	if (!path.empty)
		return [path.first().clone()]
}



def r1Path = waypointsToPath([new IPosition(x: 2, y: 9), new IPosition(x: 12, y: 9)]) 
def robot = [
    id: "R1",
    position: r1Path.first() ,
	nextPosition: r1Path[1],	
	nextPositionAlongPath: r1Path[1],
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
	]
]

def r2Path = waypointsToPath([new IPosition(x: 6, y: 2), new IPosition(x: 6, y: 12)])
def robot2 = [
	id: "R2",
	position: r2Path.first(),
	nextPosition: r2Path[1],	
	nextPositionAlongPath: r2Path[1],
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
	]
]



def IRobot = [read: ["id"], write: ["nextPosition"]]
def IRobotDrive = [read: ["id", "nextPositionAlongPath"], write: [] ]

def robotEnsemble = [
	id: "robotEnsemble",
	coordinator: IRobotDrive,
	member: IRobot,
	membership: {coordinator, member -> 
		coordinator.id.toString().equals(member.id.toString())
	},
	member2coordinator: {coordinator, member ->
		return [:]
	},
	coordinator2member: {coordinator, member ->		
		return [nextPosition: coordinator.nextPositionAlongPath.clone()]
	},	
	priority: {other -> false}
]




class RobotInfo {
	IPosition position
	List path
	
	public equals(RobotInfo other) {
		position.equals(other.position) && (path.toSet().equals(other.path.toSet()))
	}
	
	public String toString() {
		"RobotInfo[pos=$position, path=$path]"
	} 
	
	public clone() {
		return new RobotInfo(position: position.clone(), path: path.collect {it.clone()})
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

def continueRobot(String robotId, RobotInfo robotInfo, Map nextpos) {	
	if (robotInfo.position.equals(robotInfo.path.first())) {
		nextpos[robotId] = new RobotInfo(position: robotInfo.path[1], path: robotInfo.path.drop(1)).clone()
	} else {
		nextpos[robotId] = new RobotInfo(position: robotInfo.path.first(), path: robotInfo.path).clone()
	}
}

def stopRobot(String robotId, RobotInfo robotInfo, Map nextpos) {
	//nextpos[robotId] = robotInfo.clone()
}

def CrossingDriveF(robots, area) {
	def nextpos = [:]
	robots.each{String robotId, RobotInfo robotInfo->
		//v.path = v.path.intersect(area)
		if (nobodyAtRightHand(robotInfo, robots, area))
			continueRobot(robotId, robotInfo, nextpos)			
		else
			stopRobot(robotId, robotInfo, nextpos)
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
def ICrossing = [read: ["nextPositions", "robots"], write: ["robots"]]
def ICrossingRobot = [read:["id", "position", "path"], write: ["nextPosition"]]

def crossingEnsemble = [
	id: "crossingEnsemble",
	coordinator: ICrossing,
	member: ICrossingRobot,
	membership: {coordinator, member ->
		positionInArea(member.position, coordinator.area)
	},
	member2coordinator: {coordinator, member ->
		System.out.println("${member.id}->CROSSING");		
		return ["robots.${member.id}": new RobotInfo(position: member.position, path: member.path).clone()]
	},
	coordinator2member: {coordinator, member ->
		System.out.println("CROSSING->${member.id}");
		def memberResult = [:]
		if (coordinator.nextPositions[member.id] != null) {
			memberResult.nextPosition = coordinator.nextPositions[member.id].position.clone()			
		}				
		return memberResult
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
