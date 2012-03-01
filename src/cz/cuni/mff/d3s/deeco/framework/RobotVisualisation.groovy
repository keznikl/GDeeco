package cz.cuni.mff.d3s.deeco.framework

import groovy.swing.SwingBuilder
import java.util.List;

import javafx.animation.ScaleTransition
import javafx.animation.SequentialTransition
import javafx.animation.TranslateTransition
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets
import javafx.geometry.Pos;
import javafx.scene.Group
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Rectangle
import javafx.stage.Stage;

import javax.swing.BorderFactory
import javax.swing.JFrame;
import javax.swing.JLabel;

import javafx.scene.paint.Color;
import javafx.util.Duration;

import groovyx.gpars.actor.Actor;
import groovyx.gpars.actor.Actors;
import groovyx.javafx.GroovyFX
import groovyx.javafx.SceneGraphBuilder

public class RobotVisualisation extends TriggeredProcessActor {
	def cols = 12
	def rows = 12 
	List textlabels = []
	Map robotLabels = [:]
	List groundLabels = []	
	Set crossing = [] as Set
	
	
	Map robots = [:]
	Map sceneObjects = [:]
	List floorObjects = []
	def fieldSize = 40
	def robotSize = 30
	def arcWidth = 20
	def arcHeight = 20
	def margin = 20
	def strokeWidth = 2	
	
	def sg
	Group root
	
	public RobotVisualisation() {
		func = this.&animate
		inMapping = ["root"]
		outMapping = []	

		def syncAct = Actors.actor {
			react { msg -> System.out.println("JFX started");	}
		}
		
		Thread.start {
			GroovyFX.start {primaryStage->
				sg = new SceneGraphBuilder(primaryStage);
				
				sg.stage(
					title: "Robots",			
					visible: true,
					resizable: false,
					style: "decorated",
					) {			
						scene(fill: gray, camera: new PerspectiveCamera(), 
							width: cols*fieldSize + 2*margin,
							height: rows*fieldSize + 2*margin) {
							root = group {
								
									for (y in (0..<rows)) {
										for (x in (0..<cols)) {
											def pos = getOnScreenPosition(x, y)
											group {
												Rectangle rect = rectangle(width: fieldSize, height: fieldSize,
													fill: darkgray, stroke: black, strokeWidth: strokeWidth)  
												floorObjects.add(rect)
																						
												label(text: "[${x+1}, ${y+1}]", layoutX: 2, layoutY: 2, font: 9) 
												translate(x: pos.x, y: pos.y)								
											}
										}
									}
									
								
								translate(x: margin, y: margin)						
							}
							DropShadow ds = new DropShadow(offsetX: 1.0, offsetY: 3.0, color: Color.BLACK);
							root.setEffect(ds)
						}
					}	
		
				syncAct.send "JFX_START"					
			}
			// After the window is closed, terminate the whole app
			System.exit(0)
		}
		
		syncAct.join()
		start()
	}
	
	private animate(component) {	
			
			def id = component.id.first().toString()
			boolean isCrossing = id.startsWith("C")			
			Platform.runLater {
				if (isCrossing) {
					if (id in crossing)
						return
					else
						crossing.add(id)
						
					paintCrossing(component.area.first())
					
				} else {			
					paintRobot(component)		
				}	
			}	
	}
	
	/* Helper functions */
	
