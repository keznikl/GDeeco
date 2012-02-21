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
							background: Color.white,
							horizontalAlignment: JLabel.CENTER
							))
					}
				
			}
		}		
		
		start()
	}
	
	private animate(component) {
		new SwingBuilder().edt {
			def id = component.id.toString()
			def col = component.position.x.first() - 1
			def row = component.position.y.first() - 1
			textlabels.each{
					if (it.text.equals(id)) {
						it.text = ""
						it.setBackground(Color.white);
					}
				}
			def label = textlabels.get(rows*row+col)
			label.text = id;
			label.setBackground(Color.green);
		}
	}
}