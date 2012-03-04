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
 * 
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
			schedData: [sleepTime: 800]),
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

	/* The ensemble has the lowest priority */
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
 * Computes the recommended values of the nextPosition field for the robots,
 * which are in close perimeter to the crossing.
 * The crossing uses the standard right-hand rule for the priorities of the robots.
 * The relevant robot data are expected to appear in the robots collection, the resulting 
 * nextPosition values are stored in the nextPositions collection.
 * The drive process performs the computation of nextPositions.
 * Since the framework may experiences race conditions when switching ensembles,
 * the process is periodic rather than triggered.
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

/**
 * Interface of a crossing.
 * Provides the nextPositions, area, and robots fields for reading, 
 * and the robots collection for writing.
 */
def ICrossing = new Interface(
	read: [
		nextPositions:null, 
		robots:null, 
		area:null], 
	write: [
		robots:null]
)

/**
 * Interface of a robot in a crossing.
 * Provides its id, position and path for reading and nextPosition for writing.
 */
def ICrossingRobot = new Interface(
	read: [
		id:null, 
		position:null, 
		path:null], 
	write: [
		nextPosition:null]
)

/**
 * Ensemble for driving a robot through a crossing according to its prescribed path.
 * 
 * The ensemble is formed between a crossing and the robots in its close perimeter.
 * It copies the position and path fields of each robot into the crossing knowledge,
 * and the nextPosition from crossing back to the robot.
 * Thus, it effectively drives the robot through the crossing.
 * Has the highest priority (priority closure always returns true), thus
 * is active whenever the membership predicate holds. 
 */
def crossingEnsemble =  new Ensemble(
	id: "crossingEnsemble",
	coordinator: ICrossing,
	member: ICrossingRobot,
	/* The ensemble is formed whenever the robot is in the area of the crossing */
	membership: {coordinator, member ->
		positionInArea(member.position, coordinator.area)
	},
	
	/* Copies the robot nextPosition and path to the robots collection on the crossing (indexed by the robot's id).	 */
	member2coordinator: {coordinator, member ->
		System.out.println("${member.id}->CROSSING");	
		return ["robots.${member.id}": new RobotInfo(position: member.position, path: member.path).clone()]
	},

	/* Copies the nextPosition of the robot from the nextPositions collection of the crossing (indexed by the robot's id). */
	coordinator2member: {coordinator, member ->
		System.out.println("CROSSING->${member.id}");
		def memberResult = [:]
		if (coordinator.nextPositions[member.id] != null) {
			memberResult.nextPosition = coordinator.nextPositions[member.id].position.clone()			
		}				
		return memberResult
	},

	/* The ensemble has the highest priority */
	priority: {other -> true}	
)



/* Initialization of the DEECo framework and application startup **********************/

/*
 * Instantiate the framework class, it allows registering a knowledge listener
 * listening to changes of all the components, which is useful for example for 
 * implementing a visualization module (as in this case).
 */
def f = new Framework(new cz.cuni.mff.d3s.deeco.casestudy.RobotVisualisation())

/* register all the defined ensembles */
f.registerEnsemble(robotEnsemble)
f.registerEnsemble(crossingEnsemble)

/* 
 * instantiate and run all the defined components, the method returns list 
 * of actors which correspond to the started components
 */
def actors = f.runComponents([robot, robot2, crossing])

/* start the component actors */
actors*.start()

/* start the framework (i.e., start observing component changes and creating ensembles) */
f.start()

/* wait for the component actors to stop */
actors*.join()




/* Robot helper code *******************************************/

/**
 * A two-dimensional position of a robot.
 * Implements clone and equals.
 * 
 * @author Keznikl
 *
 */
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

/**
 * Converts the given waypoints to a sequence of neigboring IPositions.
 * 
 * @param waypoints	waypoints to be converted
 * @return			sequence of IPositions, conforming to the waypoints.
 */
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


/* Crossing helper code *******************************************/

/**
 * Decides if the given is in the area.
 *
 * @param position	position to be tested
 * @param area		the are to be tested against (as a list of IPosition objects)
 * @return			true iff the position is in the area.
 */
def positionInArea(IPosition position, List area) {
	for (p in area) {
		if (p.equals(position))
			return true
	}
	return false
}

