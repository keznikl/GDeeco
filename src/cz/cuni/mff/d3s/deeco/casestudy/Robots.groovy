package cz.cuni.mff.d3s.deeco.casestudy
import java.util.List;
import java.util.Map;


import cz.cuni.mff.d3s.deeco.framework.Ensemble;
import cz.cuni.mff.d3s.deeco.framework.Framework;
import cz.cuni.mff.d3s.deeco.framework.IProcess;
import cz.cuni.mff.d3s.deeco.framework.Interface
import cz.cuni.mff.d3s.deeco.framework.SchedType;


/* Robot Component ********************************************/

/**
 * Step function of a robot. 
 * Moves the robot to the next position. If the robot reached the first 
 * position in the path to go, then the path is updated (the reached 
 * position is removed).
 * 
 * @param nextPosition	next position the robot should move to
 * @param path			remaining path to go
 * @return				[updated actual position, (potentially) updated path to go]
 */
def RobotStepF(IPosition nextPosition, List path) {
	def retpath = path
	if ((!path.empty) && nextPosition.equals(path.first()))
		retpath = path.drop(1)
	return [nextPosition.clone(), retpath]
}

/**
 * Function driving a robot along its prescribed path by setting its next position.
 *  
 * @param path			the prescribed path of the robot
 * @param nextPosition	the expected next position of the robot
 * @return				[updated next position]
 */
def RobotDriveF(List path, IPosition nextPosition) {
	if (!path.empty)
		return [path.first().clone()]
}


/** Path of the first robot including its initial position */
def r1Path = waypointsToPath([new IPosition(x: 1, y: 9), new IPosition(x: 12, y: 9)])

/** 
 * Definition of the first robot.
 * The robot is supposed to move according its prescribed path.
 * The step process moves the robot according to the nextPosition field. 
 * The drive process sets the nextPositionAlongPath field
 * according to the prescribed path.   
 * Value of nextPositionAlongPath is intended to be copied to nextPosition 
 * using an ensemble (see below).
 */
def robot = [
    id: "R1",
    position: r1Path.first() ,					/* current position */
	nextPosition: r1Path[1],					/* expected position after the next step */
	nextPositionAlongPath: r1Path[1],			/* expected nextPosition according to the prescribed path*/
    path: r1Path.drop(1), 						/* prescribed path of the robot */
	processes: [		
		drive: new IProcess(
			schedType: SchedType.PROCESS_TRIGGERED,		/* the process is triggered by changes of its input knowledge */
			func: this.&RobotDriveF,					/* the function assigned to the process */ 
			inMapping: ["path", "nextPosition"], 		/* the process reads fields path and nextPosition */
			outMapping: ["nextPositionAlongPath"]),		/* the process writes the nextPositionAlongPath field */
		step: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&RobotStepF,
			inMapping: ["nextPosition", "path"],
			outMapping: ["position", "path"],
			schedData: [sleepTime: 1000]),
	]
]

/* The second robot differs only in path, initial position, and sleetTime of the step process. */
def r2Path = waypointsToPath([new IPosition(x: 6, y: 4), new IPosition(x: 6, y: 12)])
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
			inMapping: ["path", "nextPosition"], 
			outMapping: ["nextPositionAlongPath"]),		
		step: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&RobotStepF,
			inMapping: ["nextPosition", "path"],
			outMapping: ["position", "path"],
			schedData: [sleepTime: 1000]),
	]
]


/* Robot Ensemble */

/* interfaces for the robot ensemble */

/*
 * an interface is a map with two fields read and write each of them containing
 * again a map which gives the required structure of the component's knowledge 
 * to be read/written (all the values are null).
 */

/**
 * Interface that allows setting the next position to move to.
 */
def IRobot = new Interface(
	read: [
		id:null], 
	write: [
		nextPosition:null]
)

/**
 * Interface that gives the next position to move to according to the prescribed path. 
 */
def IRobotDrive = new Interface(
	read: [
		id:null, 
		nextPositionAlongPath:null], 
	write: [:] 
)

/*
 * An ensemble consists of the following fields:
 *  id: id of the ensemble definition (for visualisation and logging)
 *  coordinator: interface of the coordinator component
 *  member: interface of a member component
 *    
 *  membership: a function determining whether the given member and coordinator
 *              should form an ensemble, returns boolean
 *  member2coordinator: ensemble dataflow from meber to coordinator, 
 *                      returns [coordinatorUpdates]
 *  coordinator2member: ensemble dataflow from coordinator to member, 
 *  				    returns [memberUpdates]
 *  priority: compares the given ensemble to the current one, returns true 
 *            iff the current has higher priority   
 */

