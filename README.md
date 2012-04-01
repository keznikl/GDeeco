<markdown>
#Setup 
##Requirements
To compile use the GDEECo framework, the following has to be available on your system:

* Java SDK >= 1.6.0 ([http://java.com/en/]())
* Groovy SDK >= 1.8.5 ([http://groovy.codehaus.org/]())
* GPars library >= 0.12 ([http://gpars.codehaus.org/]())

To compile and run the Robots demo including its visualisation, you need the following:

* JavaFX SDK >= 2.0 ([http://javafx.com/]())
* GroovyFX library >= 0.1 ([http://groovyfx.org/]())


## Running the Robot Demo
To run the robot demo via `ant`, using the `main` task of the ensclosed `build.xml`. Do not forget to set the properties `javafxhome` and `groovyhome` accordingly.

As an alternative, run the script `cz.cuni.mff.d3s.deeco.casestudy.Robots.groovy` in your IDE as a regular groovy script with the classes from `cz.cuni.mff.d3s.deeco.framework` and the `GroovyFX` and `GPars` libraries on classpath.

The appropriate versions of the `GroovyFX` and `GPars` libraries are already included in the lib directory.

![](https://github.com/keznikl/GDeeco/raw/master/doc/deeco.png)


#DEECo Component Model
During the last few weeks,  in scope of the [ASCENS](http://www.ascens-ist.eu/) project we have been working on a  component model suitable for designing systems consisting of autonomous, self-aware, and adaptable components. The components, implicitly organized in groups called *ensembles*, live in a very dynamic environment where a component can enter/exit an ensemble at any time. 

What we have ended up with is the **DEECo component model** (stands for Dependable Emergent Ensembles of Components). The goal of DEECo is to support development of applications in such a dynamic environment.

##Robotic Playground Case Study
In the current phases of design and prototyping, we use a case-study similar to the e-mobility case study in ASCENS - the robotic playground.
In the case study, we consider a number of robots (i.e., vehicles) moving on roads with crossings. Upon arrival to a crossing, the relevant robots have to cooperate in order to drive through the crossing without a crash. An assumption is that the robots can communicate only with some of the others (that are in a close perimeter), since they typically have limited communication signal coverage.

We consider several variants for the crossing strategy, including both autonomous robots, making individual decisions based on the real-world driving rules, as well as a more centralized strategy where the robots are advised by a designated crossing components (similar to a crossing with traffic lights).

We also envision several other scenarios in this case study such as convoys of robots driving the same direction etc.

Currently, we use software simulation to explore this case study. Nevertheless, we intend to experiment also on real hardware by employing the ProNXT robots. 

##Main Concepts
The main concepts of DEECo are heavily inspired by the concepts of the [SCEL](http://rap.dsi.unifi.it/scel/) specification language. The main idea is to manage all the dynamism of the environment by externalizing the distributed communication between components to a component framework. The components access only local information and the distributed communication is performed implicitly by the framework. This way, the components have to be programmed as autonomous units, without relying on whether/how the distributed communication is performed, which makes them very robust and suitable for rapidly-changing environments. The key DEECo concepts are:

###Component
A component is an autonomous unit of deployment and computation, and it consists of:

* Knowledge 
* Processes

**Knowledge** contains all the data and functions of the component. It is a hierarchical data structure mapping identifiers to (potentially structured) values. Values are either statically typed data or functions. Thus DEECo employs statically-typed data and functions as first-class entities. We assume pure functions without side effects.

**Processes**, each of them being essentially a "thread", operate upon the knowledge of the component. A process employs a function from the knowledge of the component to perform its task. As any function is assumed to have no side effects, a process defines mapping of the knowledge to the actual parameters of the employed function (input knowledge), as well as mapping of the return value back to the knowledge (output knowledge). A process can be either periodic or triggered when (a part of) its input knowledge changes.

Currently, we envision to employ the single-writer paradigm (i.e., readers - writers), meaning that at any time each value in the knowledge of a component has at most one writer while it can have multiple readers.

###Ensemble
Ensembles determine composition of components. Composition is flat, expressed implicitly via dynamic involvement in an ensemble. An ensemble consists of a single coordinator component and multiple member components. Two components can communicate only if they are in the same ensemble and one of them is the coordinator of the ensemble. Therefore, the definition of an ensemble is described pair-wise, defining the cuples coorinator - member. 
A component can be in multiple ensembles (this will be described in more detail later).
An ensemble definition consists of:

* Required interface of the coordinator and a member
* Membership function
* Mapping function

**Interface** is a structural prescription for a view on a part of the component's knowledge. An interface is associated with a component's knowledge by means of *duck typing*; i.e., if a component's knowledge has the structure prescribed by an interface, then the component reifines the interface. In other words, an interface represents a partial view on the knowledge.

**Membership function** declaratively expresses the condition, under which two components represent the pair coordinator-member of an ensemble. The condition is defined upon the knowledge of the components. In the situation where a component satisfies the membership functions of multiple ensembles, we envision a mechanism for deciding whether all or only a subset of the candidate ensembles should be applied. Currently, we employ a simple mechanism of a partial order over the ensembles for this purpose (the "maximal" ensemble of the comparable ones is selected, the ensembles which are incomparable are applied simultaineously). 

**Mapping function** expresses the implicit distributed communication between the coordinator and a member. It ensures that the relevant knowledge changes in one component get propagated to the other component. However, it is up to the framework when/how often the mapping function is invoked. Note that (except for component processes) the single-writer rule applies also to mapping function. We assume a separate mapping for each of the directions coordinator-member, member-coordinator. 

The important idea is that the components do not know anything about ensembles (including their membership in an ensemble). They only work with their own local knowledge, which gets implicitly updated whenever the component is part of a suitable ensemble.

Further details of the DEECo component model will be discussed in the following posts.

As for implementation, we work on two prototypes: one based on messaging and implemented in Groovy ([https://github.com/keznikl/GDeeco]()), and one based on tuple spaces and implemented in Java/Scala. We also use Adobe Flex for visualization of the simulated environment.

All yout questions, opinions, and comments are welcome.

</markdown>
