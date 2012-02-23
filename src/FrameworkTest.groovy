
class FrameworkTest extends GroovyTestCase {
	void testDeepEqualsList() {
		assert KnowledgeActor.deepEquals([new IPosition(x:1, y:2), new IPosition(x:3, y:4)], [new IPosition(x:1, y:2), new IPosition(x:3, y:4)])
	}
	void testDeepEqualsMap() {
		assert KnowledgeActor.deepEquals([R1:new IPosition(x:4, y:9), R2: new IPosition(x:6, y:6)], [R1: new IPosition(x:4, y:9), R2: new IPosition(x:6, y:6)])
	}
	void testIPosition() {
		assert KnowledgeActor.deepEquals(new IPosition(x:6, y:3), new IPosition(x:6, y:3)) 
	}
	

}