/**
 * Ensemble for driving a robot along its prescribed path.
 * 
 * The ensemble is always formed by a single robot with itself.
 * It copies the value of nextPositionAlongPath to nextPosition and thus
 * it effectively drives the robot along the prescribed path.
 * Has the lowest priority (priority closure always returns false), thus
 * is active only if no other ensemble is active for this robot. 
 */
def robotEnsemble = new Ensemble(
	id: "robotEnsemble",
	coordinator: IRobotDrive,
	member: IRobot,
	
	/* The ensemble is formed only if both coordinator and member are the same component */
	membership: {coordinator, member -> 
		coordinator.id.toString().equals(member.id.toString())
	},
	
	/* There is no data-flow from member to coordinator (coordinatorUpdate=return value is empty) */ 
	member2coordinator: {coordinator, member ->
		return [:]
	},

	/* Copies nextPositionAlongPath from coordinator to nextPosition in member. */	 
	coordinator2member: {coordinator, member ->		
		return [nextPosition: coordinator.nextPositionAlongPath.clone()]
	},	

	/* the ensemble has the lowest priority */
	priority: {other -> false}
)

/* Crossing Component *******************************************/

/**
 * The function drives the relevant robots through the crossing by influencing
 * their nextPosition.
 * 
 * @param robots	the robots in the crossing
 * @param area		the area of the crossing
 * @return			list [nextPosition] for the robots
 */
def CrossingDriveF(robots, area) {
	def nextpos = [:]
	robots.each{String robotId, RobotInfo robotInfo->		
		if (nobodyAtRightHand(robotInfo, robots, area))
			continueRobot(robotId, robotInfo, nextpos)			
		else
			stopRobot(robotId, robotInfo, nextpos)
	}	
	return [nextpos]
}

/** 
 * Definition of the crossing component.
 * 
 * 
 */
crossing = [
	id: "C1",
	area: crossingArea(new IPosition(x: 4, y: 6), new IPosition(x: 9, y: 11)),
	robots: [:],
	nextPositions: [:],
	processes: [
		drive: new IProcess(
			schedType: SchedType.PROCESS_PERIODIC,
			func: this.&CrossingDriveF,
			inMapping: ["robots", "area"],
			outMapping: ["nextPositions"],
			schedData: [sleepTime: 200]),
	],
]

def positionInArea(IPosition position, List area) {
	for (p in area) {
		if (p.equals(position))
			return true		
	}
	return false
}


def ICrossing = new Interface(
	read: [
		nextPositions:null, 
		robots:null, 
		area:null], 
	write: [
		robots:null]
)
def ICrossingRobot = new Interface(
	read: [
		id:null, 
		position:null, 
		path:null], 
	write: [
		nextPosition:null]
)

def crossingEnsemble =  new Ensemble(
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
)


def f = new Framework(new cz.cuni.mff.d3s.deeco.casestudy.RobotVisualisation())
f.registerEnsemble(robotEnsemble)
f.registerEnsemble(crossingEnsemble)
def actors = f.runComponents([robot, robot2, crossing])
actors*.start()
f.start()
actors*.join()




/* 
 * Robot helper code
 */

class IPosition {
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


/*
 * Crossing helper code
 */
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

public def crossingArea(IPosition tl, IPosition br) {
	def area = []
	(tl.x..br.x).each { x->
		(tl.y..br.y).each  { y->
			area.add(new IPosition(x: x, y: y))
		}
	}
	return area
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

boolean isLeaving(RobotInfo r, area) {
	((r.path.size() > 0) && (!positionInArea(r.path[0], area))) || ((r.path.size() > 1) && (!positionInArea(r.path[1], area)))
}
boolean hasNotDirection(RobotInfo r, EDirection d) {
	(getDirection(r) != d)
}
boolean isInTheCrossing(RobotInfo r, area) {
	def tl = new IPosition(x: area.first().x+2, y: area.first().y+2)
	def br = new IPosition(x: area.last().x-2, y: area.last().y-2)
	return positionInArea(r.position, crossingArea(tl, br))
}

boolean enterCondition(RobotInfo robot, Map robots, area, EDirection d) {
	isInTheCrossing(robot, area) || isLeaving(robot, area) || robots.values().every({!isInTheCrossing(it, area) && (isLeaving(it, area) || hasNotDirection(it, d))})
}
def nobodyAtRightHand(RobotInfo robot, Map robots, List area) {
	switch (getDirection(robot)) {
		case EDirection.RIGHT:
			return enterCondition(robot, robots, area, EDirection.UP)
		case EDirection.LEFT:
			return enterCondition(robot, robots, area, EDirection.DOWN)
		case EDirection.UP:
			return enterCondition(robot, robots, area, EDirection.LEFT)
		case EDirection.DOWN:
			return enterCondition(robot, robots, area, EDirection.RIGHT)
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
	// just ignore the robot (i.e., do not set the nextpos)
}