	def paintRobot(Map robot) {
		def rid = robot.id
		
		boolean isNew = !(rid in robots.keySet()) 

		def targetPos = getOnScreenPosition(robot.position.x-1, robot.position.y-1)
		
		
		if (isNew) {
			Node rect = sg.group{
				def sp = stackPane {
					def rect = rectangle(width: robotSize, height: robotSize,
						arcWidth: arcWidth/2, arcHeight: arcHeight/2,
						fill: Color.LIGHTGREEN, stroke: Color.BLACK, strokeWidth: strokeWidth)					
					label(text: rid, alignment: Pos.CENTER)				
					translate(x:(fieldSize-robotSize)/2 - 1, y:(fieldSize-robotSize)/2-1)
					DropShadow ds = new DropShadow(offsetX: 1.0, offsetY: 1.0, color: Color.BLACK);
					rect.setEffect(ds)
				}				
				def lbl = stackPane {
					rectangle(width: 100, height: 30, arcWidth: 20, arcHeight: 20,
						fill: Color.BLACK, opacity: 0.6)
					label (width: 100, height: 30, text: "Knowledge: ${robot.id}", textFill:Color.WHITE)						
					translate(x:robotSize+ 5, y:robotSize+5)
				}
				
				addRobotAnimation(sp, lbl)
				
			}
			
			root.getChildren().add(rect)			
			robots[rid] = rect			
		} 

		Node robotNode = robots[rid]
		
		
		if (isNew) {
			robotNode.translateX = targetPos.x
			robotNode.translateY = targetPos.y
		} else {
			TranslateTransition transition = new TranslateTransition(Duration.millis(robot.processes.step.schedData.sleepTime/2), robotNode);
			transition.setToX(targetPos.x)
			transition.setToY(targetPos.y)				
			transition.play()
		}		
		
	}
	
	def paintCrossing(List crossingArea) {
		def tl = crossingArea.first()
		def br = crossingArea.last()
		def inCrossing = []
		def inSafeZone = []
		(0..<cols).each { i ->
			((tl.y+1)..(br.y-3)).each {row->
				if ((i >= tl.x-1) && (i <= br.x-1)) {
					inSafeZone.add(floorObjects.get(row*cols+i))
					inSafeZone.add(floorObjects.get(row*cols+i))
				} else {
					inCrossing.add(floorObjects.get(row*cols+i))
					inCrossing.add(floorObjects.get(row*cols+i))
				}
			}
		}
		(0..<rows).each { i ->
			((tl.x+1)..(br.x-3)).each {col->
				if ((i >= tl.y-1) && (i <= br.y-1)) {
					inSafeZone.add(floorObjects.get(i*cols+col))
					inSafeZone.add(floorObjects.get(i*cols+col))
				} else {
					inCrossing.add(floorObjects.get(i*cols+col))
					inCrossing.add(floorObjects.get(i*cols+col))
				}
			}
		}
		
		
		inCrossing*.fill = Color.LIGHTGRAY//Color.BLANCHEDALMOND
		inSafeZone*.fill = Color.BLANCHEDALMOND
	}
	
	def getOnScreenPosition(x, y) {
		return new VisualPosition(x: x*fieldSize , y: y*fieldSize)
	}
	
	def addRobotAnimation(obj, tooltip) {
		
		def scaleUp = new ScaleTransition (Duration.millis(200), obj);
		scaleUp.setToX(1.2)
		scaleUp.setToY(1.2)
		def scaleDown = new ScaleTransition (Duration.millis(200), obj);
		scaleDown.setToX(1)
		scaleDown.setToY(1)
		def sequentialTransition = new SequentialTransition();
		sequentialTransition.getChildren().addAll(scaleUp, scaleDown)
		
		obj.onMouseEntered = new EventHandler<MouseEvent>() {
			public void handle(final MouseEvent e) {
				if (e.getEventType() == e.MOUSE_ENTERED)
					sequentialTransition.play()					
			}
		}		
		obj.onMouseClicked = new EventHandler<MouseEvent>() {
			public void handle(final MouseEvent e) {
				if (e.getEventType() == e.MOUSE_CLICKED)
					tooltip.visible = !tooltip.visible
			}
		}
	}
	
	private class VisualPosition {
		double x
		double y
		public String toString() { return "IPos[$x, $y]" }
		public int hashCode() { return 1000*x + y}
		public boolean equals(VisualPosition p) {
			this.hashCode() == p.hashCode()
		}
		public clone() {
			return new VisualPosition(x:x, y:y)
		}
	}
	
	
}



