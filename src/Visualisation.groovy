import groovy.swing.SwingBuilder
import java.util.List;
import javax.swing.BorderFactory
import javax.swing.JFrame;
import javax.swing.JLabel;

import java.awt.BorderLayout as BL
import java.awt.Color;



public class Visualisation extends TriggeredProcessActor {
	def cols = 12
	def rows = 12 
	List textlabels = []
	Map robotLabels = [:]
	List groundLabels = []	
	Set crossing = []
	
	Color groundColor = Color.white
	Color miscColor = Color.gray
	Color robotColor = Color.green
	Color crossingColor = Color.yellow
	
	public Visualisation() {
		func = this.&animate
		inMapping = ["root"]
		outMapping = []		
		
		new SwingBuilder().edt {
			
			frame(title:'Frame', size:[400,400], locationRelativeTo: null, show: true, defaultCloseOperation:JFrame.EXIT_ON_CLOSE) {
				gridLayout(cols: cols, rows: rows)
				
					(0..<rows*cols).each {
						textlabels.add(label(
							text: "",
							border: BorderFactory.createLineBorder(Color.black),
							opaque: true,
							background: miscColor,
							horizontalAlignment: JLabel.CENTER
							))
					}				
			}
		}		
		
		start()
	}
	
	private animate(component) {
		
		
		new SwingBuilder().edt {
			def id = component.id.first().toString()
			boolean isCrossing = id.startsWith("C")
			def toClear = []
			def toPaint = [:]
			
			
			if (isCrossing) {
				def tl = component.area.first().first()
				def br = component.area.first().last()
				
				component.area.first().each {crossing.add(textlabels.get(cols*(it.y-1)+(it.x-1)))}  
				
				(0..<cols).each { i ->
					((tl.y+1)..(br.y-3)).each {row->
						toClear.add(textlabels.get(row*cols+i))
						toClear.add(textlabels.get(row*cols+i))
					}
				}
				(0..<rows).each { i ->
					((tl.x+1)..(br.x-3)).each {col->
						toClear.add(textlabels.get(i*cols+col))
						toClear.add(textlabels.get(i*cols+col))
					}
				}
				
				robotLabels.each {k,v -> 
					if (v in toClear)
						toPaint[k] = v
				}
				
			} else {			
				def col = component.position.x.first() - 1
				def row = component.position.y.first() - 1				
				
				def toRemove = robotLabels[id]
				if (toRemove != null) { 
					robotLabels.keySet().remove(id)
					toClear.add(toRemove)
				}
				
				def label = textlabels.get(cols*row+col)
				robotLabels[id]= label
				toPaint[id] = label				
			}
			
			toClear.each { l-> 
				l.text = ""
				if (l in crossing)
					l.setBackground(crossingColor)
				else
					l.setBackground(groundColor)
			}
			
			toPaint.each { rid, l ->
				l.text = rid
				l.setBackground(robotColor)
			} 
			
		}
	}
}