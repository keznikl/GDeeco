import groovy.swing.SwingBuilder
import java.util.List;
import javax.swing.BorderFactory
import javax.swing.JFrame;
import javax.swing.JLabel;

import java.awt.BorderLayout as BL
import java.awt.Color;



public class Visualisation extends TriggeredProcessActor {
	def cols = 10
	def rows = 10 
	List textlabels = []
	List robotLabels = []
	List groundLabels = []
	
	Color groundColor = Color.white
	Color miscColor = Color.gray
	Color robotColor = Color.green
	
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
				
				(0..9).each { i ->
					((tl.y+1)..<(br.y-1)).each {row->
						toClear.add(textlabels.get(row*rows+i))
						toClear.add(textlabels.get(row*rows+i))
					}
					((tl.x+1)..<(br.x-1)).each {col->
						toClear.add(textlabels.get(i*rows+col))
						toClear.add(textlabels.get(i*rows+col))
					}
				}
			} else {
				def col = component.position.x.first() - 1
				def row = component.position.y.first() - 1				
				
				def toRemove = robotLabels.find {it.text.equals(id)}
				if (toRemove != null) { 
					robotLabels.remove(toRemove)
					toClear.add(toRemove)
				}
				
				def label = textlabels.get(rows*row+col)
				robotLabels.add(label)
				toPaint[id] = label				
			}
			
			toClear.each { l-> 
				l.text = ""
				l.setBackground(groundColor)
			}
			toPaint.each { rid, l ->
				l.text = rid
				l.setBackground(robotColor)
			} 
			
		}
	}
}