/**
 * Information about a robot as stored by the crossing component.
 * 
 * Contains robot's position and part of its path relevant to the crossing area.
 * 
 * @author Keznikl
 *
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

/**
 * Creates a list of IPositions corresponding to the rectangle between 
 * the given top-left and bottom-right points.
 * 
 * @param tl	top-left position of the area
 * @param br	bottom-right position of the area
 * @return		the rectangular area given by tl and br as a List of IPosition
 */
public def crossingArea(IPosition tl, IPosition br) {
	def area = []
	(tl.x..br.x).each { x->
		(tl.y..br.y).each  { y->
			area.add(new IPosition(x: x, y: y))
		}
	}
	return area
}

/**
 * Movement direction of a robot in a crossing.
 */
enum EDirection {UP, DOWN, LEFT, RIGHT, UNKNOWN}

/**
 * Returns the robot's direction according to its position and remaining path.
 * 
 * @param robot	RobotInfo of the robot
 * @return		the movement direction of the robot
 */
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
	
	return EDirection.UNKNOWN
}

/**
 * Returns True iff the robot is leaving the area in the next step
 * @param r		the robot to be tested
 * @param area	the area to be left by the robot
 * @return		True iff the robot is leaving the area in the next step
 */
boolean isLeaving(RobotInfo r, area) {
	((r.path.size() > 0) && (!positionInArea(r.path[0], area))) || ((r.path.size() > 1) && (!positionInArea(r.path[1], area)))
}

/**
 * Returns True iff the robot has not the given direction.
 * @param r	the robot
 * @param d	the direction
 * @return	True iff the robot has not the given direction
 */
boolean hasNotDirection(RobotInfo r, EDirection d) {
	(getDirection(r) != d)
}

/**
 * Returns True iff the given robot is in the middle of the crossing area.
 * @param r		the robot
 * @param area	the crossing area
 * @return		True iff the given robot is in the middle of the crossing area
 */
boolean isInTheCrossing(RobotInfo r, area) {
	def tl = new IPosition(x: area.first().x+2, y: area.first().y+2)
	def br = new IPosition(x: area.last().x-2, y: area.last().y-2)
	return positionInArea(r.position, crossingArea(tl, br))
}

/**
 * Decides, wheter the given robot is allowed to enter the crossing.
 * 
 * The robot is allowed to enter, iff it is already in the middle of the crossing, 
 * or it is leaving the crossing and thus cannot collide with the other robots,
 * or all the other robots are either not in the crossing or are leaving and the given 
 * robot has priority (there is no robot on its right).
 * 
 * @param robot		the robot to be decided upon
 * @param robots	the other robots in the crossing
 * @param area		area of the crossing
 * @param d			the direction which the other robot cannot have in order for the given robot to have priority
 * @return			True iff the robot is allowed to enter the crossing.
 */
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

/**
 * Moves the given robot along its prescribed path.
 * 
 * Sets the nextPosition of the robot.
 * 
 * @param robotId	the robot's id
 * @param robotInfo	the robot's RobotInfo object
 * @param nextpos	the current value of the nextPositions collections
 * @return			the updated value of the nextPositions collections
 */
def continueRobot(String robotId, RobotInfo robotInfo, Map nextpos) {
	if (robotInfo.position.equals(robotInfo.path.first())) {
		nextpos[robotId] = new RobotInfo(position: robotInfo.path[1], path: robotInfo.path.drop(1)).clone()
	} else {
		nextpos[robotId] = new RobotInfo(position: robotInfo.path.first(), path: robotInfo.path).clone()
	}
}

/**
 * Enforces the robot to stay at its current position.
 * 
 * Does not include the robot in the nextPositions collections,
 * therefore the robot's nextPosition is not updated and the robot stays
 * at its current position.
 * 
 * @param robotId	the robot's id
 * @param robotInfo	the robot's RobotInfo
 * @param nextpos	the current value of the nextPositions collections
 * @return			the unchanged value of the nextPositions collections
 */
def stopRobot(String robotId, RobotInfo robotInfo, Map nextpos) {
	// just ignore the robot (i.e., do not set the nextpos)
}

